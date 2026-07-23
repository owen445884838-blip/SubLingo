package com.sublingo.app.ui.download

import androidx.lifecycle.ViewModel
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import com.sublingo.app.data.media.SubtitlePipelineScheduler
import com.sublingo.app.data.db.ProcessingJobEntity
import com.sublingo.app.domain.repository.ProcessingJobRepository
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.domain.model.DownloadPlan
import com.sublingo.app.domain.model.DownloadRequest
import com.sublingo.app.domain.model.TaskTransitionResult
import com.sublingo.app.domain.provider.TaskFlowProvider
import com.sublingo.app.domain.provider.TaskPlannerProvider
import com.sublingo.app.domain.repository.VideoRepository
import com.sublingo.app.security.SecretStore
import com.sublingo.app.work.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadUiState(
    val url: String = "",
    val localPath: String = "",
    val onlyAudio: Boolean = false,
    val plan: DownloadPlan? = null,
    val result: TaskTransitionResult? = null,
    val status: String = "等待输入",
    val progress: Int = 0,
    val progressLabel: String = "未开始",
    val workerState: String = "未入队",
    val latestVideo: VideoEntity? = null,
    val youtubeLoginRequired: Boolean = false,
    val youtubeLoginUrl: String = "",
    val loginStatus: String = "",
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val taskPlannerProvider: TaskPlannerProvider,
    private val taskFlowProvider: TaskFlowProvider,
    private val videoRepository: VideoRepository,
    @ApplicationContext private val context: Context,
    private val subtitlePipelineScheduler: SubtitlePipelineScheduler,
    private val processingJobs: ProcessingJobRepository,
    private val secretStore: SecretStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            videoRepository.observeVideos().collectLatest { videos ->
                _uiState.value = _uiState.value.copy(latestVideo = videos.firstOrNull())
            }
        }
    }

    fun updateUrl(value: String) { _uiState.value = _uiState.value.copy(url = value) }
    fun updateLocalPath(value: String) { _uiState.value = _uiState.value.copy(localPath = value) }
    fun toggleOnlyAudio() { _uiState.value = _uiState.value.copy(onlyAudio = !_uiState.value.onlyAudio) }

    fun planTask() {
        viewModelScope.launch {
            val request = DownloadRequest(url = uiState.value.url, importLocalFilePath = uiState.value.localPath.ifBlank { null }, onlyAudio = uiState.value.onlyAudio)
            val plan = taskPlannerProvider.plan(request)
            _uiState.value = _uiState.value.copy(plan = plan, status = if (plan.sourceType == "empty") "请先输入 URL 或本地路径" else "任务计划已生成", progress = if (plan.sourceType == "empty") 0 else 12, progressLabel = plan.nextStepLabel)
        }
    }

    fun createOrResume() {
        viewModelScope.launch {
            val request = DownloadRequest(url = uiState.value.url, importLocalFilePath = uiState.value.localPath.ifBlank { null }, onlyAudio = uiState.value.onlyAudio)
            startObserve(request.url)
            val result = taskFlowProvider.createOrResume(request)
            _uiState.value = _uiState.value.copy(result = result, status = if (result.success) "任务已创建/恢复" else "任务创建失败", progress = result.progress, progressLabel = result.progressLabel, workerState = if (result.success) "已提交" else "失败")
        }
    }

    private fun startObserve(url: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            taskFlowProvider.observeProgress(url).collectLatest { list ->
                val info = list.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    ?: list.lastOrNull()
                    ?: return@collectLatest
                val state = when (info.state) {
                    WorkInfo.State.ENQUEUED -> "已排队"
                    WorkInfo.State.RUNNING -> "下载中"
                    WorkInfo.State.SUCCEEDED -> "下载完成"
                    WorkInfo.State.FAILED -> "下载失败"
                    WorkInfo.State.CANCELLED -> "已取消"
                    WorkInfo.State.BLOCKED -> "被阻塞"
                }
                val progress = info.progress.getInt(com.sublingo.app.work.DownloadWorker.PROGRESS, uiState.value.progress)
                val terminalData = info.outputData
                val label = terminalData.getString(DownloadWorker.STATUS)
                    ?: info.progress.getString(DownloadWorker.STATUS)
                    ?: uiState.value.progressLabel
                val needsLogin = info.state == WorkInfo.State.FAILED &&
                    terminalData.getBoolean(DownloadWorker.REQUIRES_YOUTUBE_LOGIN, false)
                _uiState.value = _uiState.value.copy(
                    workerState = if (needsLogin) "等待登录" else state,
                    progress = progress,
                    progressLabel = label,
                    status = if (needsLogin) "需要登录 YouTube" else state,
                    youtubeLoginRequired = needsLogin,
                    youtubeLoginUrl = terminalData.getString(DownloadWorker.LOGIN_TARGET_URL)
                        ?.takeIf(String::isNotBlank) ?: uiState.value.url,
                    loginStatus = if (needsLogin) "请登录 YouTube，成功后会自动继续下载" else uiState.value.loginStatus,
                )
            }
        }
    }

    fun completeYoutubeLogin(cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        viewModelScope.launch {
            runCatching {
                secretStore.save(COOKIE_SECRET_ALIAS, cookieHeader)
            }.onSuccess {
                val retryUrl = uiState.value.youtubeLoginUrl.ifBlank { uiState.value.url }
                _uiState.value = _uiState.value.copy(
                    url = retryUrl,
                    youtubeLoginRequired = false,
                    loginStatus = "登录成功，正在自动重试下载",
                    status = "登录成功",
                    workerState = "正在重试",
                    progress = 0,
                    progressLabel = "正在使用登录会话重新排队",
                )
                createOrResume()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loginStatus = error.message ?: "无法安全保存登录会话")
            }
        }
    }

    fun dismissYoutubeLogin() {
        _uiState.value = _uiState.value.copy(
            youtubeLoginRequired = false,
            loginStatus = "已取消登录，下载任务仍在等待",
        )
    }

    fun cancelTask() {
        viewModelScope.launch {
            taskFlowProvider.cancel(uiState.value.url)
            _uiState.value = _uiState.value.copy(status = "任务已取消", workerState = "已取消")
        }
    }

    fun importLocal(uri: Uri) {
        viewModelScope.launch {
            var importDirectory: File? = null
            runCatching {
                val id = "local-${UUID.randomUUID()}"
                val directory = File(context.getExternalFilesDir(null), "imports/$id").apply { mkdirs() }
                importDirectory = directory
                val target = File(directory, "media.mp4.part")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取所选文件" }
                    target.outputStream().use(input::copyTo)
                }
                require(target.length() > 64 * 1024) { "所选文件过小或为空" }
                val committed = File(directory, "media.mp4")
                check(target.renameTo(committed)) { "无法提交导入文件" }
                val retriever = MediaMetadataRetriever()
                val metadata = try {
                    retriever.setDataSource(committed.absolutePath)
                    require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "所选文件不包含视频轨道" }
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val embeddedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val frameTimeUs = (duration.coerceAtMost(3_000L) / 2L) * 1_000L
                    val frame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    val thumbnailPath = frame?.let { bitmap ->
                        val thumbnail = File(directory, "$id-thumbnail.jpg")
                        try {
                            thumbnail.outputStream().use { output ->
                                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                                    "无法生成视频封面"
                                }
                            }
                            thumbnail.absolutePath
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    Triple(duration, embeddedTitle, thumbnailPath)
                } finally { retriever.release() }
                val duration = metadata.first
                require(duration > 0) { "无法识别视频时长" }
                videoRepository.upsertVideo(
                    VideoEntity(
                        id = id,
                        source = "local",
                        title = metadata.second?.takeIf(String::isNotBlank)
                            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                            ?: "本地视频",
                        thumbnail = metadata.third,
                        filePath = committed.absolutePath,
                        durationMs = duration,
                        fileSize = committed.length(),
                    ),
                )
                val jobId = "job-$id"
                processingJobs.upsertJob(ProcessingJobEntity(jobId, id))
                subtitlePipelineScheduler.enqueue(id, jobId)
                _uiState.value = _uiState.value.copy(status = "本地导入完成", progress = 100, progressLabel = "已加入媒体库", workerState = "导入完成")
            }.onFailure { error ->
                importDirectory?.deleteRecursively()
                _uiState.value = _uiState.value.copy(status = "本地导入失败", progressLabel = error.message ?: "无法导入文件", workerState = "失败")
            }
        }
    }

    private companion object {
        const val COOKIE_SECRET_ALIAS = "download.cookie.default"
    }

}
