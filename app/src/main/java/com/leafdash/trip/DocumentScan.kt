package com.leafdash.trip

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** A file found by [DocumentScan]: a readable [uri] and a short path to show. */
data class FoundFile(val uri: Uri, val path: String)

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

    /**
     * Every file matching [match] in the persisted tree grants of [resolver]
     * plus the plain [dirs] (non-recursive; readable only where scoped
     * storage allows). Paths: "Download/x.csv" style, de-duplicated.
     */
    fun findAll(resolver: ContentResolver, dirs: List<java.io.File>, match: (String) -> Boolean): List<FoundFile> {
        val out = linkedMapOf<String, FoundFile>()
        for (f in dirs) {
            val files = runCatching { f.listFiles() }.getOrNull() ?: continue
            for (c in files) if (c.isFile && match(c.name)) {
                val path = c.absolutePath.substringAfter("/storage/emulated/0/").substringAfter("/sdcard/")
                out.putIfAbsent(path, FoundFile(Uri.fromFile(c), path))
            }
        }
        for (grant in resolver.persistedUriPermissions) {
            if (!grant.isReadPermission) continue
            for (u in find(resolver, grant.uri, match)) {
                // document id "primary:Download/x.csv" -> "Download/x.csv"
                val path = runCatching { DocumentsContract.getDocumentId(u) }.getOrNull()?.substringAfter(':') ?: u.toString()
                out.putIfAbsent(path, FoundFile(u, path))
            }
        }
        return out.values.toList()
    }
}
