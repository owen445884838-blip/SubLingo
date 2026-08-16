package com.sublingo.app.data.media

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.sublingo.app.R
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
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
            installPackagedYoutubeDl()
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
                    "yt-dlp $PACKAGED_YTDLP_VERSION, FFmpeg and aria2c initialized (pageSize=$pageSizeBytes)"
                } else {
                    "yt-dlp $PACKAGED_YTDLP_VERSION initialized in 16KB-compatible mode; packaged FFmpeg/aria2c disabled (pageSize=$pageSizeBytes)"
                },
            )
            return capabilities
        } catch (error: Throwable) {
            ready = false
            currentCapabilities = null
            Log.e(TAG, "Unable to initialize youtubedl-android runtime", error)
            throw IllegalStateException(
                "下载引擎初始化失败：${YoutubeDlRuntimeErrors.userMessage(error)}",
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

    private fun installPackagedYoutubeDl() {
        val directory = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}")
            .apply { check(isDirectory || mkdirs()) { "无法创建下载引擎目录" } }
        val target = File(directory, YoutubeDL.ytdlpBin)
        val preferences = context.getSharedPreferences(PACKAGED_YTDLP_PREFERENCES, Context.MODE_PRIVATE)
        if (
            preferences.getString(PACKAGED_YTDLP_VERSION_KEY, null) == PACKAGED_YTDLP_VERSION &&
            target.isFile && target.sha256() == PACKAGED_YTDLP_SHA256
        ) {
            return
        }

        val staging = File(directory, "${YoutubeDL.ytdlpBin}.part")
        try {
            context.resources.openRawResource(R.raw.ytdlp).use { input ->
                staging.outputStream().use(input::copyTo)
            }
            check(staging.sha256() == PACKAGED_YTDLP_SHA256) {
                "安装包中的 yt-dlp 校验失败，请重新安装应用"
            }
            Os.rename(staging.absolutePath, target.absolutePath)
            check(
                preferences.edit()
                    .putString(PACKAGED_YTDLP_VERSION_KEY, PACKAGED_YTDLP_VERSION)
                    .commit(),
            ) { "无法记录下载引擎版本" }
        } finally {
            if (staging.exists() && !staging.delete()) {
                Log.w(TAG, "Could not remove staged yt-dlp package")
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val TAG = "YoutubeDlRuntime"
        const val PACKAGED_YTDLP_VERSION = "2026.08.16.020253"
        const val PACKAGED_YTDLP_SHA256 = "33075a8f5a0ee7189302dde76d0caab8a2c1c4a1b82235ed761814df89e0900d"
        private const val PACKAGED_YTDLP_PREFERENCES = "packaged-ytdlp"
        private const val PACKAGED_YTDLP_VERSION_KEY = "version"
    }
}

data class YoutubeDlCapabilities(
    val pageSizeBytes: Long,
    val ffmpegAvailable: Boolean,
    val aria2cAvailable: Boolean,
)

internal object YoutubeDlRuntimeErrors {
    fun userMessage(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        if (causes.any { it is LinkageError || it is ExceptionInInitializerError }) {
            return "安装包中的运行时组件不兼容，请更新应用后重试"
        }
        return causes
            .asSequence()
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull { it != GENERIC_INITIALIZATION_ERROR }
            ?: "无法解压或启动运行时组件，请确认存储空间充足后重试"
    }

    private const val GENERIC_INITIALIZATION_ERROR = "failed to initialize"
    private const val MAX_CAUSE_DEPTH = 8
}
