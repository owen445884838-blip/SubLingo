package com.sublingo.app.data.media

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YoutubeDlRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var ready = false
    @Volatile private var currentCapabilities: YoutubeDlCapabilities? = null

    @Synchronized
    fun ensureInitialized(): YoutubeDlCapabilities {
        if (ready) return requireNotNull(currentCapabilities)
        try {
            val pageSizeBytes = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }
                .getOrDefault(4_096L)
                .coerceAtLeast(4_096L)
            val supportsPackagedExecutables = pageSizeBytes <= 4_096L
            verifyPackagedRuntime(supportsPackagedExecutables)
            if (!supportsPackagedExecutables) removeIncompatibleExtractedExecutables()
            YoutubeDL.getInstance().init(context)
            if (supportsPackagedExecutables) {
                FFmpeg.getInstance().init(context)
                Aria2c.getInstance().init(context)
            }
            val capabilities = YoutubeDlCapabilities(
                pageSizeBytes = pageSizeBytes,
                ffmpegAvailable = supportsPackagedExecutables,
                aria2cAvailable = supportsPackagedExecutables,
            )
            currentCapabilities = capabilities
            ready = true
            Log.i(
                TAG,
                if (supportsPackagedExecutables) {
                    "yt-dlp, FFmpeg and aria2c initialized (pageSize=$pageSizeBytes)"
                } else {
                    "yt-dlp initialized in 16KB-compatible mode; packaged FFmpeg/aria2c disabled (pageSize=$pageSizeBytes)"
                },
            )
            return capabilities
        } catch (error: Throwable) {
            ready = false
            currentCapabilities = null
            Log.e(TAG, "Unable to initialize youtubedl-android runtime", error)
            throw IllegalStateException(
                "下载引擎初始化失败：${error.message ?: error.javaClass.simpleName}",
                error,
            )
        }
    }

    fun capabilities(): YoutubeDlCapabilities = ensureInitialized()

    private fun verifyPackagedRuntime(includeOptionalExecutables: Boolean) {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val required = buildList {
            add("libpython.zip.so")
            if (includeOptionalExecutables) add("libffmpeg.zip.so")
        }
        val missing = required.filterNot { File(nativeDir, it).isFile }
        check(missing.isEmpty()) {
            "安装包未解压必要组件 ${missing.joinToString()}，请卸载旧版后重新安装"
        }
    }

    private fun removeIncompatibleExtractedExecutables() {
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        listOf("ffmpeg", "aria2c").forEach { name ->
            val directory = File(packages, name)
            if (directory.exists() && !directory.deleteRecursively()) {
                Log.w(TAG, "Could not remove incompatible extracted package ${directory.absolutePath}")
            }
        }
    }

    companion object { private const val TAG = "YoutubeDlRuntime" }
}

data class YoutubeDlCapabilities(
    val pageSizeBytes: Long,
    val ffmpegAvailable: Boolean,
    val aria2cAvailable: Boolean,
)
