package com.myblendvisualizer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.myblendvisualizer.model.BlendFile
import java.util.Locale

fun resolveBlendFile(contentResolver: ContentResolver, uri: Uri): BlendFile? {
    var displayName: String? = null
    var fileSizeBytes: Long? = null

    contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameColumn >= 0) displayName = cursor.getString(nameColumn)
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                fileSizeBytes = cursor.getLong(sizeColumn)
            }
        }
    }

    val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo.blend"
    val resolvedName = (displayName ?: fallbackName).trim()
    val isBlendFile = resolvedName.lowercase(Locale.ROOT).endsWith(".blend")

    if (!isBlendFile) return null

    return BlendFile(
        uri = uri,
        displayName = resolvedName,
        sizeBytes = fileSizeBytes
    )
}
