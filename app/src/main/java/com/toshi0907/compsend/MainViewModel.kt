package com.toshi0907.compsend

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Progress(val done: Int, val total: Int)

/** 作成済みのzipと、作成時の写真・設定。 */
data class Compressed(
    val zip: ZipResult,
    val photos: List<Uri>,
    val maxLongSide: Int,
    val quality: Int,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)

    val photos = mutableStateListOf<Uri>()

    var recipient by mutableStateOf(settings.recipient)
        private set
    var maxLongSide by mutableIntStateOf(settings.maxLongSide)
        private set
    var quality by mutableIntStateOf(settings.quality)
        private set

    /** 圧縮中の進捗。null は待機中。 */
    var progress by mutableStateOf<Progress?>(null)
        private set

    /** 直近に作成したzip。写真や設定を変えても、比較用にサイズを表示し続ける。 */
    var result by mutableStateOf<Compressed?>(null)
        private set

    /** 直近のzipをメールアプリに渡したか。渡していないzipは再圧縮時に削除する。 */
    private var resultSent = false

    /** 直近のzipが、現在の写真・設定で作成したものか。 */
    val isResultUpToDate: Boolean
        get() = result?.let {
            it.photos == photos.toList() && it.maxLongSide == maxLongSide && it.quality == quality
        } ?: false

    var message by mutableStateOf<String?>(null)
        private set

    fun addPhotos(uris: List<Uri>) {
        uris.filterNot { it in photos }.let(photos::addAll)
    }

    fun removePhoto(uri: Uri) {
        photos.remove(uri)
    }

    fun clearPhotos() {
        photos.clear()
    }

    fun updateRecipient(value: String) {
        recipient = value
        settings.recipient = value
    }

    fun updateMaxLongSide(value: Int) {
        maxLongSide = value
        settings.maxLongSide = value
    }

    fun updateQuality(value: Int) {
        quality = value
        settings.quality = value
    }

    fun compress() {
        if (photos.isEmpty() || progress != null) return
        val uris = photos.toList()
        val targetLongSide = maxLongSide
        val targetQuality = quality
        progress = Progress(0, uris.size)
        viewModelScope.launch {
            try {
                val zip = PhotoCompressor.compressToZip(
                    getApplication(),
                    uris,
                    targetLongSide,
                    targetQuality,
                ) { done ->
                    withContext(Dispatchers.Main) { progress = Progress(done, uris.size) }
                }
                if (zip == null) {
                    message = "写真を読み込めませんでした"
                } else {
                    discardUnsentResult()
                    result = Compressed(zip, uris, targetLongSide, targetQuality)
                    resultSent = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "圧縮に失敗しました" + (e.message?.let { ": $it" } ?: "")
            } finally {
                progress = null
            }
        }
    }

    fun markResultSent() {
        resultSent = true
    }

    private suspend fun discardUnsentResult() {
        val old = result ?: return
        if (!resultSent) withContext(Dispatchers.IO) { old.zip.file.delete() }
    }

    fun consumeMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }
}
