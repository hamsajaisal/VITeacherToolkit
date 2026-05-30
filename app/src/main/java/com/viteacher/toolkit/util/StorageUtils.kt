package com.viteacher.toolkit.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object StorageUtils {

    /**
     * Saves raw byte data as a file inside the public Downloads/VITeacherToolkit directory.
     * Uses MediaStore for Android 10 (Q) and above, and standard file operations for older versions.
     */
    fun saveFileToPublicDownloads(context: Context, fileName: String, mimeType: String, data: ByteArray): File? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VITeacherToolkit")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(data)
                    }
                    return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VITeacherToolkit/$fileName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val toolkitDir = File(downloadsDir, "VITeacherToolkit")
                if (!toolkitDir.exists()) {
                    toolkitDir.mkdirs()
                }
                val targetFile = File(toolkitDir, fileName)
                FileOutputStream(targetFile).use { outputStream ->
                    outputStream.write(data)
                }
                return targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Saves an input stream (like database backup) inside the public Downloads/VITeacherToolkit directory.
     * Uses MediaStore for Android 10 (Q) and above, and standard file operations for older versions.
     */
    fun saveInputStreamToPublicDownloads(context: Context, fileName: String, mimeType: String, inputStream: InputStream): File? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VITeacherToolkit")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        inputStream.use { input ->
                            input.copyTo(outputStream)
                        }
                    }
                    return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VITeacherToolkit/$fileName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val toolkitDir = File(downloadsDir, "VITeacherToolkit")
                if (!toolkitDir.exists()) {
                    toolkitDir.mkdirs()
                }
                val targetFile = File(toolkitDir, fileName)
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.use { input ->
                        input.copyTo(outputStream)
                    }
                }
                return targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
