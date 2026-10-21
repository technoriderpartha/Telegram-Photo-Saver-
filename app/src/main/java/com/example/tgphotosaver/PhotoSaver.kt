package com.example.tgphotosaver

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Copies photos into the gallery under Pictures/TelegramPhotos/<channel>/ (no storage permission needed). */
object PhotoSaver {

    fun safeFolderName(title: String): String {
        val cleaned = title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().take(60)
        return cleaned.ifBlank { "channel" }
    }

    /** Returns true if saved, false if a photo with this name already exists. Throws on failure. */
    suspend fun save(context: Context, source: File, channelTitle: String, messageId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val fileName = "$messageId.jpg"
            val relativePath =
                "${Environment.DIRECTORY_PICTURES}/TelegramPhotos/${safeFolderName(channelTitle)}/"

            val alreadySaved = resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(fileName, relativePath),
                null
            )?.use { it.count > 0 } ?: false
            if (alreadySaved) return@withContext false

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Could not create file")

            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write file")
                output.use { out -> source.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            true
        }
}
