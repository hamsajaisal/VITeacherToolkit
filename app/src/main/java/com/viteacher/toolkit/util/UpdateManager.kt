package com.viteacher.toolkit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String
)

object UpdateManager {

    private const val GITHUB_RELEASE_URL = "https://api.github.com/repos/hamsajaisal/VITeacherToolkit/releases/latest"

    /**
     * Checks if a new release is available on GitHub.
     * Returns UpdateInfo if an update exists, otherwise null.
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_RELEASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.getString("tag_name")
                val changelog = json.optString("body", "No release notes provided.")

                // Strip leading "v" or "V" if present in the tag name
                val cleanTagName = if (tagName.startsWith("v", ignoreCase = true)) {
                    tagName.substring(1)
                } else {
                    tagName
                }

                if (isNewerVersion(cleanTagName, currentVersionName)) {
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    if (downloadUrl.isNotEmpty()) {
                        return@withContext UpdateInfo(cleanTagName, downloadUrl, changelog)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Semantically compares latest version string with current version.
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val lVal = latestParts.getOrElse(i) { 0 }
                val cVal = currentParts.getOrElse(i) { 0 }
                if (lVal > cVal) return true
                if (lVal < cVal) return false
            }
        } catch (e: Exception) {
            return latest != current
        }
        return false
    }

    /**
     * Downloads the APK file to the app's cache directory in the background.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode == 200) {
                val fileLength = connection.contentLength
                val cacheFile = File(context.cacheDir, "update.apk")
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                connection.inputStream.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                val progress = (total * 100 / fileLength).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                            output.write(data, 0, count)
                        }
                    }
                }
                return@withContext cacheFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Checks if the app has permission to install unknown applications (required for Android 8.0+).
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches the system settings panel for "Install Unknown Apps" permission.
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val uri = Uri.parse("package:${context.packageName}")
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the Android system package installer for the downloaded update.apk.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
