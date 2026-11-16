package com.radardisco.android.core.update

import com.google.gson.JsonParser
import com.radardisco.android.model.UpdateState
import com.radardisco.android.model.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubUpdateChecker(
    private val owner: String = "LucasMGamerPlay",
    private val repo: String = "RadarDisco-Android",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun checkLatestRelease(
        currentVersion: String,
        manual: Boolean = true
    ): UpdateState = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "RadarDisco-Android/$currentVersion")
                .get()
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext if (!manual) {
                        UpdateState(status = UpdateStatus.IDLE, currentVersion = currentVersion)
                    } else {
                        UpdateState(
                            status = UpdateStatus.ERROR,
                            currentVersion = currentVersion,
                            error = "Não foi possível consultar as Releases do GitHub (HTTP ${res.code}).",
                            manualCheck = true
                        )
                    }
                }

                val raw = res.body?.string().orEmpty()
                val root = JsonParser.parseString(raw).asJsonObject
                val tagName = root.get("tag_name")?.asString?.trim().orEmpty()
                val htmlUrl = root.get("html_url")?.asString
                val assets = root.getAsJsonArray("assets")

                var apkUrl: String? = null
                if (assets != null) {
                    for (assetEl in assets) {
                        val asset = assetEl.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                        val name = asset.get("name")?.asString.orEmpty()
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.get("browser_download_url")?.asString
                            break
                        }
                    }
                }

                val cleanLatest = tagName.removePrefix("v").removePrefix("V")
                if (cleanLatest.isBlank()) {
                    return@withContext UpdateState(
                        status = UpdateStatus.NOT_AVAILABLE,
                        currentVersion = currentVersion,
                        latestVersion = currentVersion,
                        manualCheck = manual
                    )
                }

                val cmp = SemverComparator.compareSemverVersions(cleanLatest, currentVersion)
                if (cmp > 0 && apkUrl != null) {
                    UpdateState(
                        status = UpdateStatus.AVAILABLE,
                        currentVersion = currentVersion,
                        latestVersion = cleanLatest,
                        releaseUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        manualCheck = manual
                    )
                } else {
                    UpdateState(
                        status = UpdateStatus.NOT_AVAILABLE,
                        currentVersion = currentVersion,
                        latestVersion = cleanLatest,
                        releaseUrl = htmlUrl,
                        manualCheck = manual
                    )
                }
            }
        } catch (_: Throwable) {
            if (!manual) {
                UpdateState(status = UpdateStatus.IDLE, currentVersion = currentVersion)
            } else {
                UpdateState(
                    status = UpdateStatus.ERROR,
                    currentVersion = currentVersion,
                    error = "Falha de conexão ao verificar atualizações no GitHub.",
                    manualCheck = true
                )
            }
        }
    }
}
