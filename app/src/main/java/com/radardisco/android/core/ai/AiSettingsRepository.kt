package com.radardisco.android.core.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radardisco.android.model.AiApiProvider
import com.radardisco.android.model.AiSettings
import kotlinx.coroutines.flow.first
import java.net.URI

private val Context.aiSettingsDataStore by preferencesDataStore(name = "radardisco_ai_settings")

object CloudUrlValidator {
    private val PRIVATE_IPV4_REGEX = Regex(
        "^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.|169\\.254\\.|0\\.0\\.0\\.0)"
    )

    /**
     * Garante que a versão Android utilize exclusivamente APIs na nuvem via HTTPS,
     * bloqueando servidores locais (localhost, 127.0.0.1, emulador 10.0.2.2, rede LAN ou HTTP).
     */
    fun validateCloudApiUrl(rawUrl: String): Result<String> {
        val clean = rawUrl.trim().trimEnd('/')
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe a URL HTTPS da API na nuvem."))
        }

        val uri = runCatching { URI(clean) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("URL da API inválida."))

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return Result.failure(
                IllegalArgumentException("Somente URLs HTTPS de APIs na nuvem são permitidas no Android (modelos locais não são suportados).")
            )
        }

        val host = uri.host?.lowercase()?.trim('[', ']')
        if (host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("URL da API sem domínio válido."))
        }

        val isLocalHost = host == "localhost" ||
            host == "::1" ||
            host.endsWith(".local") ||
            host.endsWith(".localhost") ||
            PRIVATE_IPV4_REGEX.containsMatchIn(host)

        if (isLocalHost) {
            return Result.failure(
                IllegalArgumentException("Servidores locais ($host) não são permitidos nesta versão Android; utilize uma API na nuvem.")
            )
        }

        return Result.success(clean)
    }
}

class AiSettingsRepository(private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider_id")
        val API_KEY_ENC = stringPreferencesKey("api_key_enc")
        val MODEL = stringPreferencesKey("model")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
    }

    suspend fun load(): AiSettings {
        val prefs = context.aiSettingsDataStore.data.first()
        val provider = AiApiProvider.fromId(prefs[Keys.PROVIDER])
        val decryptedKey = ApiKeyCrypto.decrypt(prefs[Keys.API_KEY_ENC].orEmpty())
        val rawModel = prefs[Keys.MODEL]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
        val normalizedModel = if (provider == AiApiProvider.GEMINI && Regex("^gemini-(1\\.5|2\\.0|2\\.5)").containsMatchIn(rawModel)) {
            AiApiProvider.GEMINI.defaultModel
        } else {
            rawModel
        }
        val baseUrl = prefs[Keys.CUSTOM_BASE_URL]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

        return AiSettings(
            provider = provider,
            apiKey = decryptedKey,
            model = normalizedModel,
            customBaseUrl = baseUrl
        )
    }

    suspend fun save(next: AiSettings): Result<AiSettings> {
        val effectiveUrl = if (next.provider == AiApiProvider.CUSTOM_OPENAI_API) {
            next.customBaseUrl
        } else {
            next.provider.defaultBaseUrl
        }

        val validatedUrl = CloudUrlValidator.validateCloudApiUrl(effectiveUrl)
            .getOrElse { return Result.failure(it) }

        val cleanModel = next.model.trim().ifBlank { next.provider.defaultModel }
        val cleanKey = next.apiKey.trim()
        val encryptedKey = ApiKeyCrypto.encrypt(cleanKey)

        context.aiSettingsDataStore.edit { prefs ->
            prefs[Keys.PROVIDER] = next.provider.id
            prefs[Keys.API_KEY_ENC] = encryptedKey
            prefs[Keys.MODEL] = cleanModel
            prefs[Keys.CUSTOM_BASE_URL] = validatedUrl
        }

        return Result.success(
            AiSettings(
                provider = next.provider,
                apiKey = cleanKey,
                model = cleanModel,
                customBaseUrl = validatedUrl
            )
        )
    }
}
