package com.sublingo.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import kotlin.concurrent.thread
import com.sublingo.app.data.media.YoutubeDlRuntime
import com.sublingo.app.data.storage.AppStorageCleaner
import java.util.concurrent.Executors

@HiltAndroidApp
class SubLingoApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var youtubeDlRuntime: YoutubeDlRuntime
    @Inject lateinit var storageCleaner: AppStorageCleaner
    // Keep one slot available when two long subtitle/cloud stages are already running. Download
    // concurrency is controlled by WorkManager's persisted queue and yt-dlp policy; a two-thread
    // pool could otherwise starve a newly enqueued download indefinitely behind those stages.
    private val workExecutor by lazy { Executors.newFixedThreadPool(3) }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(workExecutor)
            .build()
    override fun onCreate() {
        super.onCreate()
        thread(name = "youtubedl-initializer") {
            runCatching { storageCleaner.cleanStartupTransients() }
            runCatching { youtubeDlRuntime.ensureInitialized() }
        }
    }
}
