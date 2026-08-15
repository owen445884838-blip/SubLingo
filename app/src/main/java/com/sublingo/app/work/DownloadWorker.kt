package com.sublingo.app.work

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.sublingo.app.data.media.SubtitlePipelineScheduler
import com.sublingo.app.data.media.YoutubeDlRuntime
import com.sublingo.app.data.media.MediaTrackMuxer
import com.sublingo.app.data.media.DownloadAttempt
import com.sublingo.app.data.media.DownloadRequestPolicy
import com.sublingo.app.security.SecretStore
import com.sublingo.app.data.db.ProcessingJobDao
import com.sublingo.app.data.db.ProcessingJobEntity
import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.data.db.VideoRepository
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.domain.model.ProcessingState
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val deps: WorkerDeps = EntryPointAccessors.fromApplication(applicationContext, WorkerDeps::class.java)
    @Volatile private var activeProcessIds: Set<String> = emptySet()

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL).orEmpty()
        if (url.isBlank()) return Result.failure(workDataOf(STATUS to "缺少 URL"))

        val videoId = stableVideoId(url)
        val outputDir = File(applicationContext.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val taskDir = File(outputDir, videoId).apply {
            mkdirs()
        }
        val outputTemplate = File(taskDir, "media.%(ext)s").absolutePath
        val processId = "download-$videoId"
        val jobId = "job-$videoId"
        var cookieFile: File? = null
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) destroyActiveProcesses()
        }

        return try {
            setForeground(createForegroundInfo(0, "等待下载引擎"))
            deps.videoRepository().upsert(
                VideoEntity(
                    id = videoId,
                    originalUrl = url,
                    canonicalUrl = url,
                    source = "youtubedl-android",
                    title = "等待下载",
                ),
            )
            deps.jobDao().upsert(
                ProcessingJobEntity(
                    id = jobId,
                    videoId = videoId,
                    currentStage = ProcessingStage.DOWNLOAD,
                    state = ProcessingState.RUNNING,
                    progress = 5,
                ),
            )
            setProgress(workDataOf(PROGRESS to 5, STATUS to "正在初始化下载引擎"))
            setForeground(createForegroundInfo(5, "正在初始化下载引擎"))
            val capabilities = deps.youtubeDlRuntime().ensureInitialized()
            setProgress(
                workDataOf(
                    PROGRESS to 8,
                    STATUS to if (capabilities.ffmpegAvailable) "正在准备下载" else "正在使用 16KB 兼容下载模式",
                ),
            )
            setForeground(createForegroundInfo(8, "正在准备下载"))

            val cookie = deps.secretStore().read(DOWNLOAD_COOKIE_ALIAS)?.takeIf { it.isNotBlank() }
            cookieFile = cookie?.let { createCookieFile(url, it) }
            val attempts = DownloadRequestPolicy.attempts(
                url,
                capabilities.ffmpegAvailable,
                cookieConfigured = cookieFile != null,
            )
            val failures = mutableListOf<Throwable>()
            var response: YoutubeDLResponse? = null
            attempts.forEachIndexed { index, attempt ->
                if (response != null) return@forEachIndexed
                if (index > 0) {
                    clearAttemptArtifacts(taskDir)
                    setProgress(workDataOf(PROGRESS to 8, STATUS to "首选流不可用，正在尝试${attempt.label}"))
                }
                response = runCatching {
                    if (attempt.nativeMuxAudioSelector != null) {
                        val videoRequest = buildDownloadRequest(
                            url = url,
                            outputTemplate = File(taskDir, "video.%(ext)s").absolutePath,
                            attempt = attempt,
                            cookieFile = cookieFile,
                        )
                        val videoResponse = executeDownload(videoRequest, "$processId-$index-video") { progress ->
                            updateDownloadProgress(jobId, 5f + progress.percent * .55f, "正在下载高画质视频流", progress)
                        }
                        val audioRequest = buildDownloadRequest(
                            url = url,
                            outputTemplate = File(taskDir, "audio.%(ext)s").absolutePath,
                            attempt = attempt.copy(formatSelector = attempt.nativeMuxAudioSelector, nativeMuxAudioSelector = null),
                            cookieFile = cookieFile,
                        )
                        executeDownload(audioRequest, "$processId-$index-audio") { progress ->
                            updateDownloadProgress(jobId, 60f + progress.percent * .34f, "正在下载高品质音频流", progress)
                        }
                        videoResponse
                    } else {
                        val request = buildDownloadRequest(url, outputTemplate, attempt, cookieFile)
                        executeDownload(request, "$processId-$index") { progress ->
                            updateDownloadProgress(jobId, progress.percent, attempt.label, progress)
                        }
                    }
                }.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        // Cancellation is a control signal from WorkManager (explicit cancel,
                        // unique-work replacement, or worker stop), not a failed format attempt.
                        // Never swallow it and continue into another yt-dlp request/progress update.
                        if (error is CancellationException || isStopped) throw error
                        // No output at all means extraction never reached YouTube. Trying the
                        // remaining format/client variants would use the same blocked route and
                        // postpone the actionable VPN diagnostic by several minutes.
                        if (error is DownloadAttemptIdleTimeoutException) throw error
                        failures += error
                        Log.w(TAG, "download attempt ${index + 1}/${attempts.size} (${attempt.label}) failed: ${error.message}")
                        null
                    },
                )
            }
            val completedResponse = response ?: throw YoutubeDLException(
                DownloadRequestPolicy.failureMessage(url, failures, cookieConfigured = cookieFile != null),
            )

            setProgress(workDataOf(PROGRESS to 95, STATUS to "正在校验下载结果"))
            setForeground(createForegroundInfo(95, "正在校验下载结果"))

            if (com.sublingo.app.BuildConfig.DEBUG) {
                Log.d(TAG, "yt-dlp completed; outputLength=${completedResponse.out.length}")
            }
            val mediaExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v")
            val candidates = taskDir.listFiles().orEmpty().filter { it.isFile }
            Log.i(TAG, "download artifacts=${candidates.joinToString { "${it.name}(${it.length()})" }}")
            val downloadedVideo = candidates
                .filter { it.extension.lowercase() in mediaExtensions && it.length() > MIN_MEDIA_BYTES }
                .maxByOrNull { it.length() }
                ?: throw IllegalStateException(
                    "未生成有效视频文件：${candidates.joinToString { it.name }.ifBlank { "输出目录为空" }}",
                )
            val downloadedAudio = candidates
                .filter { it != downloadedVideo && it.extension.lowercase() in setOf("m4a", "aac", "mp3", "opus", "ogg") && it.length() > MIN_MEDIA_BYTES }
                .maxByOrNull { it.length() }
            val downloadedFile = if (downloadedAudio != null && !hasAudioTrack(downloadedVideo)) {
                val pendingMerge = File(taskDir, "media.merged.mp4.part")
                runCatching {
                    MediaTrackMuxer.merge(downloadedVideo, downloadedAudio, pendingMerge)
                    val committedMerge = File(taskDir, "media.merged.mp4")
                    committedMerge.delete()
                    check(pendingMerge.renameTo(committedMerge)) { "无法提交合并后的音视频文件" }
                    committedMerge
                }.onFailure { Log.w(TAG, "Android mux failed; keeping separate audio artifact", it) }
                    .getOrDefault(downloadedVideo)
            } else downloadedVideo

            val metadata = readLocalMetadata(downloadedFile, taskDir, videoId)
            val originalTitle = readOriginalTitle(taskDir)
            require(metadata.durationMs > 0L && metadata.hasVideo) { "下载文件缺少可识别的视频轨道，请重试" }
            if (!metadata.hasAudio) {
                Log.w(TAG, "Downloaded media audio is not visible to Android metadata; subtitle pipeline will verify with FFmpeg and repair from source if needed")
            }
            val entity = VideoEntity(
                id = videoId,
                originalUrl = url,
                canonicalUrl = url,
                source = "youtubedl-android",
                remoteVideoId = null,
                title = originalTitle ?: metadata.title ?: "已下载视频",
                thumbnail = metadata.thumbnailPath,
                filePath = downloadedFile.absolutePath,
                durationMs = metadata.durationMs,
                fileSize = downloadedFile.length(),
                updatedAt = System.currentTimeMillis(),
            )
            deps.videoRepository().upsert(entity)
            deps.jobDao().upsert(
                ProcessingJobEntity(
                    id = jobId,
                    videoId = videoId,
                    currentStage = ProcessingStage.DOWNLOAD,
                    state = ProcessingState.SUCCEEDED,
                    progress = 100,
                ),
            )
            deps.subtitlePipelineScheduler().enqueue(videoId, jobId)
            setProgress(workDataOf(PROGRESS to 100, STATUS to "下载完成", FILE_PATH to downloadedFile.absolutePath, TITLE to downloadedFile.nameWithoutExtension, VIDEO_ID to videoId))
            Result.success(workDataOf(FILE_PATH to downloadedFile.absolutePath, TITLE to downloadedFile.nameWithoutExtension, VIDEO_ID to videoId))
        } catch (error: Throwable) {
            Log.e(TAG, "download failed", error)
            if (error is CancellationException || isStopped) {
                deps.jobDao().upsert(
                    ProcessingJobEntity(
                        id = jobId,
                        videoId = videoId,
                        currentStage = ProcessingStage.DOWNLOAD,
                        state = ProcessingState.CANCELLED,
                        progress = 0,
                        attemptCount = runAttemptCount,
                    ),
                )
                return Result.failure(workDataOf(STATUS to "下载已取消"))
            }
            val requiresYoutubeLogin = DownloadRequestPolicy.requiresYoutubeLogin(url, listOf(error))
            val shouldRetry = !requiresYoutubeLogin && runAttemptCount < MAX_BACKGROUND_RETRIES &&
                BackgroundRetryPolicy.isTransientNetworkFailure(error)
            deps.jobDao().upsert(
                ProcessingJobEntity(
                    id = jobId,
                    videoId = videoId,
                    currentStage = ProcessingStage.DOWNLOAD,
                    state = when {
                        requiresYoutubeLogin -> ProcessingState.WAITING_FOR_USER
                        shouldRetry -> ProcessingState.PENDING
                        else -> ProcessingState.FAILED
                    },
                    progress = 0,
                    attemptCount = runAttemptCount,
                    lastErrorCode = when {
                        requiresYoutubeLogin -> "YOUTUBE_LOGIN_REQUIRED"
                        shouldRetry -> "NETWORK_RETRY"
                        else -> null
                    },
                    lastErrorMessage = if (shouldRetry) "网络暂时不可用，任务将在后台自动重试" else error.message,
                ),
            )
            if (shouldRetry) return Result.retry()
            Result.failure(
                workDataOf(
                    STATUS to (error.message ?: "下载失败"),
                    REQUIRES_YOUTUBE_LOGIN to requiresYoutubeLogin,
                    LOGIN_TARGET_URL to url,
                ),
            )
        } finally {
            cancellationHandle?.dispose()
            cookieFile?.let { file ->
                if (file.exists() && !file.delete()) Log.w(TAG, "Unable to delete temporary cookie file")
            }
        }
    }

    private fun executeDownload(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): YoutubeDLResponse {
        var lastCallbackAt = 0L
        var lastPercent = -1
        val lastActivityAt = AtomicLong(android.os.SystemClock.elapsedRealtime())
        val timedOut = AtomicBoolean(false)
        activeProcessIds = activeProcessIds + processId
        val watchdog = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(WATCHDOG_POLL_INTERVAL_MS)
                    val idleFor = android.os.SystemClock.elapsedRealtime() - lastActivityAt.get()
                    if (idleFor >= DOWNLOAD_ATTEMPT_IDLE_TIMEOUT_MS) {
                        timedOut.set(true)
                        Log.w(TAG, "Download attempt timed out while idle: $processId")
                        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
                        break
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply {
            name = "sublingo-download-watchdog"
            isDaemon = true
            start()
        }
        return try {
            val response = YoutubeDL.getInstance().execute(request, processId) { progress: Float, etaSeconds: Long, line: String ->
                lastActivityAt.set(android.os.SystemClock.elapsedRealtime())
                val current = progress.toInt()
                val now = android.os.SystemClock.elapsedRealtime()
                if (current >= 0 && (
                        lastPercent < 0 ||
                            current >= lastPercent + PROGRESS_PERCENT_STEP ||
                            now - lastCallbackAt >= PROGRESS_UPDATE_INTERVAL_MS
                        )
                ) {
                    lastPercent = current
                    lastCallbackAt = now
                    runBlocking { onProgress(DownloadProgress(progress, etaSeconds, parseSpeed(line))) }
                }
            }
            if (timedOut.get()) {
                throw DownloadAttemptIdleTimeoutException()
            }
            response
        } finally {
            watchdog.interrupt()
            activeProcessIds = activeProcessIds - processId
        }
    }

    private fun destroyActiveProcesses() {
        activeProcessIds.forEach { processId ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        }
        activeProcessIds = emptySet()
    }

    private suspend fun updateDownloadProgress(
        jobId: String,
        rawProgress: Float,
        label: String,
        detail: DownloadProgress,
    ) {
        if (isStopped || !currentCoroutineContext().isActive) return
        val normalized = rawProgress.toInt().coerceIn(5, 94)
        val status = buildDownloadStatus(label, normalized, detail)
        setProgress(workDataOf(PROGRESS to normalized, STATUS to status))
        if (isStopped || !currentCoroutineContext().isActive) return
        setForeground(createForegroundInfo(normalized, status))
        deps.jobDao().upsert(
            ProcessingJobEntity(
                id = jobId,
                videoId = jobId.removePrefix("job-"),
                currentStage = ProcessingStage.DOWNLOAD,
                state = ProcessingState.RUNNING,
                progress = normalized,
            ),
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, "已排队，等待后台下载")
    }

    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        return BackgroundWorkNotifications.foregroundInfo(
            context = applicationContext,
            notificationId = notificationId(stableVideoId(inputData.getString(KEY_URL).orEmpty())),
            title = "SubLingo 视频下载",
            text = text,
            progress = progress,
            cancelIntent = cancelIntent,
        )
    }

    private fun notificationId(videoId: String): Int = 10_000 + (videoId.hashCode() and 0x3fff)

    private fun buildDownloadRequest(
        url: String,
        outputTemplate: String,
        attempt: DownloadAttempt,
        cookieFile: File?,
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--no-mtime")
        addOption("--continue")
        addOption("--no-update")
        addOption("--no-playlist")
        addOption("--restrict-filenames")
        // Bound stalled VPN/TLS connections so the worker can retry or report failure.
        addOption("--socket-timeout", SOCKET_TIMEOUT_SECONDS.toString())
        addOption("--retries", DOWNLOAD_RETRIES.toString())
        addOption("--fragment-retries", DOWNLOAD_RETRIES.toString())
        addOption("--write-info-json")
        addOption("-f", attempt.formatSelector)
        if (attempt.mergeToMp4) addOption("--merge-output-format", "mp4")
        if (attempt.forceIpv4) addOption("--force-ipv4")
        if (attempt.hlsConcurrentFragments > 1) {
            addOption("--hls-prefer-native")
            addOption("--concurrent-fragments", attempt.hlsConcurrentFragments.toString())
        }
        attempt.extractorArgs?.let { addOption("--extractor-args", it) }
        addOption("-o", outputTemplate)
        DownloadRequestPolicy.headers(url).forEach { (name, value) ->
            when (name) {
                "User-Agent" -> addOption("--user-agent", value)
                "Referer" -> addOption("--referer", value)
                else -> addOption("--add-header", "$name:$value")
            }
        }
        cookieFile?.let { addOption("--cookies", it.absolutePath) }
    }

    private fun createCookieFile(url: String, rawCookie: String): File {
        val directory = File(applicationContext.cacheDir, "download-cookies").apply { mkdirs() }
        return File(directory, "${stableVideoId(url)}-${id}.txt").also { target ->
            target.writeText(
                DownloadRequestPolicy.netscapeCookieFile(
                    cookieHeader = rawCookie,
                    domain = DownloadRequestPolicy.cookieDomain(url),
                ),
            )
        }
    }

    private fun clearAttemptArtifacts(taskDir: File) {
        taskDir.listFiles().orEmpty().forEach { artifact ->
            if (!artifact.deleteRecursively()) Log.w(TAG, "Unable to clear failed attempt artifact ${artifact.name}")
        }
    }

    private fun buildDownloadStatus(label: String, percent: Int, detail: DownloadProgress): String = buildString {
        append(label).append(" · ").append(percent).append('%')
        detail.speed?.let { append(" · ").append(it) }
        if (detail.etaSeconds > 0) append(" · 剩余 ").append(formatEta(detail.etaSeconds))
    }

    private fun parseSpeed(line: String?): String? = line
        ?.let { SPEED_REGEX.find(it)?.groupValues?.getOrNull(1) }
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun formatEta(seconds: Long): String = when {
        seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
        else -> "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun readLocalMetadata(file: File, outputDir: File, videoId: String): LocalMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val thumbnail = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumbnailFile = thumbnail?.let { bitmap ->
                File(outputDir, "$videoId-thumbnail.jpg").also { target ->
                    target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    bitmap.recycle()
                }
            }
            LocalMetadata(title, duration, thumbnailFile?.absolutePath, hasVideo, hasAudio)
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to read downloaded media metadata", error)
            LocalMetadata(null, 0L, null, false, false)
        } finally {
            retriever.release()
        }
    }

    private fun hasAudioTrack(file: File): Boolean {
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (_: Throwable) {
            false
        } finally {
            extractor.release()
        }
    }

    private fun readOriginalTitle(taskDir: File): String? {
        val infoFile = taskDir.listFiles()?.firstOrNull { it.name.endsWith(".info.json") } ?: return null
        return runCatching {
            Json.parseToJsonElement(infoFile.readText()).jsonObject["title"]?.jsonPrimitive?.contentOrNull
        }.onFailure { Log.w(TAG, "Unable to read yt-dlp metadata", it) }.getOrNull()
    }

    private fun stableVideoId(source: String): String {
        val hex = source.trim().lowercase().hashCode().toUInt().toString(16).take(8)
        return "video-$hex"
    }


    private data class LocalMetadata(
        val title: String?,
        val durationMs: Long,
        val thumbnailPath: String?,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
    )

    private data class DownloadProgress(
        val percent: Float,
        val etaSeconds: Long,
        val speed: String?,
    )

    private class DownloadAttemptIdleTimeoutException : IllegalStateException(
        "下载请求超时，请检查网络或 VPN 分流设置",
    )

    companion object {
        private const val TAG = "DownloadWorker"
        private const val MIN_MEDIA_BYTES = 64 * 1024L
        private const val MAX_BACKGROUND_RETRIES = 5
        private const val SOCKET_TIMEOUT_SECONDS = 30
        private const val DOWNLOAD_RETRIES = 2
        private const val WATCHDOG_POLL_INTERVAL_MS = 5_000L
        private const val DOWNLOAD_ATTEMPT_IDLE_TIMEOUT_MS = 90_000L
        private const val PROGRESS_PERCENT_STEP = 3
        private const val PROGRESS_UPDATE_INTERVAL_MS = 2_000L
        private val SPEED_REGEX = Regex("\\bat\\s+(.+?)\\s+ETA\\b", RegexOption.IGNORE_CASE)
        const val KEY_URL = "download_url"
        const val PROGRESS = "progress"
        const val STATUS = "status"
        const val FILE_PATH = "file_path"
        const val TITLE = "title"
        const val VIDEO_ID = "video_id"
        const val REQUIRES_YOUTUBE_LOGIN = "requires_youtube_login"
        const val LOGIN_TARGET_URL = "login_target_url"
        private const val DOWNLOAD_COOKIE_ALIAS = "download.cookie.default"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerDeps {
        fun videoRepository(): VideoRepository
        fun jobDao(): ProcessingJobDao
        fun subtitlePipelineScheduler(): SubtitlePipelineScheduler
        fun secretStore(): SecretStore
        fun youtubeDlRuntime(): YoutubeDlRuntime
    }
}
