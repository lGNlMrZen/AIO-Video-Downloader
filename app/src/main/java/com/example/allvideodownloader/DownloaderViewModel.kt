package com.example.allvideodownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application.applicationContext)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
    }

    // Ekstrak link bersih jika teks Share dari TikTok/IG/YT mengandung caption tambahan
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
            _uiState.value = DownloadUiState.Error("Masukkan tautan video terlebih dahulu.")
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
                    _uiState.value = DownloadUiState.Error(error.message ?: "Gagal memproses link")
                }
            )
        }
    }

    fun startDownload(details: VideoDetails, format: VideoFormatOption) {
        viewModelScope.launch {
            _uiState.value = DownloadUiState.Downloading(
                details = details,
                progress = 0f,
                etaSeconds = 0L,
                statusLine = "Menyiapkan unduhan..."
            )

            val result = repository.downloadVideo(details, format) { progress, eta, line ->
                _uiState.value = DownloadUiState.Downloading(
                    details = details,
                    progress = progress.coerceIn(0f, 100f),
                    etaSeconds = eta,
                    statusLine = line
                )
            }

            result.fold(
                onSuccess = { savedPath ->
                    _uiState.value = DownloadUiState.Success(savedPath)
                },
                onFailure = { error ->
                    _uiState.value = DownloadUiState.Error(error.message ?: "Unduhan gagal")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = DownloadUiState.Idle
    }
}
