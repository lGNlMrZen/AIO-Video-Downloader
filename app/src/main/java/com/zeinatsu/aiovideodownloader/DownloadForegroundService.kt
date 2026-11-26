package com.zeinatsu.aiovideodownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_PROGRESS_ID = "vidgrab_download_progress"
        const val CHANNEL_COMPLETE_ID = "vidgrab_download_complete"
        const val NOTIF_PROGRESS_ID = 1001
        const val NOTIF_RESULT_ID = 1002

        const val ACTION_START = "com.zeinatsu.aiovideodownloader.ACTION_START"
        const val ACTION_PAUSE = "com.zeinatsu.aiovideodownloader.ACTION_PAUSE"
        const val ACTION_RESUME = "com.zeinatsu.aiovideodownloader.ACTION_RESUME"
        const val ACTION_CANCEL = "com.zeinatsu.aiovideodownloader.ACTION_CANCEL"
        const val EXTRA_INITIAL_PROGRESS = "extra_initial_progress"

        fun startDownload(context: Context, initialProgress: Float = 0f) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INITIAL_PROGRESS, initialProgress)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendAction(context: Context, actionName: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = actionName
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: VideoRepository
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var downloadJob: Job? = null

    private var isUserPaused = false
    private var isUserCancelled = false
    private var lastKnownProgress = 0f

    override fun onCreate() {
        super.onCreate()
        repository = VideoRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val initProgress = intent.getFloatExtra(EXTRA_INITIAL_PROGRESS, 0f)
                executeDownload(initProgress)
            }
            ACTION_PAUSE -> pauseDownload()
            ACTION_RESUME -> executeDownload(lastKnownProgress)
            ACTION_CANCEL -> cancelDownload()
        }
        return START_NOT_STICKY
    }

    private fun executeDownload(initialProgress: Float) {
        val session = DownloadStateHolder.activeSession ?: run {
            stopSelf()
            return
        }

        isUserPaused = false
        isUserCancelled = false
        lastKnownProgress = initialProgress
        downloadJob?.cancel()

        acquireWakeLock()

        val startText = if (initialProgress > 0f) "Melanjutkan unduhan..." else "Memulai unduhan..."
        val initialNotif = buildProgressNotification(
            title = session.details.title,
            contentText = startText,
            progress = initialProgress.toInt(),
            isPaused = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_PROGRESS_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_PROGRESS_ID, initialNotif)
        }

        DownloadStateHolder.updateUiState(
            DownloadUiState.Downloading(
                details = session.details,
                selectedFormat = session.selectedFormat,
                progress = initialProgress,
                etaSeconds = 0L,
                statusLine = startText,
                isPaused = false
            )
        )

        downloadJob = serviceScope.launch {
            var lastUpdateMs = 0L

            val result = repository.downloadVideo(
                details = session.details,
                selectedFormat = session.selectedFormat,
                storageSubPath = session.storagePath,
                advancedOptions = session.advancedOptions
            ) { progress, eta, line ->
                if (!isUserPaused && !isUserCancelled) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMs >= 450L || progress >= 99f) {
                        lastUpdateMs = now
                        val safeProgress = (if (progress > 0f) progress else initialProgress).coerceIn(0f, 100f)
                        lastKnownProgress = safeProgress

                        DownloadStateHolder.updateUiState(
                            DownloadUiState.Downloading(
                                details = session.details,
                                selectedFormat = session.selectedFormat,
                                progress = safeProgress,
                                etaSeconds = eta,
                                statusLine = line,
                                isPaused = false
                            )
                        )

                        val etaText = if (eta > 0) " • Sisa ${eta}d" else ""
                        notificationManager.notify(
                            NOTIF_PROGRESS_ID,
                            buildProgressNotification(
                                title = session.details.title,
                                contentText = "${safeProgress.toInt()}%$etaText",
                                progress = safeProgress.toInt(),
                                isPaused = false
                            )
                        )
                    }
                }
            }

            if (isUserPaused || isUserCancelled) return@launch

            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)

            result.fold(
                onSuccess = { savedPath ->
                    DownloadStateHolder.updateUiState(DownloadUiState.Success(savedPath))
                    DownloadStateHolder.notifyFilesChanged()
                    DownloadStateHolder.addAlert(
                        "Unduhan Selesai",
                        "${session.details.title} (${session.selectedFormat.label}) berhasil disimpan."
                    )
                    showCompletionNotification(
                        title = "Unduhan Selesai",
                        message = "${session.details.title} (${session.selectedFormat.label})",
                        isError = false
                    )
                },
                onFailure = { error ->
                    val msg = error.message ?: "Unduhan terhenti"
                    DownloadStateHolder.updateUiState(DownloadUiState.Error(msg))
                    DownloadStateHolder.addAlert("Unduhan Gagal", msg, isError = true)
                    showCompletionNotification(
                        title = "Unduhan Gagal",
                        message = msg,
                        isError = true
                    )
                }
            )

            stopSelf()
        }
    }

    private fun pauseDownload() {
        val session = DownloadStateHolder.activeSession ?: return
        isUserPaused = true
        YoutubeDL.getInstance().destroyProcessById(VideoRepository.DOWNLOAD_PROCESS_ID)
        downloadJob?.cancel()
        releaseWakeLock()

        val pausedLine = "Unduhan dijeda (${lastKnownProgress.toInt()}%)"
        DownloadStateHolder.updateUiState(
            DownloadUiState.Downloading(
                details = session.details,
                selectedFormat = session.selectedFormat,
                progress = lastKnownProgress,
                etaSeconds = 0L,
                statusLine = pausedLine,
                isPaused = true
            )
        )

        stopForeground(STOP_FOREGROUND_DETACH)
        notificationManager.notify(
            NOTIF_PROGRESS_ID,
            buildProgressNotification(
                title = session.details.title,
                contentText = pausedLine,
                progress = lastKnownProgress.toInt(),
                isPaused = true
            )
        )
    }

    private fun cancelDownload() {
        val session = DownloadStateHolder.activeSession
        isUserCancelled = true
        isUserPaused = false
        YoutubeDL.getInstance().destroyProcessById(VideoRepository.DOWNLOAD_PROCESS_ID)
        downloadJob?.cancel()
        releaseWakeLock()

        if (session != null) {
            repository.cleanupTempFiles(session.storagePath, session.details.title)
            DownloadStateHolder.updateUiState(DownloadUiState.InfoReady(session.details))
        } else {
            DownloadStateHolder.updateUiState(DownloadUiState.Idle)
        }

        DownloadStateHolder.activeSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIF_PROGRESS_ID)
        stopSelf()
    }

    private fun buildProgressNotification(
        title: String,
        contentText: String,
        progress: Int,
        isPaused: Boolean
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadForegroundService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelActionIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0 && !isPaused)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Lanjut" else "Jeda",
                toggleActionIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Batal",
                cancelActionIntent
            )
            .build()
    }

    private fun showCompletionNotification(title: String, message: String, isError: Boolean) {
        val openAppIntent = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setSmallIcon(if (isError) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIF_RESULT_ID, notif)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Progres Unduhan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan progres unduhan video/audio di latar belakang"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                "Status Unduhan Selesai",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi saat unduhan selesai atau gagal"
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VidGrab::DownloadWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
