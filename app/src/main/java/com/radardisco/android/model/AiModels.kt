package com.radardisco.android.model

/**
 * Provedores de IA suportados no Android — exclusivamente via APIs na nuvem.
 * Modelos locais (Ollama, LM Studio, servidores localhost) não são suportados nesta versão.
 */
enum class AiApiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val presetModels: List<String>
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini API",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-3.8-flash",
        presetModels = listOf(
            "gemini-3.8-flash",
            "gemini-4-argon",
            "gemini-3.5-flash-lite"
        )
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI API",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4.1-mini",
        presetModels = listOf(
            "gpt-4.1-mini",
            "gpt-4.1",
            "gpt-4o-mini"
        )
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter API",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "google/gemini-2.5-flash",
        presetModels = listOf(
            "google/gemini-2.5-flash",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-haiku"
        )
    ),
    GROQ(
        id = "groq",
        displayName = "Groq Cloud API",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        presetModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant"
        )
    ),
    CUSTOM_OPENAI_API(
        id = "custom_api",
        displayName = "Outra API na Nuvem (HTTPS)",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "",
        presetModels = emptyList()
    );

    companion object {
        fun fromId(id: String?): AiApiProvider =
            entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

data class AiSettings(
    val provider: AiApiProvider = AiApiProvider.GEMINI,
    val apiKey: String = "",
    val model: String = AiApiProvider.GEMINI.defaultModel,
    val customBaseUrl: String = AiApiProvider.GEMINI.defaultBaseUrl
) {
    fun effectiveBaseUrl(): String {
        val raw = if (provider == AiApiProvider.CUSTOM_OPENAI_API) {
            customBaseUrl
        } else {
            customBaseUrl.ifBlank { provider.defaultBaseUrl }
        }
        return raw.trim().trimEnd('/')
    }

    fun effectiveModel(): String = model.trim().ifBlank { provider.defaultModel }
}

enum class AiRiskLevel(val wireValue: String) {
    SAFE("safe"),
    CAUTION("caution"),
    DANGER("danger");

    companion object {
        fun fromWire(value: String?): AiRiskLevel = when (value?.trim()?.lowercase()) {
            "safe" -> SAFE
            "danger" -> DANGER
            else -> CAUTION
        }
    }
}

data class AiRecommendationItem(
    val name: String,
    val path: String,
    val itemType: ItemType,
    val sizeBytes: Long,
    val riskLevel: AiRiskLevel,
    val whatIsIt: String,
    val whyAndImpact: String
)

data class AiAnalysisReport(
    val providerUsed: String,
    val modelUsed: String,
    val generatedAt: String,
    val summary: String,
    val estimatedReclaimableBytes: Long,
    val recommendations: List<AiRecommendationItem>
)

data class AiAnalyzeResponse(
    val ok: Boolean,
    val report: AiAnalysisReport? = null,
    val error: String? = null,
    val needsSetup: Boolean = false
)
