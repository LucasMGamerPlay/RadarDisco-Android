package com.radardisco.android.model

enum class UpdateStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    NOT_AVAILABLE,
    ERROR
}

data class UpdateState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val currentVersion: String = "1.0.0",
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val apkDownloadUrl: String? = null,
    val error: String? = null,
    val manualCheck: Boolean = false
)
