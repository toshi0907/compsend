package com.toshi0907.compsend

import android.content.ActivityNotFoundException
import android.net.Uri
import android.text.format.Formatter
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> viewModel.addPhotos(uris) }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            onMessage(message)
            viewModel.consumeMessage()
        }
    }
    val updateMessage = updateViewModel.message
    LaunchedEffect(updateMessage) {
        if (updateMessage != null) {
            onMessage(updateMessage)
            updateViewModel.consumeMessage()
        }
    }
    UpdateDialog(updateViewModel)

    val result = viewModel.result
    val lifecycleOwner = LocalLifecycleOwner.current
    val openMail = { zip: ZipResult ->
        viewModel.consumeResult()
        try {
            context.startActivity(MailSender.createIntent(context, zip, viewModel.recipient))
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("メールアプリが見つかりません")
        }
    }
    // サイズ超過や除外した写真がある場合は、メールアプリを開く前に確認する
    val tooLarge = result != null && result.sizeBytes > MailSender.WARN_SIZE_BYTES
    val needsConfirm = result != null && (tooLarge || result.failedCount > 0)
    LaunchedEffect(result) {
        // 圧縮中にアプリを離れた場合は、バックグラウンドからの起動がブロックされないよう復帰を待つ
        if (result != null && !needsConfirm) {
            lifecycleOwner.lifecycle.withResumed { openMail(result) }
        }
    }
    if (result != null && needsConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::consumeResult,
            title = { Text("送信前の確認") },
            text = {
                val lines = buildList {
                    if (result.failedCount > 0) {
                        add("${result.failedCount}枚の写真を読み込めなかったため除外しました(${result.photoCount}枚を送信します)。")
                    }
                    if (tooLarge) {
                        add(
                            "zipのサイズが ${Formatter.formatShortFileSize(context, result.sizeBytes)} です。" +
                                "メールの添付上限を超えて送信できない可能性があります。" +
                                "長辺や品質を下げるか、写真の枚数を減らすことを検討してください。",
                        )
                    }
                }
                Text(lines.joinToString("\n\n"))
            },
            confirmButton = { TextButton(onClick = { openMail(result) }) { Text("このまま送信") } },
            dismissButton = { TextButton(onClick = viewModel::consumeResult) { Text("やめる") } },
        )
    }

    val busy = viewModel.progress != null
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "CompSend", style = MaterialTheme.typography.headlineMedium)

        val recipient = viewModel.recipient
        val invalid = recipient.isNotBlank() && !MailSender.isValidAddress(recipient)
        OutlinedTextField(
            value = recipient,
            onValueChange = viewModel::updateRecipient,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("既定の宛先") },
            placeholder = { Text("未入力ならメールアプリで指定") },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text("メールアドレスの形式が正しくありません") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !busy,
        )

        OptionRow(
            label = "長辺",
            options = SettingsRepository.LONG_SIDE_OPTIONS,
            selected = viewModel.maxLongSide,
            optionLabel = { if (it == 0) "原寸" else "${it}px" },
            onSelect = viewModel::updateMaxLongSide,
            enabled = !busy,
        )
        OptionRow(
            label = "品質",
            options = SettingsRepository.QUALITY_OPTIONS,
            selected = viewModel.quality,
            optionLabel = { it.toString() },
            onSelect = viewModel::updateQuality,
            enabled = !busy,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "選択中: ${viewModel.photos.size}枚",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (viewModel.photos.isNotEmpty()) {
                TextButton(onClick = viewModel::clearPhotos, enabled = !busy) { Text("すべて解除") }
            }
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
            ) { Text("写真を選択") }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(viewModel.photos, key = { it.toString() }) { uri ->
                Thumbnail(uri = uri, onRemove = { viewModel.removePhoto(uri) }, removable = !busy)
            }
        }

        val progress = viewModel.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("圧縮中… ${progress.done} / ${progress.total}", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = viewModel::compress,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && viewModel.photos.isNotEmpty() && !invalid,
        ) { Text("圧縮してメールで送信") }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Build ${BuildConfig.BUILD_NUMBER} (${BuildConfig.VERSION_NAME})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { updateViewModel.check() }, enabled = !updateViewModel.checking) {
                Text(
                    text = if (updateViewModel.checking) "確認中…" else "更新を確認",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<Int>,
    selected: Int,
    optionLabel: (Int) -> String,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.width(40.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(uri: Uri, onRemove: () -> Unit, removable: Boolean) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (removable) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp),
            ) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "選択を解除", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
