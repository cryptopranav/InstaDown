package com.pranavkd.instadown.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaStoreHelper {

    fun saveToMediaStore(
        context: Context,
        file: File,
        fileName: String,
        mimeType: String
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStoreApi29(context, file, fileName, mimeType)
        } else {
            saveToLegacyStorage(context, file, fileName)
        }
    }

    private fun saveToMediaStoreApi29(
        context: Context,
        file: File,
        fileName: String,
        mimeType: String
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, contentValues) ?: return null

        resolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { input ->
                input.copyTo(outputStream)
            }
        }

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyStorage(
        context: Context,
        file: File,
        fileName: String
    ): Uri? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val destFile = File(downloadsDir, fileName)
        file.copyTo(destFile, overwrite = true)
        return Uri.fromFile(destFile)
    }
}
