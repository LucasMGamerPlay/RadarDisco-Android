package com.radardisco.android.core.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.radardisco.android.model.ScanSourceType

/**
 * Implementação rápida de ScanFileSystem sobre Storage Access Framework (OpenDocumentTree)
 * usando ContentResolver.query diretamente em DocumentsContract para evitar overhead do DocumentFile.
 */
class SafScanFileSystem(
    context: Context,
    private val treeUri: Uri
) : ScanFileSystem {

    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)
    private val parentByChildUri = HashMap<String, String>()

    override val sourceType: ScanSourceType = ScanSourceType.SAF_TREE

    override fun resolveRootDisplayName(rootPath: String): String {
        val doc = DocumentFile.fromTreeUri(appContext, Uri.parse(rootPath))
        return doc?.name?.takeIf { it.isNotBlank() }
            ?: rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Pasta Selecionada" }
    }

    override fun listChildren(dirPath: String): List<ScanFileEntry> {
        val dirUri = Uri.parse(dirPath)
        val dirDocId = runCatching {
            DocumentsContract.getDocumentId(dirUri)
        }.getOrElse {
            rootDocId
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val entries = mutableListOf<ScanFileEntry>()
        val cursor = contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw SecurityException("Não foi possível acessar a pasta selecionada.")

        cursor.use { c ->
            val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (c.moveToNext()) {
                val docId = if (idCol >= 0) c.getString(idCol) else continue
                val name = if (nameCol >= 0) c.getString(nameCol) ?: "item" else "item"
                val mime = if (mimeCol >= 0) c.getString(mimeCol) ?: "" else ""
                val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()

                synchronized(parentByChildUri) {
                    parentByChildUri[childUri] = dirPath
                }

                entries.add(
                    ScanFileEntry(
                        name = name,
                        path = childUri,
                        isDirectory = isDir,
                        isSymbolicLink = false,
                        sizeBytes = if (isDir) 0L else size.coerceAtLeast(0L)
                    )
                )
            }
        }

        return entries
    }

    override fun parentPathOf(path: String): String? =
        synchronized(parentByChildUri) { parentByChildUri[path] }

    companion object {
        fun treeRootDocumentUriString(treeUri: Uri): String {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
        }
    }
}
