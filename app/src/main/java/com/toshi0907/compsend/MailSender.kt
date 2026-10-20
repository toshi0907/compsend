package com.toshi0907.compsend

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    /**
     * zip を添付したメール作成画面を開く Intent。選択肢はメールアプリに限定する。
     *
     * ACTION_SEND に mailto: の selector を付ける方法は、選ばれた ACTION_SENDTO 用の画面が
     * ACTION_SEND を受け付けない(Android 13以降は宣言外の Intent が届かない)ため失敗する。
     * そこで mailto: を扱えるアプリのうち、zip の ACTION_SEND を受け付ける画面を明示的に指定する。
     */
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
            clipData = ClipData.newRawUri(result.file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pm = context.packageManager
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        val mailPackages = pm
            .queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), flags)
            .map { it.activityInfo.packageName }
            .toSet()
        val targets = pm.queryIntentActivities(send, flags)
            .map { it.activityInfo }
            .filter { it.packageName in mailPackages }
            .map { Intent(send).setClassName(it.packageName, it.name) }
        return when (targets.size) {
            // メールアプリが特定できない場合も送信できるよう、共有先をすべて候補にする
            0 -> Intent.createChooser(send, "送信アプリを選択")
            1 -> targets.single()
            else -> Intent.createChooser(targets.first(), "メールアプリを選択")
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
        }
    }
}
