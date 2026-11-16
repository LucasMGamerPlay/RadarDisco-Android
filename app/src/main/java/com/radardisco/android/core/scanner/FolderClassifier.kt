package com.radardisco.android.core.scanner

import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ItemType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class FolderKindInfo(
    val badge: String? = null,
    val hogKind: String? = null
)

object FolderClassifier {

    private val AI_EXTS = setOf(
        ".safetensors", ".gguf", ".ckpt", ".onnx", ".pt", ".pth",
        ".bin", ".ggml", ".tflite", ".pb", ".h5", ".mlc", ".task"
    )

    private val GAME_APP_EXTS = setOf(
        ".apk", ".apks", ".xapk", ".aab", ".obb", ".exe", ".dll",
        ".pak", ".asar", ".msi", ".iso", ".cab", ".wad", ".vpk",
        ".bundle", ".unity3d", ".rpf", ".ucas", ".utoc", ".so"
    )

    private val MEDIA_EXTS = setOf(
        ".mp4", ".mkv", ".mov", ".avi", ".webm", ".wmv", ".flv", ".m4v", ".3gp",
        ".mp3", ".wav", ".flac", ".ogg", ".aac", ".m4a", ".opus",
        ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".tiff",
        ".psd", ".raw", ".dng", ".heic", ".heif", ".svg"
    )

    private val ARCHIVE_EXTS = setOf(
        ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".tgz",
        ".vhdx", ".vhd", ".vmdk", ".ova", ".img", ".bak", ".dmp"
    )

    private val DOC_EXTS = setOf(
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".txt", ".md", ".csv", ".json", ".xml", ".yaml", ".yml",
        ".epub", ".sql", ".db", ".sqlite", ".crypt14", ".crypt15"
    )

    private val DEV_EXTS = setOf(
        ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".kt", ".kts",
        ".py", ".pyc", ".pyd", ".rs", ".go", ".java", ".jar", ".class",
        ".c", ".cpp", ".h", ".cs", ".pdb", ".obj", ".lib", ".map"
    )

    fun createEmptyCategoryMap(): MutableMap<FileCategory, Long> =
        FileCategory.entries.associateWith { 0L }.toMutableMap()

    fun classifyFile(fileName: String, folderContextBadge: String? = null): FileCategory {
        when (folderContextBadge) {
            "node_modules", "Ambiente Virtual", "Git" -> return FileCategory.DEV_DEPS
            "Modelo IA" -> return FileCategory.AI_MODELS
            "Jogos / OBB" -> return FileCategory.GAMES_APPS
        }
        val dotIndex = fileName.lastIndexOf('.')
        val ext = if (dotIndex >= 0) fileName.substring(dotIndex).lowercase() else ""
        return when {
            ext in AI_EXTS -> FileCategory.AI_MODELS
            ext in GAME_APP_EXTS -> FileCategory.GAMES_APPS
            ext in MEDIA_EXTS -> FileCategory.MEDIA
            ext in ARCHIVE_EXTS -> FileCategory.ARCHIVES
            ext in DOC_EXTS -> FileCategory.DOCUMENTS
            ext in DEV_EXTS -> FileCategory.DEV_DEPS
            else -> FileCategory.OTHER
        }
    }

    fun detectFolderKind(folderName: String, fullPath: String = ""): FolderKindInfo {
        val lower = folderName.lowercase()
        val pathLower = fullPath.lowercase()

        return when {
            lower == "node_modules" ->
                FolderKindInfo(badge = "node_modules", hogKind = "Dependências Node (node_modules)")
            lower in setOf(".venv", "venv", "conda", "site-packages") ->
                FolderKindInfo(badge = "Ambiente Virtual", hogKind = "Ambiente Python (.venv)")
            lower == ".git" ->
                FolderKindInfo(badge = "Git", hogKind = "Repositório Git (.git)")
            lower in setOf(
                ".thumbnails", "thumbnails", "cache", "caches", "code_cache",
                "image_cache", "video_cache", "gpucache", "gpu_cache",
                "code cache", "shadercache", ".cache", "okhttp_cache"
            ) -> FolderKindInfo(badge = "Cache", hogKind = "Pasta de Cache / Miniaturas")
            lower in setOf("temp", "tmp", "crashdumps", ".trash", ".trashed") ->
                FolderKindInfo(badge = "Temp", hogKind = "Arquivos Temporários / Lixo")
            lower in setOf(
                "huggingface", "ollama", "checkpoints", "loras",
                "diffusion_models", "unet", "gguf", "llm_models"
            ) -> FolderKindInfo(badge = "Modelo IA", hogKind = "Modelos & Pesos de IA")
            lower in setOf("whatsapp", "whatsapp business", "whatsapp video", "whatsapp images", "whatsapp documents", "whatsapp voice notes") ||
                (lower == "sent" && pathLower.contains("whatsapp")) ||
                (lower == "databases" && pathLower.contains("whatsapp")) ->
                FolderKindInfo(badge = "WhatsApp", hogKind = "Mídia / Dados do WhatsApp")
            lower in setOf("telegram", "telegram video", "telegram documents", "telegram images", "telegram audio") ->
                FolderKindInfo(badge = "Telegram", hogKind = "Mídia do Telegram")
            lower in setOf("obb", "steamapps") ->
                FolderKindInfo(badge = "Jogos / OBB", hogKind = "Dados de Jogos (OBB)")
            lower in setOf("screenrecordings", "screenrecorder") ->
                FolderKindInfo(badge = "Gravações", hogKind = "Gravações de Tela")
            lower in setOf("download", "downloads") ->
                FolderKindInfo(badge = "Downloads", hogKind = "Pasta Downloads")
            lower in setOf("lost.dir", ".android_secure", "system volume information", "\$recycle.bin") ->
                FolderKindInfo(badge = "Sistema")
            else -> FolderKindInfo()
        }
    }

    fun getDominantCategory(
        categorySizes: Map<FileCategory, Long>,
        specialBadge: String? = null
    ): FileCategory {
        when (specialBadge) {
            "node_modules", "Ambiente Virtual", "Git" -> return FileCategory.DEV_DEPS
            "Modelo IA" -> return FileCategory.AI_MODELS
            "Jogos / OBB" -> return FileCategory.GAMES_APPS
            "WhatsApp", "Telegram", "Gravações" -> {
                if ((categorySizes[FileCategory.MEDIA] ?: 0L) > 0L) return FileCategory.MEDIA
            }
        }
        var bestCategory = FileCategory.OTHER
        var bestValue = 0L
        for ((cat, value) in categorySizes) {
            if (value > bestValue) {
                bestValue = value
                bestCategory = cat
            }
        }
        return bestCategory
    }

    fun normalizePosixPath(path: String): String {
        if (path.startsWith("content://")) return path
        val normalized = path.replace('\\', '/').trim()
        if (normalized == "/") return "/"
        return normalized.trimEnd('/')
    }

    fun buildSmartWebSearchUrl(name: String, targetPath: String, itemType: ItemType): String {
        val cleanPath = targetPath
            .replace('\\', '/')
            .replace(Regex("^/storage/emulated/\\d+/?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^/sdcard/?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^content://[^/]+/tree/[^/]+/?", RegexOption.IGNORE_CASE), "")

        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        val genericContext = if (segments.size > 3) {
            segments.takeLast(3).joinToString("/")
        } else {
            segments.joinToString("/")
        }

        val kindWord = if (itemType == ItemType.FOLDER) "pasta" else "arquivo"
        val query = if (genericContext.isNotBlank() && !genericContext.equals(name, ignoreCase = true)) {
            "\"$name\" \"$genericContext\" $kindWord Android o que é posso apagar"
        } else {
            "\"$name\" $kindWord Android o que é posso apagar"
        }

        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return "https://www.google.com/search?q=$encoded"
    }
}
