package com.example.allvideodownloader

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class VideoRepository(private val context: Context) {

    // Mengambil judul, thumbnail, dan opsi format dari URL (YT, IG, TikTok, FB)
    suspend fun fetchVideoInfo(url: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-check-certificates")
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val durationFormatted = formatDuration(info.duration)

            // Opsi format universal yang kompatibel untuk YT, TikTok, IG, & FB
            val formatOptions = listOf(
                VideoFormatOption(
                    label = "Best Quality (Auto / 1080p+ MP4)",
                    formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                ),
                VideoFormatOption(
                    label = "HD 720p (MP4)",
                    formatSelector = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]"
                ),
                VideoFormatOption(
                    label = "SD 480p (Hemat Kuota)",
                    formatSelector = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]"
                ),
                VideoFormatOption(
                    label = "Audio Only (M4A)",
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
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengambil informasi video. Pastikan link valid dan publik."))
        }
    }

    // Mengeksekusi download & menyimpannya ke folder publik Movies/Downloads
    suspend fun downloadVideo(
        details: VideoDetails,
        selectedFormat: VideoFormatOption,
        onProgress: (progress: Float, eta: Long, line: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseDir = if (selectedFormat.isAudioOnly) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }

            val outputDir = File(baseDir, "AllVideoDownloader")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Sanitasi nama file agar aman dari karakter ilegal
            val safeTitle = details.title
                .replace(Regex("[^a-zA-Z0-9\\s-_]"), "")
                .trim()
                .take(50)
                .ifEmpty { "video_${System.currentTimeMillis()}" }

            val outputTemplate = File(outputDir, "$safeTitle.%(ext)s").absolutePath

            val request = YoutubeDLRequest(details.url).apply {
                addOption("-f", selectedFormat.formatSelector)
                addOption("-o", outputTemplate)
                addOption("--no-playlist")
                addOption("--no-mtime")
                if (!selectedFormat.isAudioOnly) {
                    addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line)
            }

            // Cari file hasil unduhan terbaru di folder tersebut untuk di-scan ke Galeri
            val downloadedFile = outputDir.listFiles()
                ?.filter { it.name.startsWith(safeTitle) }
                ?.maxByOrNull { it.lastModified() }

            val finalPath = downloadedFile?.absolutePath ?: outputDir.absolutePath

            // Trigger MediaScanner agar video langsung muncul di Galeri Android
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
            Result.failure(Exception(e.localizedMessage ?: "Terjadi kesalahan saat mengunduh video."))
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
