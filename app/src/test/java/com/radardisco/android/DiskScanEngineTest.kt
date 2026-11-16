package com.radardisco.android

import com.radardisco.android.core.scanner.DiskScanEngine
import com.radardisco.android.core.scanner.JavaIoScanFileSystem
import com.radardisco.android.core.scanner.ScanFileEntry
import com.radardisco.android.core.scanner.ScanFileSystem
import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanSourceType
import com.radardisco.android.model.ScanStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiskScanEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun scansNestedFoldersCalculatesSizesAndCategories() = runBlocking {
        val root = tempFolder.newFolder("scan_root")
        val mediaDir = File(root, "DCIM").apply { mkdirs() }
        val nodeDir = File(root, "project/node_modules").apply { mkdirs() }
        val cacheDir = File(root, "Cache").apply { mkdirs() }

        File(mediaDir, "video.mp4").writeBytes(ByteArray(4096))
        File(nodeDir, "bundle.txt").writeBytes(ByteArray(2048))
        // >10MB file para acionar spaceHogs em pasta Cache
        File(cacheDir, "big_cache.dat").writeBytes(ByteArray(11 * 1024 * 1024))

        val engine = DiskScanEngine(
            rawRootPath = root.absolutePath,
            fileSystem = JavaIoScanFileSystem(),
            concurrency = 4
        )
        engine.run()

        val snap = engine.getSnapshot()
        assertEquals(ScanStatus.COMPLETED, snap.status)
        assertEquals(11L * 1024L * 1024L + 4096L + 2048L, snap.stats.totalBytes)
        assertEquals(3, snap.stats.scannedFiles)
        assertTrue(snap.items.size >= 3)

        // Cache > 10MB deve aparecer em spaceHogs
        assertTrue(snap.spaceHogs.any { it.name == "Cache" && it.size >= 11L * 1024L * 1024L })
        // Pasta project herdando node_modules deve conter categoria DEV_DEPS
        assertEquals(2048L, snap.categoryTotals[FileCategory.DEV_DEPS])
        assertEquals(4096L, snap.categoryTotals[FileCategory.MEDIA])
    }

    @Test
    fun focusNavigationBreadcrumbsAndRemovalUpdateTree() = runBlocking {
        val root = tempFolder.newFolder("nav_root")
        val sub = File(root, "Downloads/Archives").apply { mkdirs() }
        val zipFile = File(sub, "backup.zip").apply { writeBytes(ByteArray(8192)) }
        File(root, "readme.txt").writeBytes(ByteArray(1024))

        val engine = DiskScanEngine(
            rawRootPath = root.absolutePath,
            fileSystem = JavaIoScanFileSystem()
        )
        engine.run()

        engine.setFocusPath(sub.absolutePath)
        val focused = engine.getSnapshot()
        assertEquals(3, focused.breadcrumbs.size)
        assertEquals("Archives", focused.breadcrumbs.last().name)
        assertEquals(8192L, focused.focusSize)
        assertEquals(1, focused.items.size)
        assertEquals("backup.zip", focused.items.first().name)
        assertEquals(ItemType.FILE, focused.items.first().type)

        // Remover o nó da pasta Archives deve recalcular a raiz e recuar foco se necessário
        val removed = engine.removeNodeByPath(zipFile.absolutePath)
        assertEquals(8192L, removed)

        engine.setFocusPath(root.absolutePath)
        val afterDelete = engine.getSnapshot()
        assertEquals(1024L, afterDelete.stats.totalBytes)
        assertEquals(1, afterDelete.stats.scannedFiles)
    }

    @Test
    fun avoidsCanonicalLoopAndSupportsPauseResume() = runBlocking {
        val fakeFs = object : ScanFileSystem {
            override val sourceType: ScanSourceType = ScanSourceType.FILE_SYSTEM

            override fun resolveRootDisplayName(rootPath: String): String = "Root"

            override fun listChildren(dirPath: String): List<ScanFileEntry> {
                Thread.sleep(25)
                return when (dirPath) {
                    "/fake" -> listOf(
                        ScanFileEntry("a", "/fake/a", isDirectory = true, isSymbolicLink = false, sizeBytes = 0L),
                        ScanFileEntry("root_alias", "/fake/alias", isDirectory = true, isSymbolicLink = false, sizeBytes = 0L)
                    )
                    "/fake/a" -> listOf(
                        ScanFileEntry("doc.pdf", "/fake/a/doc.pdf", isDirectory = false, isSymbolicLink = false, sizeBytes = 1000L)
                    )
                    "/fake/alias" -> listOf(
                        ScanFileEntry("should_skip.bin", "/fake/alias/should_skip.bin", isDirectory = false, isSymbolicLink = false, sizeBytes = 9999L)
                    )
                    else -> emptyList()
                }
            }

            override fun parentPathOf(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }

            override fun canonicalKey(path: String): String? =
                if (path == "/fake/alias") "/fake" else path
        }

        val engine = DiskScanEngine(
            rawRootPath = "/fake",
            fileSystem = fakeFs,
            concurrency = 2
        )

        val job = launch { engine.run() }
        delay(10)
        engine.pause()
        assertEquals(ScanStatus.PAUSED, engine.getSnapshot().status)
        delay(20)
        engine.resume()
        job.join()

        val snap = engine.getSnapshot()
        assertEquals(ScanStatus.COMPLETED, snap.status)
        assertEquals(1000L, snap.stats.totalBytes)
        assertFalse(snap.topFiles.any { it.name == "should_skip.bin" })
    }
}
