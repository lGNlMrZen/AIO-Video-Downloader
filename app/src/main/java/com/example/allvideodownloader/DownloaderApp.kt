package com.example.allvideodownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloaderApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initLibraries()
    }

    private fun initLibraries() {
        applicationScope.launch {
            try {
                YoutubeDL.getInstance().init(this@DownloaderApp)
                FFmpeg.getInstance().init(this@DownloaderApp)
                Log.d("DownloaderApp", "YoutubeDL & FFmpeg berhasil diinisialisasi")
            } catch (e: YoutubeDLException) {
                Log.e("DownloaderApp", "Gagal inisialisasi library", e)
            }
        }
    }
}
