package com.zeinatsu.aiovideodownloader

data class VideoFormatOption(
    val label: String,
    val formatSelector: String,
    val isAudioOnly: Boolean = false
)

data class VideoDetails(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationText: String,
    val formats: List<VideoFormatOption>
)

sealed class DownloadUiState {
    object Idle : DownloadUiState()
    object FetchingInfo : DownloadUiState()
    data class InfoReady(val details: VideoDetails) : DownloadUiState()
    data class Downloading(
        val details: VideoDetails,
        val selectedFormat: VideoFormatOption,
        val progress: Float,
        val etaSeconds: Long,
        val statusLine: String,
        val isPaused: Boolean = false
    ) : DownloadUiState()
    data class Success(val filePath: String) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}
