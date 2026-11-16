package com.radardisco.android.core.storage

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.radardisco.android.model.AppStorageEntry
import java.util.UUID

object AppStorageStatsService {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun queryInstalledAppStorage(context: Context): List<AppStorageEntry> {
        if (!hasUsageStatsPermission(context)) return emptyList()

        val storageStatsManager = context.getSystemService(StorageStatsManager::class.java) ?: return emptyList()
        val pm = context.packageManager
        val userHandle = Process.myUserHandle()
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val results = mutableListOf<AppStorageEntry>()
        for (appInfo in apps) {
            val storageUuid: UUID = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
            val stats = runCatching {
                storageStatsManager.queryStatsForPackage(storageUuid, appInfo.packageName, userHandle)
            }.getOrNull() ?: continue

            val total = stats.appBytes + stats.dataBytes
            if (total <= 0L) continue
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(appInfo.packageName)

            results.add(
                AppStorageEntry(
                    packageName = appInfo.packageName,
                    appName = label,
                    appBytes = stats.appBytes,
                    dataBytes = (stats.dataBytes - stats.cacheBytes).coerceAtLeast(0L),
                    cacheBytes = stats.cacheBytes,
                    totalBytes = total
                )
            )
        }

        return results.sortedByDescending { it.totalBytes }
    }
}
