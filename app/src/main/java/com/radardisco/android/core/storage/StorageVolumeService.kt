package com.radardisco.android.core.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.radardisco.android.model.QuickScanTarget
import com.radardisco.android.model.StorageVolumeInfo
import java.io.File
import kotlin.math.max

object StorageVolumeService {

    fun hasAllFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun listStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val seenRoots = HashSet<String>()

        val storageManager = context.getSystemService(StorageManager::class.java)
        if (storageManager != null) {
            for (vol in storageManager.storageVolumes) {
                val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else {
                    if (vol.isPrimary) Environment.getExternalStorageDirectory() else null
                }
                if (dir != null && dir.exists()) {
                    val path = dir.absolutePath.trimEnd('/')
                    if (seenRoots.add(path)) {
                        val label = when {
                            vol.isPrimary -> "Armazenamento Interno"
                            else -> vol.getDescription(context).takeIf { it.isNotBlank() } ?: "Cartão SD"
                        }
                        buildVolumeInfo(
                            id = vol.uuid ?: if (vol.isPrimary) "primary" else path,
                            mountPath = path,
                            label = label,
                            isRemovable = vol.isRemovable
                        )?.let { volumes.add(it) }
                    }
                }
            }
        }

        // Fallback via getExternalFilesDirs for removable SD cards on API 26-29
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val externalDirs = context.getExternalFilesDirs(null)
            for (extDir in externalDirs) {
                if (extDir == null) continue
                val marker = "/Android/data/"
                val idx = extDir.absolutePath.indexOf(marker)
                if (idx <= 0) continue
                val rootPath = extDir.absolutePath.substring(0, idx)
                if (seenRoots.add(rootPath) && File(rootPath).exists()) {
                    val isPrimary = rootPath == Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
                    buildVolumeInfo(
                        id = if (isPrimary) "primary" else rootPath,
                        mountPath = rootPath,
                        label = if (isPrimary) "Armazenamento Interno" else "Armazenamento Externo",
                        isRemovable = !isPrimary
                    )?.let { volumes.add(it) }
                }
            }
        }

        if (volumes.isEmpty()) {
            val primary = Environment.getExternalStorageDirectory()
            if (primary.exists()) {
                buildVolumeInfo(
                    id = "primary",
                    mountPath = primary.absolutePath.trimEnd('/'),
                    label = "Armazenamento Interno",
                    isRemovable = false
                )?.let { volumes.add(it) }
            }
        }

        return volumes.sortedWith(compareBy<StorageVolumeInfo> { it.isRemovable }.thenBy { it.label })
    }

    private fun buildVolumeInfo(
        id: String,
        mountPath: String,
        label: String,
        isRemovable: Boolean
    ): StorageVolumeInfo? {
        return runCatching {
            val stat = StatFs(mountPath)
            val totalBytes = stat.totalBytes
            if (totalBytes <= 0L) return null
            val freeBytes = stat.availableBytes
            val usedBytes = max(0L, totalBytes - freeBytes)
            val usedPercent = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
                .toFloat()
                .coerceIn(0f, 100f)
            StorageVolumeInfo(
                id = id,
                mountPath = mountPath,
                label = label,
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                usedBytes = usedBytes,
                usedPercent = usedPercent,
                isRemovable = isRemovable
            )
        }.getOrNull()
    }

    fun listQuickTargets(primaryRootPath: String?): List<QuickScanTarget> {
        if (primaryRootPath.isNullOrBlank()) return emptyList()
        val root = primaryRootPath.trimEnd('/')
        val candidates = listOf(
            Triple("Downloads", "$root/Download", "APKs, arquivos e instaladores"),
            Triple("DCIM / Câmera", "$root/DCIM", "Fotos, vídeos e .thumbnails"),
            Triple("WhatsApp", "$root/Android/media/com.whatsapp/WhatsApp", "Mídias e backups (Android 11+)"),
            Triple("WhatsApp (Legado)", "$root/WhatsApp", "Mídias antigas do WhatsApp"),
            Triple("Telegram", "$root/Telegram", "Vídeos, docs e caches"),
            Triple("Filmes / Gravações", "$root/Movies", "Vídeos e gravações de tela"),
            Triple("Documentos", "$root/Documents", "PDFs, planilhas e backups")
        )
        return candidates.mapNotNull { (name, path, subtitle) ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                QuickScanTarget(name = name, path = path, subtitle = subtitle)
            } else {
                null
            }
        }
    }
}
