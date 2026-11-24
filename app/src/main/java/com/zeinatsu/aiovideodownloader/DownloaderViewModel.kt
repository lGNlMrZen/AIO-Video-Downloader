package com.zeinatsu.aiovideodownloader

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Job
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
    private var downloadJob: Job? = null
    private var isUserPaused = false
    private var isUserCancelled = false

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val _storagePath = MutableStateFlow("/Download/VidGrab/")
    val storagePath: StateFlow<String> = _storagePath.asStateFlow()

    private val _advancedOptions = MutableStateFlow(AdvancedDownloadOptions())
    val advancedOptions: StateFlow<AdvancedDownloadOptions> = _advancedOptions.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<RealDownloadedFile>>(emptyList())
    val downloadedFiles: StateFlow<List<RealDownloadedFile>> = _downloadedFiles.asStateFlow()

    private val _alerts = MutableStateFlow<List<AppNotificationItem>>(emptyList())
    val alerts: StateFlow<List<AppNotificationItem>> = _alerts.asStateFlow()

    private val _engineUpdateStatus = MutableStateFlow<String?>(null)
    val engineUpdateStatus: StateFlow<String?> = _engineUpdateStatus.asStateFlow()

    init {
        refreshFilesList()
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

    fun deleteFile(file: File) {
        if (file.exists()) {
            file.delete()
            refreshFilesList()
        }
    }

    fun getEngineVersion(): String = repository.getEngineVersion()

    fun updateYtDlpEngine() {
        viewModelScope.launch {
            _engineUpdateStatus.value = "Mengunduh pembaruan yt-dlp..."
            val res = repository.updateEngine()
            res.fold(
                onSuccess = { msg ->
                    _engineUpdateStatus.value = msg
                    addAlert("Engine Diperbarui", msg)
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Gagal update"
                    _engineUpdateStatus.value = "Gagal: $errMsg"
                    addAlert("Gagal Update Engine", errMsg, isError = true)
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
            _uiState.value = DownloadUiState.Error("Tempel tautan video terlebih dahulu.")
            return
        }

        viewModelScope.launch {
            _uiState.value = DownloadUiState.FetchingInfo
            val result = repository.fetchVideoInfo(cleanUrl)
            result.fold(
                onSuccess = { details ->
                    _uiState.value = DownloadUiState.InfoReady(details)
                },
                onFailure = { error ->
                    val msg = error.message ?: "Gagal menganalisis tautan"
                    _uiState.value = DownloadUiState.Error(msg)
                    addAlert("Gagal Ekstrak Link", msg, isError = true)
                }
            )
        }
    }

    fun startDownload(details: VideoDetails, format: VideoFormatOption, initialProgress: Float = 0f) {
        if (_advancedOptions.value.wifiOnly && !isConnectedToWifi()) {
            _uiState.value = DownloadUiState.Error("Mode 'Hanya Wi-Fi' aktif. Hubungkan ke Wi-Fi atau matikan pengaturan di tab Account.")
            return
        }

        isUserPaused = false
        isUserCancelled = false
        downloadJob?.cancel()

        downloadJob = viewModelScope.launch {
            _uiState.value = DownloadUiState.Downloading(
                details = details,
                selectedFormat = format,
                progress = initialProgress,
                etaSeconds = 0L,
                statusLine = if (initialProgress > 0f) "Melanjutkan unduhan..." else "Memulai unduhan...",
                isPaused = false
            )

            var lastUiUpdateTime = 0L

            val result = repository.downloadVideo(
                details = details,
                selectedFormat = format,
                storageSubPath = _storagePath.value,
                advancedOptions = _advancedOptions.value
            ) { progress, eta, line ->
                if (!isUserPaused && !isUserCancelled) {
                    val now = System.currentTimeMillis()
                    // Batasi refresh UI maksimal ~3x per detik (350ms) agar hemat baterai & bebas lag
                    if (now - lastUiUpdateTime >= 350L || progress >= 99f) {
                        lastUiUpdateTime = now
                        val safeProgress = if (progress > 0f) progress else initialProgress
                        _uiState.value = DownloadUiState.Downloading(
                            details = details,
                            selectedFormat = format,
                            progress = safeProgress.coerceIn(0f, 100f),
                            etaSeconds = eta,
                            statusLine = line,
                            isPaused = false
                        )
                    }
                }
            }

            if (isUserPaused || isUserCancelled) return@launch

            result.fold(
                onSuccess = { savedPath ->
                    _uiState.value = DownloadUiState.Success(savedPath)
                    refreshFilesList()
                    addAlert("Unduhan Selesai", "${details.title} (${format.label}) berhasil disimpan.")
                },
                onFailure = { error ->
                    val msg = error.message ?: "Unduhan terhenti"
                    _uiState.value = DownloadUiState.Error(msg)
                    addAlert("Unduhan Gagal", msg, isError = true)
                }
            )
        }
    }

            // Abaikan error dari yt-dlp jika berhenti karena tombol Pause atau Cancel
            if (isUserPaused || isUserCancelled) return@launch

            result.fold(
                onSuccess = { savedPath ->
                    _uiState.value = DownloadUiState.Success(savedPath)
                    refreshFilesList()
                    addAlert("Unduhan Selesai", "${details.title} (${format.label}) berhasil disimpan.")
                },
                onFailure = { error ->
                    val msg = error.message ?: "Unduhan terhenti"
                    _uiState.value = DownloadUiState.Error(msg)
                    addAlert("Unduhan Gagal", msg, isError = true)
                }
            )
        }
    }

    fun pauseCurrentDownload() {
        val currentState = _uiState.value as? DownloadUiState.Downloading ?: return
        isUserPaused = true
        YoutubeDL.getInstance().destroyProcessById(VideoRepository.DOWNLOAD_PROCESS_ID)
        downloadJob?.cancel()

        _uiState.value = currentState.copy(
            isPaused = true,
            statusLine = "Unduhan dijeda (${currentState.progress.toInt()}%)"
        )
    }

    fun resumeCurrentDownload() {
        val currentState = _uiState.value as? DownloadUiState.Downloading ?: return
        startDownload(
            details = currentState.details,
            format = currentState.selectedFormat,
            initialProgress = currentState.progress
        )
    }

    fun cancelCurrentDownload() {
        val currentState = _uiState.value as? DownloadUiState.Downloading
        isUserCancelled = true
        isUserPaused = false
        YoutubeDL.getInstance().destroyProcessById(VideoRepository.DOWNLOAD_PROCESS_ID)
        downloadJob?.cancel()

        currentState?.let {
            repository.cleanupTempFiles(_storagePath.value, it.details.title)
            _uiState.value = DownloadUiState.InfoReady(it.details)
        } ?: run {
            _uiState.value = DownloadUiState.Idle
        }
    }

    fun resetState() {
        _uiState.value = DownloadUiState.Idle
    }

    private fun addAlert(title: String, message: String, isError: Boolean = false) {
        _alerts.value = listOf(AppNotificationItem(title = title, message = message, isError = isError)) + _alerts.value
    }

    private fun isConnectedToWifi(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
