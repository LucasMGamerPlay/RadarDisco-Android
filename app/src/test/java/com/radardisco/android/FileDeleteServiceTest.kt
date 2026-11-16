package com.radardisco.android

import com.radardisco.android.core.storage.FileDeleteService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class FileDeleteServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun blocksProtectedAndroidAndRootPaths() {
        val volumes = setOf("/storage/emulated/0", "/storage/ABCD-1234")
        assertTrue(FileDeleteService.isProtectedSystemPath("/", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/system", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/system/priv-app", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/storage/emulated/0", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/storage/emulated/0/Android", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/storage/emulated/0/Android/data", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/storage/ABCD-1234", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("/storage/ABCD-1234/Android/obb", volumes))
        assertTrue(FileDeleteService.isProtectedSystemPath("content://com.android.externalstorage.documents/tree/primary", volumes, protectedRoot = "content://com.android.externalstorage.documents/tree/primary"))

        assertFalse(FileDeleteService.isProtectedSystemPath("/storage/emulated/0/Download/test.apk", volumes))
    }

    @Test
    fun rejectsDeletingProtectedRootPathOnExecute() {
        val res = FileDeleteService.executeDirectDelete(
            rawPath = "/storage/emulated/0/Android/data",
            volumeRoots = setOf("/storage/emulated/0")
        )
        assertFalse(res.ok)
    }

    @Test
    fun deletesFolderTreePermanently() {
        val parent = tempFolder.newFolder("container")
        val target = File(parent, "cache_to_delete").apply { mkdirs() }
        File(target, "a.tmp").writeText("hello")
        val nested = File(target, "nested").apply { mkdirs() }
        File(nested, "b.tmp").writeText("world")

        val res = FileDeleteService.executeDirectDelete(
            rawPath = target.absolutePath,
            protectedRoot = parent.absolutePath
        )

        assertTrue(res.ok)
        assertFalse(res.partial)
        assertFalse(target.exists())
    }

    @Test
    fun partialCleanupLeavesLockedChildAndDeletesUnlockedSiblings() {
        val parent = tempFolder.newFolder("partial_container")
        val target = File(parent, "app_folder").apply { mkdirs() }
        val deletable = File(target, "free.log").apply { writeText("can delete") }
        val locked = File(target, "locked.db").apply { writeText("locked") }

        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        if (isWindows) {
            RandomAccessFile(locked, "rw").use {
                val res = FileDeleteService.executeDirectDelete(
                    rawPath = target.absolutePath,
                    protectedRoot = parent.absolutePath
                )
                assertTrue(res.ok)
                assertTrue(res.partial)
                assertFalse(deletable.exists())
                assertTrue(locked.exists())
            }
        } else {
            val res = FileDeleteService.executeDirectDelete(
                rawPath = target.absolutePath,
                protectedRoot = parent.absolutePath
            )
            assertTrue(res.ok)
            assertFalse(target.exists())
        }
    }

    @Test
    fun returnsErrorWhenPathDoesNotExist() {
        val parent = tempFolder.newFolder("missing_check")
        val missing = File(parent, "does_not_exist.zip")
        val res = FileDeleteService.executeDirectDelete(
            rawPath = missing.absolutePath,
            protectedRoot = parent.absolutePath
        )
        assertFalse(res.ok)
        assertEquals(0L, res.freedBytes)
    }
}
