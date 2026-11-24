package com.leafdash.trip

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Recursive file search in a folder granted through the system tree picker. */
object DocumentScan {
    private const val MAX_DIRS = 2000   // bail out on huge trees

    /** Document URIs under [tree] (and its subfolders) whose name passes [match]. */
    fun find(resolver: ContentResolver, tree: Uri, match: (String) -> Boolean): List<Uri> {
        val out = mutableListOf<Uri>()
        val dirs = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        var visited = 0
        while (dirs.isNotEmpty() && visited++ < MAX_DIRS) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirs.removeFirst())
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            runCatching { resolver.query(children, cols, null, null, null) }.getOrNull()?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) dirs += id
                    else if (match(name)) out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
        }
        return out
    }
}
