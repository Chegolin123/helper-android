package com.helper.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивает APK из [url] в cache и запускает системный установщик.
 * Использует SessionInstaller-интент (API 21+) — стандартный путь Android.
 */
class ApkInstaller(private val context: Context) {

    sealed interface InstallResult {
        data object Started : InstallResult
        data class Error(val message: String) : InstallResult
    }

    /** Скачивает и запускает установку. Возвращает статус запуска установщика. */
    suspend fun downloadAndInstall(url: String): InstallResult = withContext(Dispatchers.IO) {
        try {
            val apkFile = download(url)
            launchInstaller(apkFile)
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.Error("Не удалось скачать: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun download(url: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "helper.apk")
        if (out.exists()) out.delete()

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                instanceFollowRedirects = true // GitHub отдаёт через redirect на S3
            }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn?.disconnect()
        }
        return out
    }

    private fun launchInstaller(apk: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apk)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Открываем новую задачу, т.к. запускаем из приложения.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        /** PendingIntent для проверки post-install (необязательно, зарезервировано). */
        @Suppress("unused")
        fun installIntent(context: Context, uri: Uri): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
