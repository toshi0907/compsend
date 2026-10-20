package com.toshi0907.compsend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ZipResult(
    val file: File,
    val photoCount: Int,
    val failedCount: Int,
    val sizeBytes: Long,
)

object PhotoCompressor {
    private const val SHARE_DIR = "share"

    /** メールアプリが下書きから後で読み込むことがあるため、直前のzipはすぐには消さない。 */
    private const val KEEP_MILLIS = 24L * 60 * 60 * 1000

    /**
     * 写真をリサイズ・JPEG再エンコードして1つのzipにまとめる。
     * 再エンコードにより位置情報などのEXIFは含まれない(回転はデコード時に反映済み)。
     * 1枚も変換できなかった場合は null を返す。
     */
    suspend fun compressToZip(
        context: Context,
        uris: List<Uri>,
        maxLongSide: Int,
        quality: Int,
        onProgress: suspend (done: Int) -> Unit,
    ): ZipResult? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > KEEP_MILLIS }?.forEach { it.delete() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(dir, "photos_$timestamp.zip")

        var success = 0
        var failed = 0
        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                // JPEGは圧縮済みで縮まないため、再圧縮のCPUコストを掛けない
                zip.setLevel(Deflater.NO_COMPRESSION)
                uris.forEachIndexed { index, uri ->
                    ensureActive()
                    // エントリ途中で失敗してzipが壊れないよう、先にメモリ上でエンコードする
                    val jpeg = runCatching { encodeJpeg(context, uri, maxLongSide, quality) }.getOrNull()
                    if (jpeg != null) {
                        success++
                        zip.putNextEntry(ZipEntry("photo_%03d.jpg".format(Locale.US, success)))
                        zip.write(jpeg)
                        zip.closeEntry()
                    } else {
                        failed++
                    }
                    onProgress(index + 1)
                }
            }
        } catch (e: Throwable) {
            zipFile.delete()
            throw e
        }

        if (success == 0) {
            zipFile.delete()
            null
        } else {
            ZipResult(zipFile, success, failed, zipFile.length())
        }
    }

    private fun encodeJpeg(context: Context, uri: Uri, maxLongSide: Int, quality: Int): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longSide = max(width, height)
            if (maxLongSide in 1 until longSide) {
                val scale = maxLongSide.toDouble() / longSide
                decoder.setTargetSize(
                    max(1, (width * scale).roundToInt()),
                    max(1, (height * scale).roundToInt()),
                )
            }
        }
        val bitmap = if (decoded.hasAlpha()) flattenOnWhite(decoded) else decoded
        try {
            return ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
            if (bitmap !== decoded) decoded.recycle()
        }
    }

    /** JPEGは透過を持てないため、透過部分を白で塗りつぶす(そのままだと黒になる)。 */
    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        return out
    }
}
