package com.radardisco.android

import com.radardisco.android.core.ai.CloudAiService
import com.radardisco.android.core.ai.CloudUrlValidator
import com.radardisco.android.model.AiApiProvider
import com.radardisco.android.model.AiRiskLevel
import com.radardisco.android.model.AiSettings
import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanItemSnapshot
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.model.ScanStats
import com.radardisco.android.model.ScanStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudAiServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun urlValidatorBlocksLocalhostPrivateIpsAndHttp() {
        assertTrue(CloudUrlValidator.validateCloudApiUrl("http://api.openai.com/v1").isFailure)
        assertTrue(CloudUrlValidator.validateCloudApiUrl("https://localhost:11434/v1").isFailure)
        assertTrue(CloudUrlValidator.validateCloudApiUrl("https://127.0.0.1:8080/v1").isFailure)
        assertTrue(CloudUrlValidator.validateCloudApiUrl("https://10.0.2.2:11434/v1").isFailure)
        assertTrue(CloudUrlValidator.validateCloudApiUrl("https://192.168.1.50/v1").isFailure)
        assertTrue(CloudUrlValidator.validateCloudApiUrl("https://ollama.local/v1").isFailure)

        val ok = CloudUrlValidator.validateCloudApiUrl("https://api.openai.com/v1/")
        assertTrue(ok.isSuccess)
        assertEquals("https://api.openai.com/v1", ok.getOrNull())
    }

    @Test
    fun parsesGeminiInteractionsStepsResponseAndVerifiesHeaders() = runBlocking {
        val jsonReport = """
            {
              "summary": "Downloads antigos concentram 2.1 GB recuperáveis.",
              "estimatedReclaimableBytes": 2100000000,
              "recommendations": [
                {
                  "name": "instalador.apk",
                  "path": "/storage/emulated/0/Download/instalador.apk",
                  "itemType": "file",
                  "sizeBytes": 2100000000,
                  "riskLevel": "safe",
                  "whatIsIt": "Pacote APK antigo já instalado.",
                  "whyAndImpact": "Pode apagar com segurança sem afetar o aplicativo."
                }
              ]
            }
        """.trimIndent().replace("\"", "\\\"").replace("\n", "\\n")

        val interactionsResponse = """
            {
              "id": "inter_123",
              "status": "completed",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "$jsonReport"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(interactionsResponse))

        val service = CloudAiService(httpClient = OkHttpClient(), validateUrls = false)
        val baseUrl = server.url("").toString().trimEnd('/')
        val response = service.analyzeSnapshot(
            snapshot = sampleSnapshot(),
            settings = AiSettings(
                provider = AiApiProvider.GEMINI,
                apiKey = "AIza-test-key",
                model = "gemini-3.8-flash",
                customBaseUrl = baseUrl
            )
        )

        val recorded = server.takeRequest()
        assertEquals("/v1beta/interactions", recorded.path)
        assertEquals("AIza-test-key", recorded.getHeader("x-goog-api-key"))
        assertEquals("2026-05-20", recorded.getHeader("Api-Revision"))

        assertTrue(response.ok)
        val report = response.report
        assertNotNull(report)
        assertEquals("Downloads antigos concentram 2.1 GB recuperáveis.", report!!.summary)
        assertEquals(2100000000L, report.estimatedReclaimableBytes)
        assertEquals(1, report.recommendations.size)
        assertEquals(AiRiskLevel.SAFE, report.recommendations.first().riskLevel)
        assertEquals("/storage/emulated/0/Download/instalador.apk", report.recommendations.first().path)
    }

    @Test
    fun openAiFallbackWhenStructuredOutputReturns400AndExtractsFencedJson() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"json_schema unsupported"}}"""))

        val fencedReport = """
            ```json
            {
              "summary": "Cache temporário seguro para limpeza.",
              "estimatedReclaimableBytes": 50000000,
              "recommendations": [
                {
                  "name": "Cache",
                  "path": "nome_inventado",
                  "itemType": "folder",
                  "sizeBytes": 50000000,
                  "riskLevel": "caution",
                  "whatIsIt": "Pasta de cache.",
                  "whyAndImpact": "Apps recriam se necessário."
                }
              ]
            }
            ```
        """.trimIndent()

        val gsonFriendly = com.google.gson.Gson().toJson(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "finish_reason" to "stop",
                        "message" to mapOf(
                            "content" to "",
                            "reasoning_content" to fencedReport
                        )
                    )
                )
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gsonFriendly))

        val service = CloudAiService(httpClient = OkHttpClient(), validateUrls = false)
        val baseUrl = server.url("v1").toString().trimEnd('/')
        val response = service.analyzeSnapshot(
            snapshot = sampleSnapshot(),
            settings = AiSettings(
                provider = AiApiProvider.CUSTOM_OPENAI_API,
                apiKey = "sk-test",
                model = "cloud-model-1",
                customBaseUrl = baseUrl
            )
        )

        assertEquals(2, server.requestCount)
        assertTrue(response.ok)
        val rec = response.report!!.recommendations.first()
        assertEquals(AiRiskLevel.CAUTION, rec.riskLevel)
        // Verifica resolução automática do caminho real quando IA alucina o path mas acerta o nome
        assertEquals("/storage/emulated/0/Cache", rec.path)
    }

    @Test
    fun blocksLocalhostInRealValidationMode() = runBlocking {
        val service = CloudAiService(httpClient = OkHttpClient(), validateUrls = true)
        val response = service.analyzeSnapshot(
            snapshot = sampleSnapshot(),
            settings = AiSettings(
                provider = AiApiProvider.CUSTOM_OPENAI_API,
                apiKey = "sk-test",
                model = "local-model",
                customBaseUrl = "http://127.0.0.1:11434/v1"
            )
        )
        assertFalse(response.ok)
        assertTrue(response.needsSetup)
    }

    private fun sampleSnapshot(): ScanSnapshot = ScanSnapshot(
        status = ScanStatus.COMPLETED,
        rootPath = "/storage/emulated/0",
        focusPath = "/storage/emulated/0",
        focusSize = 2150000000L,
        items = listOf(
            ScanItemSnapshot(
                name = "Cache",
                path = "/storage/emulated/0/Cache",
                type = ItemType.FOLDER,
                size = 50000000L,
                percentOfParent = 2.3f,
                percentOfRoot = 2.3f,
                fileCount = 12,
                folderCount = 1,
                isScanning = false,
                category = FileCategory.DEV_DEPS,
                specialBadge = "Cache"
            ),
            ScanItemSnapshot(
                name = "instalador.apk",
                path = "/storage/emulated/0/Download/instalador.apk",
                type = ItemType.FILE,
                size = 2100000000L,
                percentOfParent = 97.7f,
                percentOfRoot = 97.7f,
                fileCount = 1,
                folderCount = 0,
                isScanning = false,
                category = FileCategory.GAMES_APPS
            )
        ),
        stats = ScanStats(scannedFiles = 13, scannedFolders = 2, totalBytes = 2150000000L)
    )
}
