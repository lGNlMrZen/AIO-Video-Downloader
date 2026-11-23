package com.example.allvideodownloader

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdvancedDownloadOptions(
    val codec: String = "H.264",
    val audioBitrate: String = "320 kbps",
    val includeSubtitles: Boolean = false,
    val wifiOnly: Boolean = false
)

data class RealDownloadedFile(
    val file: File,
    val title: String,
    val format: String,
    val sizeText: String,
    val dateText: String
)

class VideoRepository(private val context: Context) {

    suspend fun fetchVideoInfo(url: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-check-certificates")
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val durationFormatted = formatDuration(info.duration)

            val formatOptions = listOf(
                VideoFormatOption(
                    label = "1080p FHD",
                    formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                ),
                VideoFormatOption(
                    label = "720p HD",
                    formatSelector = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]"
                ),
                VideoFormatOption(
                    label = "480p SD",
                    formatSelector = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]"
                ),
                VideoFormatOption(
                    label = "Audio M4A",
                    formatSelector = "bestaudio[ext=m4a]/bestaudio",
                    isAudioOnly = true
                )
            )

            val details = VideoDetails(
                url = url,
                title = info.title ?: "Video_${System.currentTimeMillis()}",
                uploader = info.uploader ?: "Unknown Creator",
                thumbnailUrl = info.thumbnail,
                durationText = durationFormatted,
                formats = formatOptions
            )

            Result.success(details)
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengambil metadata video. Pastikan link publik & valid."))
        }
    }

    suspend fun downloadVideo(
        details: VideoDetails,
        selectedFormat: VideoFormatOption,
        storageSubPath: String,
        advancedOptions: AdvancedDownloadOptions,
        onProgress: (progress: Float, eta: Long, line: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputDir = resolveStorageDirectory(storageSubPath)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val safeTitle = details.title
                .replace(Regex("[^a-zA-Z0-9\\s-_]"), "")
                .trim()
                .take(50)
                .ifEmpty { "VidGrab_${System.currentTimeMillis()}" }

            val outputTemplate = File(outputDir, "$safeTitle.%(ext)s").absolutePath

            val request = YoutubeDLRequest(details.url).apply {
                addOption("-f", selectedFormat.formatSelector)
                addOption("-o", outputTemplate)
                addOption("--no-playlist")
                addOption("--no-mtime")

                if (!selectedFormat.isAudioOnly) {
                    addOption("--merge-output-format", "mp4")
                }

                if (advancedOptions.includeSubtitles) {
                    addOption("--write-subs")
                    addOption("--sub-langs", "id,en")
                    addOption("--convert-subs", "srt")
                }
            }

            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line)
            }

            val downloadedFile = outputDir.listFiles()
                ?.filter { it.name.startsWith(safeTitle) && !it.name.endsWith(".srt") }
                ?.maxByOrNull { it.lastModified() }

            val finalPath = downloadedFile?.absolutePath ?: outputDir.absolutePath

            downloadedFile?.let { file ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            }

            Result.success(finalPath)
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Proses unduhan gagal."))
        }
    }

    fun getDownloadedFiles(storageSubPath: String): List<RealDownloadedFile> {
        val dir = resolveStorageDirectory(storageSubPath)
        if (!dir.exists()) return emptyList()

        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return dir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("mp4", true) || it.extension.equals("m4a", true) || it.extension.equals("mp3", true)) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val sizeMb = file.length() / (1024.0 * 1024.0)
                RealDownloadedFile(
                    file = file,
                    title = file.nameWithoutExtension,
                    format = file.extension.uppercase(Locale.US),
                    sizeText = String.format(Locale.US, "%.1f MB", sizeMb),
                    dateText = sdf.format(Date(file.lastModified()))
                )
            } ?: emptyList()
    }

    private fun resolveStorageDirectory(subPath: String): File {
        val cleanPath = subPath.trim('/')
        val root = Environment.getExternalStorageDirectory()
        return if (cleanPath.startsWith("storage/emulated/0")) {
            File("/$cleanPath")
        } else {
            File(root, cleanPath)
        }
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "--:--"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
