package com.soaringscoring.taskloader.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * On Android 11+ apps can't reach `Android/media/...` with plain filesystem
 * APIs, but that path (unlike `Android/data`) is NOT blocked from the SAF
 * folder picker. So: the user grants access to `Android/media` once via
 * ACTION_OPEN_DOCUMENT_TREE, we persist that permission, and from then on
 * we can list/write into it like any other SAF tree.
 */
object XcsoarFolderStore {

    /**
     * Looks at the direct children of the granted `Android/media` tree and
     * returns the ones that look like an XCSoar variant (name contains
     * "soar", same heuristic xcomps uses so it picks up XCSoar, XCSoar Jet,
     * and any future forks without hardcoding package names).
     */
    fun findXcsoarFolders(context: Context, mediaTreeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, mediaTreeUri) ?: return emptyList()
        if (!root.isDirectory) return emptyList()

        val matches = LinkedHashMap<Uri, DocumentFile>()

        // The user may have picked the XCSoar-variant folder itself (e.g.
        // org.xcsoar) rather than its parent Android/media folder — count
        // that as a match too, instead of only ever looking one level down.
        if (root.name?.contains("soar", ignoreCase = true) == true) {
            matches[root.uri] = root
        }

        root.listFiles().forEach { child ->
            if (child.isDirectory && child.name?.contains("soar", ignoreCase = true) == true) {
                matches[child.uri] = child
            }
        }

        return matches.values.toList()
    }

    /**
     * Writes [bytes] into `<xcsoarFolder>/Tasks/[filename]`, creating the
     * Tasks subfolder and/or the file if needed, overwriting if it already
     * exists. Returns true on success.
     */
    fun writeTaskFile(
        context: Context,
        xcsoarFolder: DocumentFile,
        filename: String,
        bytes: ByteArray
    ): Boolean {
        val tasksDir = xcsoarFolder.findFile("Tasks")?.takeIf { it.isDirectory }
            ?: xcsoarFolder.createDirectory("Tasks")
            ?: return false

        val existing = tasksDir.findFile(filename)
        val target = existing ?: tasksDir.createFile("application/octet-stream", filename)
        ?: return false

        return try {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(bytes)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
