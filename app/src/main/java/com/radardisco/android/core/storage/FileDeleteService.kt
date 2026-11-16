package com.radardisco.android.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.radardisco.android.core.scanner.FolderClassifier
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

data class DeleteResult(
    val ok: Boolean,
    val partial: Boolean = false,
    val freedBytes: Long = 0L,
    val message: String? = null,
    val error: String? = null
)

object FileDeleteService {

    private val STATIC_PROTECTED_PATHS = setOf(
        "/",
        "/system",
        "/vendor",
        "/product",
        "/system_ext",
        "/apex",
        "/data",
        "/proc",
        "/dev",
        "/sys",
        "/mnt",
        "/storage",
        "/storage/emulated",
        "/storage/emulated/0",
        "/sdcard",
        "/storage/emulated/0/android",
        "/storage/emulated/0/android/data",
        "/storage/emulated/0/android/obb",
        "/sdcard/android",
        "/sdcard/android/data",
        "/sdcard/android/obb"
    )

    private val SD_ROOT_REGEX = Regex("^/storage/[0-9a-f]{4}-[0-9a-f]{4}$", RegexOption.IGNORE_CASE)
    private val SD_ANDROID_REGEX = Regex("^/storage/[0-9a-f]{4}-[0-9a-f]{4}/android(?:/data|/obb)?$", RegexOption.IGNORE_CASE)
    private val SYSTEM_PREFIXES = listOf(
        "/system/", "/vendor/", "/product/", "/system_ext/", "/apex/", "/proc/", "/dev/", "/sys/"
    )

    fun isProtectedSystemPath(
        targetPath: String,
        volumeRoots: Set<String> = emptySet(),
        protectedRoot: String? = null
    ): Boolean {
        if (targetPath.isBlank()) return true
        if (targetPath.startsWith("content://")) {
            return protectedRoot != null && targetPath == protectedRoot
        }

        val norm = FolderClassifier.normalizePosixPath(targetPath)
        val lower = norm.lowercase()

        if (lower in STATIC_PROTECTED_PATHS) return true
        if (SD_ROOT_REGEX.matches(lower) || SD_ANDROID_REGEX.matches(lower)) return true
        if (SYSTEM_PREFIXES.any { lower.startsWith(it) }) return true

        val normalizedRoots = volumeRoots.map { FolderClassifier.normalizePosixPath(it).lowercase() }
        if (lower in normalizedRoots) return true
        if (protectedRoot != null && !protectedRoot.startsWith("content://")) {
            if (lower == FolderClassifier.normalizePosixPath(protectedRoot).lowercase()) return true
        }
        return false
    }

    private fun pathExistsNoFollow(file: File): Boolean =
        runCatching { Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) }
            .getOrElse { file.exists() }

    private data class RecursiveDeleteStats(
        var deletedFilesOrLinks: Int = 0,
        var lockedItems: Int = 0,
        var freedBytes: Long = 0L
    )

    private fun deleteNoFollow(target: File, stats: RecursiveDeleteStats) {
        if (Files.isSymbolicLink(target.toPath())) {
            if (target.delete() || !pathExistsNoFollow(target)) {
                stats.deletedFilesOrLinks++
            } else {
                stats.lockedItems++
            }
            return
        }
        if (target.isDirectory) {
            val children = target.listFiles()
            if (children == null) {
                stats.lockedItems++
                return
            }
            for (child in children) {
                deleteNoFollow(child, stats)
            }
            target.delete()
            return
        }

        val fileSize = target.length().coerceAtLeast(0L)
        val deleted = target.delete() || run {
            runCatching { target.setWritable(true) }
            target.delete()
        }
        if (deleted || !pathExistsNoFollow(target)) {
            stats.deletedFilesOrLinks++
            stats.freedBytes += fileSize
        } else {
            stats.lockedItems++
        }
    }

    fun executeDirectDelete(
        rawPath: String,
        volumeRoots: Set<String> = emptySet(),
        protectedRoot: String? = null
    ): DeleteResult {
        val norm = FolderClassifier.normalizePosixPath(rawPath)
        if (isProtectedSystemPath(norm, volumeRoots, protectedRoot)) {
            return DeleteResult(
                ok = false,
                error = "Este diretório faz parte do sistema Android ou é a raiz do armazenamento e está protegido contra exclusão."
            )
        }

        val target = File(norm)
        if (!pathExistsNoFollow(target)) {
            return DeleteResult(
                ok = false,
                error = "O caminho não foi encontrado no armazenamento: $norm"
            )
        }

        val isDir = target.isDirectory && !Files.isSymbolicLink(target.toPath())

        if (!isDir) {
            val stats = RecursiveDeleteStats()
            runCatching { deleteNoFollow(target, stats) }
            if (!pathExistsNoFollow(target)) {
                return DeleteResult(ok = true, freedBytes = stats.freedBytes)
            }
            return DeleteResult(
                ok = false,
                error = "Este arquivo está em uso ou protegido pelo sistema e não pôde ser apagado."
            )
        }

        val entries = target.listFiles()
            ?: return DeleteResult(
                ok = false,
                error = "Permissão negada pelo Android para acessar o conteúdo desta pasta."
            )

        val stats = RecursiveDeleteStats()
        for (child in entries) {
            runCatching { deleteNoFollow(child, stats) }
        }

        if (stats.lockedItems == 0) {
            runCatching { target.delete() }
        }

        if (!pathExistsNoFollow(target)) {
            return DeleteResult(ok = true, freedBytes = stats.freedBytes)
        }

        if (stats.deletedFilesOrLinks > 0) {
            return DeleteResult(
                ok = true,
                partial = true,
                freedBytes = stats.freedBytes,
                message = "Limpeza concluída (${stats.deletedFilesOrLinks} subitens apagados; ${stats.lockedItems} item(ns) protegido(s) pelo sistema foram mantidos)."
            )
        }

        return DeleteResult(
            ok = false,
            error = "Os arquivos desta pasta estão em uso ou protegidos pelo Android (ex.: Android/data restrito)."
        )
    }

    fun executeSafDelete(
        context: Context,
        uriString: String,
        protectedRootUri: String? = null
    ): DeleteResult {
        if (protectedRootUri != null && uriString == protectedRootUri) {
            return DeleteResult(
                ok = false,
                error = "A pasta raiz selecionada está protegida contra exclusão direta."
            )
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return DeleteResult(ok = false, error = "URI inválida para exclusão.")

        val doc = DocumentFile.fromSingleUri(context, uri)
            ?: return DeleteResult(ok = false, error = "Item não encontrado na árvore selecionada.")

        if (!doc.exists()) {
            return DeleteResult(ok = false, error = "O item selecionado não existe mais.")
        }

        val deleted = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri) || doc.delete()
        }.getOrDefault(false)

        return if (deleted || !doc.exists()) {
            DeleteResult(ok = true)
        } else {
            DeleteResult(
                ok = false,
                error = "Não foi possível apagar este item via Seletor de Pasta (SAF)."
            )
        }
    }
}
