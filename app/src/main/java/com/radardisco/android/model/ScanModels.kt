package com.radardisco.android.model

enum class FileCategory(val id: String) {
    AI_MODELS("ai_models"),
    GAMES_APPS("games_apps"),
    MEDIA("media"),
    DEV_DEPS("dev_deps"),
    ARCHIVES("archives"),
    DOCUMENTS("documents"),
    OTHER("other")
}

enum class ItemType(val wireValue: String) {
    FOLDER("folder"),
    FILE("file")
}

enum class ScanSourceType {
    FILE_SYSTEM,
    SAF_TREE
}

enum class ScanStatus {
    IDLE,
    SCANNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class StorageVolumeInfo(
    val id: String,
    val mountPath: String,
    val label: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Float,
    val isRemovable: Boolean = false
)

data class QuickScanTarget(
    val name: String,
    val path: String,
    val subtitle: String
)

data class BreadcrumbItem(
    val name: String,
    val path: String,
    val size: Long
)

data class ScanChildPreview(
    val name: String,
    val path: String,
    val size: Long,
    val category: FileCategory,
    val type: ItemType
)

data class ScanItemSnapshot(
    val name: String,
    val path: String,
    val type: ItemType,
    val size: Long,
    val percentOfParent: Float,
    val percentOfRoot: Float,
    val fileCount: Int,
    val folderCount: Int,
    val isScanning: Boolean,
    val accessDenied: Boolean = false,
    val category: FileCategory,
    val specialBadge: String? = null,
    val childrenPreview: List<ScanChildPreview> = emptyList()
)

data class TopFolderEntry(
    val name: String,
    val path: String,
    val size: Long,
    val fileCount: Int,
    val category: FileCategory,
    val specialBadge: String? = null
)

data class TopFileEntry(
    val name: String,
    val path: String,
    val parentPath: String,
    val size: Long,
    val category: FileCategory
)

data class SpaceHogEntry(
    val name: String,
    val path: String,
    val size: Long,
    val fileCount: Int,
    val kind: String,
    val category: FileCategory
)

data class AppStorageEntry(
    val packageName: String,
    val appName: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val totalBytes: Long
)

data class ScanStats(
    val scannedFiles: Int = 0,
    val scannedFolders: Int = 0,
    val skippedCount: Int = 0,
    val totalBytes: Long = 0L,
    val elapsedMs: Long = 0L,
    val filesPerSec: Int = 0,
    val currentPath: String = ""
)

data class ScanSnapshot(
    val status: ScanStatus = ScanStatus.IDLE,
    val sourceType: ScanSourceType = ScanSourceType.FILE_SYSTEM,
    val rootPath: String = "",
    val focusPath: String = "",
    val focusSize: Long = 0L,
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val items: List<ScanItemSnapshot> = emptyList(),
    val topFolders: List<TopFolderEntry> = emptyList(),
    val topFiles: List<TopFileEntry> = emptyList(),
    val spaceHogs: List<SpaceHogEntry> = emptyList(),
    val categoryTotals: Map<FileCategory, Long> = FileCategory.entries.associateWith { 0L },
    val errorMessage: String? = null,
    val stats: ScanStats = ScanStats()
)
