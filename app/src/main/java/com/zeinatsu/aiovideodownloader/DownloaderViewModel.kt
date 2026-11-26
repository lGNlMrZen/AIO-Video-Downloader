package com.zeinatsu.aiovideodownloader

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class AppNotificationItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val isError: Boolean = false
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application.applicationContext)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    val uiState: StateFlow<DownloadUiState> = DownloadStateHolder.uiState
    val alerts: StateFlow<List<AppNotificationItem>> = DownloadStateHolder.alerts

    private val _storagePath = MutableStateFlow("/Download/VidGrab/")
    val storagePath: StateFlow<String> = _storagePath.asStateFlow()

    private val _advancedOptions = MutableStateFlow(AdvancedDownloadOptions())
    val advancedOptions: StateFlow<AdvancedDownloadOptions> = _advancedOptions.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<RealDownloadedFile>>(emptyList())
    val downloadedFiles: StateFlow<List<RealDownloadedFile>> = _downloadedFiles.asStateFlow()

    private val _engineUpdateStatus = MutableStateFlow<String?>(null)
    val engineUpdateStatus: StateFlow<String?> = _engineUpdateStatus.asStateFlow()

    init {
        refreshFilesList()
        viewModelScope.launch {
            DownloadStateHolder.filesRefreshTick.collect {
                refreshFilesList()
            }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun updateStoragePath(newPath: String) {
        val formatted = if (newPath.endsWith("/")) newPath else "$newPath/"
        _storagePath.value = formatted
        refreshFilesList()
    }

    fun updateAdvancedOptions(options: AdvancedDownloadOptions) {
        _advancedOptions.value = options
    }

    fun refreshFilesList() {
        _downloadedFiles.value = repository.getDownloadedFiles(_storagePath.value)
    }

    fun removeHistoryOnly(file: File) {
        repository.hideFromHistoryOnly(file)
        refreshFilesList()
    }

    fun deleteFileAndHistory(file: File) {
        repository.deleteFileAndHistory(file)
        refreshFilesList()
    }

    fun getEngineVersion(): String = repository.getEngineVersion()

    fun updateYtDlpEngine() {
        viewModelScope.launch {
            _engineUpdateStatus.value = "Mengunduh pembaruan yt-dlp..."
            val res = repository.updateEngine()
            res.fold(
                onSuccess = { msg ->
                    _engineUpdateStatus.value = msg
                    DownloadStateHolder.addAlert("Engine Diperbarui", msg)
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Gagal update"
                    _engineUpdateStatus.value = "Gagal: $errMsg"
                    DownloadStateHolder.addAlert("Gagal Update Engine", errMsg, isError = true)
                }
            )
        }
    }

    fun handleSharedText(rawText: String) {
        val urlRegex = Regex("(https?://\\S+)")
        val extractedUrl = urlRegex.find(rawText)?.value ?: rawText.trim()
        if (extractedUrl.isNotEmpty()) {
            _urlInput.value = extractedUrl
            analyzeUrl(extractedUrl)
        }
    }

    fun analyzeUrl(url: String = _urlInput.value) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            DownloadStateHolder.updateUiState(DownloadUiState.Error("Tempel tautan video terlebih dahulu."))
            return
        }

        viewModelScope.launch {
            DownloadStateHolder.updateUiState(DownloadUiState.FetchingInfo)
            val result = repository.fetchVideoInfo(cleanUrl)
            result.fold(
                onSuccess = { details ->
                    DownloadStateHolder.updateUiState(DownloadUiState.InfoReady(details))
                },
                onFailure = { error ->
                    val msg = error.message ?: "Gagal menganalisis tautan"
                    DownloadStateHolder.updateUiState(DownloadUiState.Error(msg))
                    DownloadStateHolder.addAlert("Gagal Ekstrak Link", msg, isError = true)
                }
            )
        }
    }

    fun startDownload(details: VideoDetails, format: VideoFormatOption, initialProgress: Float = 0f) {
        if (_advancedOptions.value.wifiOnly && !isConnectedToWifi()) {
            DownloadStateHolder.updateUiState(
                DownloadUiState.Error("Mode 'Hanya Wi-Fi' aktif. Hubungkan ke Wi-Fi atau matikan pengaturan di tab Account.")
            )
            return
        }

        DownloadStateHolder.activeSession = ActiveDownloadSession(
            details = details,
            selectedFormat = format,
            storagePath = _storagePath.value,
            advancedOptions = _advancedOptions.value
        )

        DownloadForegroundService.startDownload(getApplication(), initialProgress)
    }

    fun pauseCurrentDownload() {
        DownloadForegroundService.sendAction(getApplication(), DownloadForegroundService.ACTION_PAUSE)
    }

    fun resumeCurrentDownload() {
        DownloadForegroundService.sendAction(getApplication(), DownloadForegroundService.ACTION_RESUME)
    }

    fun cancelCurrentDownload() {
        DownloadForegroundService.sendAction(getApplication(), DownloadForegroundService.ACTION_CANCEL)
    }

    fun resetState() {
        DownloadStateHolder.updateUiState(DownloadUiState.Idle)
    }

    private fun isConnectedToWifi(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
