package com.toshi0907.compsend

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MailSender {
    /** これを超えるとメールサービスの添付上限(Gmailは25MB、エンコードで約1.37倍に膨らむ)に掛かりやすい。 */
    const val WARN_SIZE_BYTES = 18L * 1024 * 1024

    fun isValidAddress(address: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(address.trim()).matches()

    /** zip を添付したメール作成画面を開く Intent。選択肢はメールアプリに限定する。 */
    fun createIntent(context: Context, result: ZipResult, recipient: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.file,
        )
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "写真 $date (${result.photoCount}枚)")
            if (isValidAddress(recipient)) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.trim()))
            }
            // ACTION_SEND に mailto: の selector を付けるとメールアプリだけが候補になる
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            clipData = ClipData.newRawUri(result.file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return send
    }
}
