package com.radardisco.android

import com.radardisco.android.core.scanner.FolderClassifier
import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderClassifierAndSearchTest {

    @Test
    fun classifiesAndroidAndAiExtensionsProperly() {
        assertEquals(FileCategory.AI_MODELS, FolderClassifier.classifyFile("llama-3.gguf"))
        assertEquals(FileCategory.GAMES_APPS, FolderClassifier.classifyFile("main.123.com.game.obb"))
        assertEquals(FileCategory.GAMES_APPS, FolderClassifier.classifyFile("radardisco.apk"))
        assertEquals(FileCategory.MEDIA, FolderClassifier.classifyFile("VID_2026.mp4"))
        assertEquals(FileCategory.ARCHIVES, FolderClassifier.classifyFile("backup.tar.gz"))
        assertEquals(FileCategory.DOCUMENTS, FolderClassifier.classifyFile("msgstore.db.crypt15"))
        assertEquals(FileCategory.DEV_DEPS, FolderClassifier.classifyFile("MainActivity.kt"))
    }

    @Test
    fun detectsAndroidSpecialFolderKinds() {
        val whatsapp = FolderClassifier.detectFolderKind("WhatsApp Video", "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video")
        assertEquals("WhatsApp", whatsapp.badge)

        val obb = FolderClassifier.detectFolderKind("obb", "/storage/emulated/0/Android/obb")
        assertEquals("Jogos / OBB", obb.badge)

        val cache = FolderClassifier.detectFolderKind(".thumbnails", "/storage/emulated/0/DCIM/.thumbnails")
        assertEquals("Cache", cache.badge)
    }

    @Test
    fun smartWebSearchBuildsCleanGoogleQueryWithAndroidContext() {
        val url = FolderClassifier.buildSmartWebSearchUrl(
            name = "WhatsApp Video",
            targetPath = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            itemType = ItemType.FOLDER
        )
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("WhatsApp"))
        assertTrue(url.contains("Android"))
    }
}
