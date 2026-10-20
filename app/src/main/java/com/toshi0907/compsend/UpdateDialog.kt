package com.toshi0907.compsend

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** 新しいビルドの案内 → ダウンロード → インストールまでを1つのダイアログで行う。 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val state = viewModel.state
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        UpdateState.None -> return
    }

    val context = LocalContext.current
    // 設定画面で許可して戻ってきたときに反映するため、復帰のたびに確認し直す
    var canInstall by remember { mutableStateOf(UpdateChecker.canInstall(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = UpdateChecker.canInstall(context)
    }
    val start = { intent: Intent ->
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("画面を開けませんでした")
        }
    }

    val versions = "Build ${BuildConfig.BUILD_NUMBER} → Build ${info.buildNumber}"
    AlertDialog(
        // ダウンロード中は誤タップで閉じないよう、明示的な「キャンセル」でのみ閉じる
        onDismissRequest = { if (state !is UpdateState.Downloading) viewModel.dismiss() },
        title = { Text("アップデート") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is UpdateState.Available -> Text("新しいバージョンがあります。\n$versions")
                    is UpdateState.Downloading -> {
                        Text("ダウンロード中…\n$versions")
                        val progress = state.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateState.Ready -> Text(
                        if (canInstall) {
                            "ダウンロードが完了しました。\n$versions"
                        } else {
                            "インストールするには、このアプリに「不明なアプリのインストール」を許可してください。" +
                                "許可したらこの画面に戻り、「インストール」を押してください。"
                        },
                    )
                    UpdateState.None -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = viewModel::download) { Text("ダウンロード") }
                is UpdateState.Ready -> if (canInstall) {
                    TextButton(onClick = { start(UpdateChecker.createInstallIntent(context, state.apk)) }) {
                        Text("インストール")
                    }
                } else {
                    TextButton(onClick = { start(UpdateChecker.createPermissionIntent(context)) }) {
                        Text("設定を開く")
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss) {
                Text(if (state is UpdateState.Downloading) "キャンセル" else "後で")
            }
        },
    )
}
