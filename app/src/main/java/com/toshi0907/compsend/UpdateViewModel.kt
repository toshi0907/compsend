package com.toshi0907.compsend

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf<UpdateState>(UpdateState.None)
        private set

    var checking by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { UpdateChecker.clearDownloads(application) }
            check(manual = false)
        }
    }

    /** 起動時の自動確認(manual = false)では、最新版だった場合や失敗した場合に何も表示しない。 */
    fun check(manual: Boolean = true) {
        if (checking || state != UpdateState.None) return
        checking = true
        viewModelScope.launch {
            try {
                val info = UpdateChecker.fetchLatest()
                if (info.buildNumber > BuildConfig.BUILD_NUMBER) {
                    state = UpdateState.Available(info)
                } else if (manual) {
                    message = "最新版です(Build ${BuildConfig.BUILD_NUMBER})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) message = "更新の確認に失敗しました" + (e.message?.let { ": $it" } ?: "")
            } finally {
                checking = false
            }
        }
    }

    fun download() {
        val info = (state as? UpdateState.Available)?.info ?: return
        state = UpdateState.Downloading(info, null)
        downloadJob = viewModelScope.launch {
            try {
                val apk = UpdateChecker.download(getApplication(), info) { progress ->
                    withContext(Dispatchers.Main) { state = UpdateState.Downloading(info, progress) }
                }
                state = UpdateState.Ready(info, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = UpdateState.Available(info)
                message = "ダウンロードに失敗しました" + (e.message?.let { ": $it" } ?: "")
            }
        }
    }

    fun dismiss() {
        downloadJob?.cancel()
        downloadJob = null
        state = UpdateState.None
    }

    fun consumeMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }
}
