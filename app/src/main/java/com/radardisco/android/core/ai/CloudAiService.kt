package com.radardisco.android.core.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.radardisco.android.core.scanner.FolderClassifier
import com.radardisco.android.model.AiAnalysisReport
import com.radardisco.android.model.AiAnalyzeResponse
import com.radardisco.android.model.AiApiProvider
import com.radardisco.android.model.AiRecommendationItem
import com.radardisco.android.model.AiRiskLevel
import com.radardisco.android.model.AiSettings
import com.radardisco.android.model.AppStorageEntry
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.model.ScanSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

class CloudAiService(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val validateUrls: Boolean = true
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchCloudModels(
        provider: AiApiProvider,
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Informe a chave da API antes de buscar os modelos."))
        }

        val cleanBase = resolveBaseUrl(provider, baseUrl)
            .getOrElse { return@withContext Result.failure(it) }

        runCatching {
            if (provider == AiApiProvider.GEMINI) {
                val req = Request.Builder()
                    .url("$cleanBase/v1beta/models?pageSize=50")
                    .addHeader("x-goog-api-key", cleanKey)
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw IllegalStateException("Erro HTTP ${res.code} ao listar modelos Gemini: $body")
                    }
                    val root = JsonParser.parseString(body).asJsonObject
                    val modelsArray = root.getAsJsonArray("models") ?: JsonArray()
                    val names = mutableListOf<String>()
                    for (el in modelsArray) {
                        val obj = el.asJsonObject
                        val name = obj.get("name")?.asString?.removePrefix("models/") ?: continue
                        val methods = obj.getAsJsonArray("supportedGenerationMethods")
                            ?.mapNotNull { it.asString }
                            .orEmpty()
                        if (name.startsWith("gemini-") && (methods.isEmpty() || "generateContent" in methods)) {
                            names.add(name)
                        }
                    }
                    names.distinct().sortedDescending()
                }
            } else {
                val req = Request.Builder()
                    .url("$cleanBase/models")
                    .addHeader("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw IllegalStateException("Erro HTTP ${res.code} ao listar modelos da API: $body")
                    }
                    val root = JsonParser.parseString(body).asJsonObject
                    val dataArray = root.getAsJsonArray("data") ?: JsonArray()
                    dataArray.mapNotNull { el ->
                        el.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.asString
                    }.distinct().sorted()
                }
            }
        }
    }

    suspend fun analyzeSnapshot(
        snapshot: ScanSnapshot,
        settings: AiSettings,
        appStats: List<AppStorageEntry> = emptyList()
    ): AiAnalyzeResponse = withContext(Dispatchers.IO) {
        val cleanKey = settings.apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext AiAnalyzeResponse(
                ok = false,
                needsSetup = true,
                error = "Configure sua chave de API (Google Gemini ou outra API na nuvem) para analisar o armazenamento."
            )
        }

        val cleanBase = resolveBaseUrl(settings.provider, settings.effectiveBaseUrl())
            .getOrElse { err ->
                return@withContext AiAnalyzeResponse(
                    ok = false,
                    needsSetup = true,
                    error = err.message ?: "URL da API na nuvem inválida."
                )
            }

        val modelUsed = settings.effectiveModel()
        if (modelUsed.isBlank()) {
            return@withContext AiAnalyzeResponse(
                ok = false,
                needsSetup = true,
                error = "Informe o nome do modelo da API antes de iniciar a análise."
            )
        }

        val realPathByKey = HashMap<String, String>()
        val realPathByName = HashMap<String, String>()
        for (h in snapshot.spaceHogs) {
            realPathByKey[h.path.lowercase()] = h.path
            realPathByName.putIfAbsent(h.name.lowercase(), h.path)
        }
        for (f in snapshot.topFolders) {
            realPathByKey[f.path.lowercase()] = f.path
            realPathByName.putIfAbsent(f.name.lowercase(), f.path)
        }
        for (i in snapshot.items) {
            realPathByKey[i.path.lowercase()] = i.path
            realPathByName.putIfAbsent(i.name.lowercase(), i.path)
        }
        for (file in snapshot.topFiles) {
            realPathByKey[file.path.lowercase()] = file.path
            realPathByName.putIfAbsent(file.name.lowercase(), file.path)
        }

        val compactPrompt = buildCompactPrompt(snapshot, appStats)
        val userPrompt = "Analise este mapeamento do armazenamento Android e retorne o diagnóstico em JSON:\n\n$compactPrompt"

        return@withContext try {
            val rawOutputText = if (settings.provider == AiApiProvider.GEMINI) {
                callGeminiInteractionsApi(cleanBase, cleanKey, modelUsed, userPrompt)
            } else {
                callOpenAiCompatibleCloudApi(settings.provider, cleanBase, cleanKey, modelUsed, userPrompt)
            }

            if (rawOutputText.isBlank()) {
                return@withContext AiAnalyzeResponse(
                    ok = false,
                    error = "A API retornou uma resposta vazia."
                )
            }

            val parsed = extractJsonObject(rawOutputText)
            val rawRecs = parsed.getAsJsonArray("recommendations") ?: JsonArray()
            val recommendations = rawRecs.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val item = el.asJsonObject
                val itemName = item.get("name")?.asString?.takeIf { it.isNotBlank() } ?: "Item"
                val rawPath = item.get("path")?.asString.orEmpty()
                val resolvedPath = realPathByKey[rawPath.lowercase()]
                    ?: realPathByName[itemName.lowercase()]
                    ?: rawPath

                val itemTypeStr = item.get("itemType")?.asString?.lowercase()
                val itemType = if (itemTypeStr == "file") ItemType.FILE else ItemType.FOLDER
                val riskLevel = AiRiskLevel.fromWire(item.get("riskLevel")?.asString)
                val sizeBytes = runCatching { item.get("sizeBytes")?.asLong ?: 0L }.getOrDefault(0L)
                val whatIsIt = item.get("whatIsIt")?.asString
                    ?: item.get("description")?.asString
                    ?: ""
                val whyAndImpact = item.get("whyAndImpact")?.asString
                    ?: item.get("reason")?.asString
                    ?: ""

                AiRecommendationItem(
                    name = itemName,
                    path = resolvedPath,
                    itemType = itemType,
                    sizeBytes = sizeBytes,
                    riskLevel = riskLevel,
                    whatIsIt = whatIsIt,
                    whyAndImpact = whyAndImpact
                )
            }

            val summaryText = parsed.get("summary")?.asString
                ?: parsed.get("analysis_summary")?.asString
                ?: "Análise concluída com sucesso."

            val fallbackReclaimable = recommendations
                .filter { it.riskLevel == AiRiskLevel.SAFE || it.riskLevel == AiRiskLevel.CAUTION }
                .sumOf { it.sizeBytes }

            val estimatedReclaimable = runCatching {
                parsed.get("estimatedReclaimableBytes")?.asLong
            }.getOrNull() ?: fallbackReclaimable

            val report = AiAnalysisReport(
                providerUsed = settings.provider.displayName,
                modelUsed = modelUsed,
                generatedAt = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()),
                summary = summaryText,
                estimatedReclaimableBytes = estimatedReclaimable,
                recommendations = recommendations
            )

            AiAnalyzeResponse(ok = true, report = report)
        } catch (err: Throwable) {
            AiAnalyzeResponse(
                ok = false,
                error = err.message ?: "Erro inesperado ao consultar a API de IA."
            )
        }
    }

    private fun resolveBaseUrl(provider: AiApiProvider, rawUrl: String): Result<String> {
        val clean = rawUrl.trim().trimEnd('/').ifBlank { provider.defaultBaseUrl }
        return if (validateUrls) {
            CloudUrlValidator.validateCloudApiUrl(clean)
        } else {
            Result.success(clean)
        }
    }

    private fun callGeminiInteractionsApi(
        cleanBase: String,
        apiKey: String,
        model: String,
        userPrompt: String
    ): String {
        val bodyMap = mapOf(
            "model" to model,
            "system_instruction" to SYSTEM_INSTRUCTION,
            "input" to userPrompt,
            "store" to false,
            "response_format" to mapOf(
                "type" to "text",
                "mime_type" to "application/json",
                "schema" to REPORT_JSON_SCHEMA
            )
        )

        val req = Request.Builder()
            .url("$cleanBase/v1beta/interactions")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Api-Revision", "2026-05-20")
            .post(gson.toJson(bodyMap).toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { res ->
            val rawBody = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Erro na API Gemini (${res.code}): ${extractErrorMessage(rawBody)}")
            }
            return parseGeminiInteractionOutput(rawBody)
        }
    }

    private fun callOpenAiCompatibleCloudApi(
        provider: AiApiProvider,
        cleanBase: String,
        apiKey: String,
        model: String,
        userPrompt: String
    ): String {
        val basePayload = mutableMapOf<String, Any>(
            "model" to model,
            "temperature" to 0.2,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_INSTRUCTION),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )

        val structuredPayload = HashMap(basePayload).apply {
            put(
                "response_format",
                mapOf(
                    "type" to "json_schema",
                    "json_schema" to mapOf(
                        "name" to "disk_analysis_report",
                        "strict" to true,
                        "schema" to REPORT_JSON_SCHEMA
                    )
                )
            )
        }

        fun buildPost(payload: Map<String, Any>): Request {
            val builder = Request.Builder()
                .url("$cleanBase/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
            if (provider == AiApiProvider.OPENROUTER) {
                builder.addHeader("HTTP-Referer", "https://github.com/LucasMGamerPlay/RadarDisco")
                builder.addHeader("X-Title", "RadarDisco Android")
            }
            return builder.post(gson.toJson(payload).toRequestBody(jsonMediaType)).build()
        }

        var responseBody = ""
        var responseCode = 0
        var ok = false

        httpClient.newCall(buildPost(structuredPayload)).execute().use { res ->
            responseCode = res.code
            responseBody = res.body?.string().orEmpty()
            ok = res.isSuccessful
        }

        // Fallback sem response_format se o provedor rejeitar json_schema (HTTP 400)
        if (!ok && responseCode == 400) {
            httpClient.newCall(buildPost(basePayload)).execute().use { retryRes ->
                responseCode = retryRes.code
                responseBody = retryRes.body?.string().orEmpty()
                ok = retryRes.isSuccessful
            }
        }

        if (!ok) {
            throw IllegalStateException("Erro na API ${provider.displayName} ($responseCode): ${extractErrorMessage(responseBody)}")
        }

        val root = JsonParser.parseString(responseBody).asJsonObject
        val choice = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?: throw IllegalStateException("Resposta da API sem campo choices.")
        val message = choice.getAsJsonObject("message")
        val content = message?.get("content")?.asString?.trim().orEmpty()
        if (content.isNotEmpty()) return content

        val reasoning = message?.get("reasoning_content")?.asString.orEmpty()
        val fenced = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE).find(reasoning)
        if (fenced != null && !fenced.groupValues[1].contains("\"...\"")) {
            return fenced.groupValues[1]
        }

        if (choice.get("finish_reason")?.asString == "length") {
            throw IllegalStateException("O modelo atingiu o limite de tokens antes de concluir a resposta.")
        }

        return ""
    }

    internal fun parseGeminiInteractionOutput(rawBody: String): String {
        val root = JsonParser.parseString(rawBody).asJsonObject

        // 1. Formato atual Interactions API (Maio 2026): steps[] -> model_output -> content[]
        val steps = root.getAsJsonArray("steps")
        if (steps != null) {
            val sb = StringBuilder()
            for (stepEl in steps) {
                val step = stepEl.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                if (step.get("type")?.asString == "model_output") {
                    val contentArr = step.getAsJsonArray("content") ?: continue
                    for (contentEl in contentArr) {
                        val contentObj = contentEl.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                        if (contentObj.get("type")?.asString == "text") {
                            sb.append(contentObj.get("text")?.asString.orEmpty())
                        }
                    }
                }
            }
            if (sb.isNotBlank()) return sb.toString().trim()
        }

        // 2. Fallback legado: outputs[]
        val outputs = root.getAsJsonArray("outputs")
        if (outputs != null) {
            val sb = StringBuilder()
            for (outEl in outputs) {
                val outObj = outEl.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                if (outObj.get("type")?.asString == "text") {
                    sb.append(outObj.get("text")?.asString.orEmpty())
                }
            }
            if (sb.isNotBlank()) return sb.toString().trim()
        }

        // 3. Fallback direto output_text
        val directText = root.get("output_text")?.asString?.trim()
        if (!directText.isNullOrEmpty()) return directText

        return ""
    }

    internal fun extractJsonObject(rawText: String): JsonObject {
        val trimmed = rawText.trim()
        runCatching {
            val el = JsonParser.parseString(trimmed)
            if (el.isJsonObject) return el.asJsonObject
        }

        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(trimmed)
        if (fenceMatch != null) {
            val inner = fenceMatch.groupValues[1].trim()
            runCatching {
                val el = JsonParser.parseString(inner)
                if (el.isJsonObject) return el.asJsonObject
            }
        }

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val slice = trimmed.substring(firstBrace, lastBrace + 1)
            runCatching {
                val el = JsonParser.parseString(slice)
                if (el.isJsonObject) return el.asJsonObject
            }
        }

        throw IllegalStateException("A resposta do modelo não veio em formato JSON válido.")
    }

    private fun extractErrorMessage(rawBody: String): String {
        runCatching {
            val root = JsonParser.parseString(rawBody).asJsonObject
            val errObj = root.getAsJsonObject("error")
            if (errObj != null) {
                return errObj.get("message")?.asString ?: rawBody
            }
        }
        return rawBody.take(300)
    }

    internal fun buildCompactPrompt(
        snapshot: ScanSnapshot,
        appStats: List<AppStorageEntry> = emptyList()
    ): String {
        val seenPaths = HashSet<String>()
        val folderLines = mutableListOf<String>()

        fun displayPath(path: String): String {
            return if (snapshot.sourceType == ScanSourceType.SAF_TREE) {
                FolderClassifier.normalizePosixPath(path)
            } else {
                path
            }
        }

        for (h in snapshot.spaceHogs.take(16)) {
            val key = h.path.lowercase()
            if (!seenPaths.add(key)) continue
            folderLines.add(
                "FOLDER | name=\"${h.name}\" | sizeBytes=${h.size} (${formatHumanSize(h.size)}) | kind=\"${h.kind}\" | path=\"${displayPath(h.path)}\""
            )
        }

        val sourceFolders = if (snapshot.topFolders.isNotEmpty()) {
            snapshot.topFolders
        } else {
            snapshot.items
                .filter { it.type == ItemType.FOLDER }
                .map {
                    com.radardisco.android.model.TopFolderEntry(
                        name = it.name,
                        path = it.path,
                        size = it.size,
                        fileCount = it.fileCount,
                        category = it.category,
                        specialBadge = it.specialBadge
                    )
                }
        }

        for (f in sourceFolders) {
            if (folderLines.size >= 26) break
            val key = f.path.lowercase()
            if (!seenPaths.add(key)) continue
            val badgeInfo = f.specialBadge?.let { " | badge=\"$it\"" }.orEmpty()
            folderLines.add(
                "FOLDER | name=\"${f.name}\" | sizeBytes=${f.size} (${formatHumanSize(f.size)})$badgeInfo | path=\"${displayPath(f.path)}\""
            )
        }

        val fileLines = snapshot.topFiles.take(10).map { file ->
            "FILE | name=\"${file.name}\" | sizeBytes=${file.size} (${formatHumanSize(file.size)}) | cat=\"${file.category.id}\" | path=\"${displayPath(file.path)}\""
        }

        val parts = mutableListOf(
            "Raiz: ${snapshot.rootPath} | Total Mapeado: ${formatHumanSize(snapshot.stats.totalBytes)} (${snapshot.stats.totalBytes} bytes) | Arquivos: ${snapshot.stats.scannedFiles}",
            "--- PASTAS PRINCIPAIS E CACHES ---",
            *folderLines.toTypedArray(),
            "--- MAIORES ARQUIVOS INDIVIDUAIS ---",
            *fileLines.toTypedArray()
        )

        if (appStats.isNotEmpty()) {
            parts.add("--- APPS INSTALADOS (USO E CACHE) ---")
            appStats.take(8).forEach { app ->
                parts.add(
                    "APP | name=\"${app.appName}\" (${app.packageName}) | total=${formatHumanSize(app.totalBytes)} | cache=${formatHumanSize(app.cacheBytes)}"
                )
            }
        }

        return parts.joinToString("\n")
    }

    private fun formatHumanSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val idx = min(units.lastIndex, floor(ln(bytes.toDouble()) / ln(1024.0)).toInt())
        val value = bytes.toDouble() / 1024.0.pow(idx.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[idx])
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val SYSTEM_INSTRUCTION = """
            Especialista em armazenamento Android, apps, mídia, jogos mobile e IA.
            Classifique os itens escaneados em 3 níveis (riskLevel):
            - "safe": Caches regeneráveis (.thumbnails, cache, code_cache, gpu_cache, Temp, .Trash), instaladores .apk/.xapk antigos em Download após instalação, arquivos temporários de atualização.
            - "caution": Pastas de WhatsApp/Telegram (WhatsApp Video, WhatsApp Images, Sent, Documents), Downloads pessoais, gravações de tela, backups locais (.crypt14/.crypt15), modelos IA (.gguf, .tflite, .onnx) se não usados, arquivos compactados grandes (.zip/.rar).
            - "danger": DCIM/Camera (fotos pessoais), Documents/Pictures importantes, Android/obb de jogos ativos, Databases do WhatsApp sem backup em nuvem, diretórios essenciais do sistema.

            IMPORTANTE:
            - Seja MUITO conciso e direto no raciocínio para economizar tokens.
            - Responda SEMPRE em pt-BR, usando EXATAMENTE o "path", "name" e "sizeBytes" recebidos.
            - Selecione de 8 a 12 itens mais relevantes (priorize safe e caution grandes, mais 2-3 alertas danger importantes).
            - Retorne APENAS JSON válido neste formato exato:
            {"summary":"...","estimatedReclaimableBytes":12345,"recommendations":[{"name":"...","path":"...","itemType":"folder","sizeBytes":12345,"riskLevel":"safe","whatIsIt":"...","whyAndImpact":"..."}]}
        """.trimIndent()

        private val REPORT_JSON_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "summary" to mapOf(
                    "type" to "string",
                    "description" to "Resumo executivo em português do espaço analisado e recomendações principais."
                ),
                "estimatedReclaimableBytes" to mapOf(
                    "type" to "number",
                    "description" to "Soma aproximada em bytes dos itens safe/caution facilmente recuperáveis."
                ),
                "recommendations" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "properties" to mapOf(
                            "name" to mapOf("type" to "string"),
                            "path" to mapOf("type" to "string"),
                            "itemType" to mapOf("type" to "string", "enum" to listOf("folder", "file")),
                            "sizeBytes" to mapOf("type" to "number"),
                            "riskLevel" to mapOf("type" to "string", "enum" to listOf("safe", "caution", "danger")),
                            "whatIsIt" to mapOf("type" to "string"),
                            "whyAndImpact" to mapOf("type" to "string")
                        ),
                        "required" to listOf("name", "path", "itemType", "sizeBytes", "riskLevel", "whatIsIt", "whyAndImpact")
                    )
                )
            ),
            "required" to listOf("summary", "estimatedReclaimableBytes", "recommendations")
        )
    }
}
