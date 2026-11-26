package com.zeinatsu.aiovideodownloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveDownloadSession(
    val details: VideoDetails,
    val selectedFormat: VideoFormatOption,
    val storagePath: String,
    val advancedOptions: AdvancedDownloadOptions
)

object DownloadStateHolder {
    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val _alerts = MutableStateFlow<List<AppNotificationItem>>(emptyList())
    val alerts: StateFlow<List<AppNotificationItem>> = _alerts.asStateFlow()

    private val _filesRefreshTick = MutableStateFlow(0L)
    val filesRefreshTick: StateFlow<Long> = _filesRefreshTick.asStateFlow()

    var activeSession: ActiveDownloadSession? = null

    fun updateUiState(state: DownloadUiState) {
        _uiState.value = state
    }

    fun addAlert(title: String, message: String, isError: Boolean = false) {
        _alerts.value = listOf(
            AppNotificationItem(title = title, message = message, isError = isError)
        ) + _alerts.value
    }

    fun notifyFilesChanged() {
        _filesRefreshTick.value = System.currentTimeMillis()
    }
}
