package com.zeinatsu.aiovideodownloader

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

    companion object {
        const val DOWNLOAD_PROCESS_ID = "vidgrab_active_dl"
        private const val PREFS_NAME = "vidgrab_history_prefs"
        private const val KEY_HIDDEN_PATHS = "hidden_history_paths"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    suspend fun updateEngine(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context,
                YoutubeDL.UpdateChannel._STABLE
            )
            val version = YoutubeDL.getInstance().version(context) ?: "Terbaru"
            Result.success("Status: ${status?.name ?: "OK"} (Versi: $version)")
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Gagal memperbarui engine yt-dlp"))
        }
    }

    fun getEngineVersion(): String {
        return YoutubeDL.getInstance().version(context) ?: "Built-in"
    }

    suspend fun fetchVideoInfo(url: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-check-certificates")
                addOption("--user-agent", browserUserAgent)
                addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val durationFormatted = formatDuration(info.duration)

            val formatOptions = listOf(
                VideoFormatOption(
                    label = "4K UHD",
                    formatSelector = "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best"
                ),
                VideoFormatOption(
                    label = "2K QHD",
                    formatSelector = "bestvideo[height<=1440]+bestaudio/best[height<=1440]/best"
                ),
                VideoFormatOption(
                    label = "1080p FHD",
                    formatSelector = "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                ),
                VideoFormatOption(
                    label = "720p HD",
                    formatSelector = "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                ),
                VideoFormatOption(
                    label = "480p SD",
                    formatSelector = "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
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
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengambil metadata video. Pastikan video bersifat publik."))
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

            val codecFilter = when (advancedOptions.codec) {
                "AV1" -> "[vcodec^=av01]"
                "VP9" -> "[vcodec^=vp9]"
                "H.264" -> "[vcodec^=avc1]"
                else -> ""
            }

            val finalFormatSelector = if (!selectedFormat.isAudioOnly && codecFilter.isNotEmpty()) {
                val primaryWithCodec = selectedFormat.formatSelector.replace(
                    "bestvideo",
                    "bestvideo$codecFilter"
                )
                "$primaryWithCodec/${selectedFormat.formatSelector}"
            } else {
                selectedFormat.formatSelector
            }

            val request = YoutubeDLRequest(details.url).apply {
                addOption("-f", finalFormatSelector)
                addOption("-o", outputTemplate)
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--continue")
                addOption("--user-agent", browserUserAgent)

                if (!selectedFormat.isAudioOnly) {
                    addOption("--merge-output-format", "mp4")
                }

                if (advancedOptions.includeSubtitles) {
                    addOption("--write-subs")
                    addOption("--sub-langs", "id,en")
                    addOption("--convert-subs", "srt")
                }
            }

            YoutubeDL.getInstance().execute(request, DOWNLOAD_PROCESS_ID) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line)
            }

            val downloadedFile = outputDir.listFiles()
                ?.filter { it.name.startsWith(safeTitle) && !it.name.endsWith(".srt") && !it.name.endsWith(".part") }
                ?.maxByOrNull { it.lastModified() }

            val finalPath = downloadedFile?.absolutePath ?: outputDir.absolutePath

            downloadedFile?.let { file ->
                unhideHistoryPath(file.absolutePath)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            }

            Result.success(finalPath)
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Proses unduhan dihentikan."))
        }
    }

    fun hideFromHistoryOnly(file: File) {
        val current = prefs.getStringSet(KEY_HIDDEN_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(file.absolutePath)
        prefs.edit().putStringSet(KEY_HIDDEN_PATHS, current).apply()
    }

    fun deleteFileAndHistory(file: File) {
        hideFromHistoryOnly(file)
        if (file.exists()) {
            val path = file.absolutePath
            file.delete()
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }
    }

    private fun unhideHistoryPath(path: String) {
        val current = prefs.getStringSet(KEY_HIDDEN_PATHS, emptySet())?.toMutableSet() ?: return
        if (current.remove(path)) {
            prefs.edit().putStringSet(KEY_HIDDEN_PATHS, current).apply()
        }
    }

    fun cleanupTempFiles(storageSubPath: String, videoTitle: String) {
        val dir = resolveStorageDirectory(storageSubPath)
        if (!dir.exists()) return
        val safeTitle = videoTitle
            .replace(Regex("[^a-zA-Z0-9\\s-_]"), "")
            .trim()
            .take(50)

        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(safeTitle) && (file.name.endsWith(".part") || file.name.endsWith(".ytdl"))) {
                file.delete()
            }
        }
    }

    fun getDownloadedFiles(storageSubPath: String): List<RealDownloadedFile> {
        val dir = resolveStorageDirectory(storageSubPath)
        if (!dir.exists()) return emptyList()

        val hiddenPaths = prefs.getStringSet(KEY_HIDDEN_PATHS, emptySet()) ?: emptySet()
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        return dir.listFiles()
            ?.filter {
                it.isFile &&
                    !hiddenPaths.contains(it.absolutePath) &&
                    (
                        it.extension.equals("mp4", true) ||
                        it.extension.equals("mkv", true) ||
                        it.extension.equals("webm", true) ||
                        it.extension.equals("m4a", true) ||
                        it.extension.equals("mp3", true)
                    )
            }
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
