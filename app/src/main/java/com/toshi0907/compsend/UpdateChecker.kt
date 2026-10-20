package com.toshi0907.compsend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val buildNumber: Int,
    val apkUrl: String,
    /** Release に登録された APK のサイズ。不明な場合は -1。 */
    val apkSize: Long,
)

/**
 * GitHub Release `latest`(main への push 時に CI が更新)を参照してアプリを更新する。
 * 本文の `build_number: N` を [BuildConfig.BUILD_NUMBER] と比較する。
 */
object UpdateChecker {
    private const val RELEASE_URL = "https://api.github.com/repos/toshi0907/compsend/releases/tags/latest"
    private const val APK_NAME = "compsend.apk"
    private const val UPDATE_DIR = "update"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private val BUILD_NUMBER_REGEX = Regex("""^build_number:\s*(\d+)""", RegexOption.MULTILINE)

    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = open(RELEASE_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            // optString は JSON の null を文字列 "null" にするため、明示的に空扱いにする
            val body = if (json.isNull("body")) "" else json.getString("body")
            val build = BUILD_NUMBER_REGEX.find(body)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IOException("ビルド番号が見つかりません")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map(assets::getJSONObject)
                .firstOrNull { it.optString("name") == APK_NAME }
                ?: throw IOException("APKが見つかりません")
            UpdateInfo(build, apk.getString("browser_download_url"), apk.optLong("size", -1))
        } finally {
            conn.disconnect()
        }
    }

    /** APK を cacheDir/update/ にダウンロードする。進捗はサイズ不明なら null。 */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: suspend (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = clearDownloads(context).apply { mkdirs() }
        // キャンセル済みの古いダウンロードが後から自分の一時ファイルを消しても影響しないよう、毎回別名にする
        val tmp = File.createTempFile(APK_NAME, ".tmp", dir)
        val apk = File(dir, APK_NAME)
        // browser_download_url は別ホスト(https)へリダイレクトされるが、同一プロトコルなので自動で追従する
        val conn = open(info.apkUrl)
        // ブロック中の read はコルーチンのキャンセルでは中断されないため、接続を切って抜けさせる
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                conn.disconnect()
            }
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize.takeIf { it > 0 }
            var done = 0L
            var lastPercent = Int.MIN_VALUE
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        // サイズ不明(-1)の場合は最初の1回だけ通知する
                        val percent = total?.let { (done * 100 / it).toInt() } ?: -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent.takeIf { it >= 0 }?.let { it / 100f })
                        }
                    }
                }
            }
            if (total != null && done != total) throw IOException("ダウンロードが途中で終了しました")
            if (!tmp.renameTo(apk)) throw IOException("APKを保存できませんでした")
            apk
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        } finally {
            watcher.cancel()
            conn.disconnect()
        }
    }

    /** ダウンロード済みの APK を消す(更新後に残った古い APK の掃除も兼ねる)。 */
    fun clearDownloads(context: Context): File =
        File(context.cacheDir, UPDATE_DIR).apply { listFiles()?.forEach { it.delete() } }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 「不明なアプリのインストール」をこのアプリに許可する設定画面。 */
    fun createPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun createInstallIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "CompSend/${BuildConfig.BUILD_NUMBER}")
        }
}
