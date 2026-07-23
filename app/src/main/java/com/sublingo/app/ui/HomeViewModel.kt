package com.sublingo.app.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublingo.app.data.db.ProcessingJobEntity
import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.domain.model.DownloadRequest
import com.sublingo.app.domain.model.ProcessingState
import com.sublingo.app.domain.provider.TaskFlowProvider
import com.sublingo.app.domain.repository.ProcessingJobRepository
import com.sublingo.app.domain.repository.VideoRepository
import com.sublingo.app.data.media.SubtitlePipelineScheduler
import com.sublingo.app.data.media.DownloadRequestPolicy
import com.sublingo.app.data.media.YoutubeLoginCookiePolicy
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class HomeTaskUi(
    val job: ProcessingJobEntity,
    val title: String,
    val hasMedia: Boolean,
    val sourceUrl: String,
    val downloadQueuePosition: Int? = null,
)

data class HomeUiState(
    val url: String = "",
    val activeTasks: List<HomeTaskUi> = emptyList(),
    val recentVideos: List<VideoEntity> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedVideoIds: Set<String> = emptySet(),
    val youtubeLoginRequired: Boolean = false,
    val youtubeLoginUrl: String = "",
    val loginStatus: String = "",
    val taskActionStatus: Map<String, String> = emptyMap(),
    val localImportStatus: String = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskFlowProvider: TaskFlowProvider,
    private val videoRepository: VideoRepository,
    private val processingJobRepository: ProcessingJobRepository,
    private val subtitlePipelineScheduler: SubtitlePipelineScheduler,
    private val secretStore: SecretStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val reconciledVideoIds = mutableSetOf<String>()
    private val promptedYoutubeLoginVideoIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(videoRepository.observeVideos(), processingJobRepository.observeJobs()) { videos, jobs ->
                val videosById = videos.associateBy(VideoEntity::id)
                jobs.filter { job ->
                        val video = videosById[job.videoId]
                        !video?.filePath.isNullOrBlank() &&
                        job.state == ProcessingState.SUCCEEDED &&
                        job.currentStage in setOf(ProcessingStage.DOWNLOAD, ProcessingStage.TRANSLATION)
                }.forEach { job ->
                    if (reconciledVideoIds.add(job.videoId)) {
                        subtitlePipelineScheduler.enqueue(job.videoId, job.id)
                    }
                }
                val visibleJobs = jobs
                    .filter { job ->
                        job.state in setOf(
                            ProcessingState.PENDING,
                            ProcessingState.RUNNING,
                            ProcessingState.FAILED,
                            ProcessingState.WAITING_FOR_USER,
                        ) || (job.state == ProcessingState.SUCCEEDED && job.currentStage != ProcessingStage.VOCABULARY)
                    }
                    .distinctBy { it.videoId }
                val queuedDownloads = visibleJobs
                    .filter { it.currentStage == ProcessingStage.DOWNLOAD && it.state == ProcessingState.PENDING }
                    .sortedBy { it.createdAt }
                val queuePositionByJobId = queuedDownloads.mapIndexed { index, job -> job.id to index + 1 }.toMap()
                val activeTasks = visibleJobs
                    .map { job ->
                        val video = videosById[job.videoId]
                        HomeTaskUi(
                            job = job,
                            title = video?.title ?: "视频处理任务",
                            hasMedia = !video?.filePath.isNullOrBlank(),
                            sourceUrl = video?.originalUrl.orEmpty(),
                            downloadQueuePosition = queuePositionByJobId[job.id],
                        )
                    }
                HomeUiState(
                    url = _uiState.value.url,
                    activeTasks = activeTasks,
                    // Keep active downloads in the grid as stable placeholder cards. The same Room
                    // video row receives its file, thumbnail and title after validation, allowing
                    // the placeholder to turn into the real library card without a list jump.
                    recentVideos = videos.filter { video ->
                        !video.filePath.isNullOrBlank() || activeTasks.any { it.job.videoId == video.id }
                    },
                    selectionMode = _uiState.value.selectionMode,
                    selectedVideoIds = _uiState.value.selectedVideoIds,
                    youtubeLoginRequired = _uiState.value.youtubeLoginRequired,
                    youtubeLoginUrl = _uiState.value.youtubeLoginUrl,
                    loginStatus = _uiState.value.loginStatus,
                    taskActionStatus = _uiState.value.taskActionStatus,
                    localImportStatus = _uiState.value.localImportStatus,
                )
            }.collect { next ->
                val loginTask = next.activeTasks.firstOrNull {
                    it.job.state == ProcessingState.WAITING_FOR_USER &&
                        it.job.lastErrorCode == "YOUTUBE_LOGIN_REQUIRED" &&
                        DownloadRequestPolicy.site(it.sourceUrl) == com.sublingo.app.data.media.DownloadSite.YOUTUBE
                }
                _uiState.value = if (
                    loginTask != null && promptedYoutubeLoginVideoIds.add(loginTask.job.videoId)
                ) {
                    next.copy(
                        youtubeLoginRequired = true,
                        youtubeLoginUrl = loginTask.sourceUrl,
                        loginStatus = "请登录 YouTube，成功后会自动继续下载",
                    )
                } else next
            }
        }
    }

    fun updateUrl(value: String) {
        _uiState.value = _uiState.value.copy(url = value)
    }

    fun createTask() {
        val url = uiState.value.url.trim()
        if (url.isBlank()) return
        viewModelScope.launch { taskFlowProvider.createOrResume(DownloadRequest(url = url)) }
    }

    fun importLocal(uri: Uri) {
        viewModelScope.launch {
            var importDirectory: File? = null
            runCatching {
                _uiState.value = _uiState.value.copy(localImportStatus = "正在导入本地视频")
                val videoId = "local-${UUID.randomUUID()}"
                val directory = File(context.getExternalFilesDir(null), "imports/$videoId").apply { mkdirs() }
                importDirectory = directory
                val pending = File(directory, "media.mp4.part")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取所选文件" }
                    pending.outputStream().use(input::copyTo)
                }
                require(pending.length() > 64 * 1024L) { "所选文件过小或为空" }
                val media = File(directory, "media.mp4")
                check(pending.renameTo(media)) { "无法提交导入文件" }
                val retriever = MediaMetadataRetriever()
                val metadata = try {
                    retriever.setDataSource(media.absolutePath)
                    require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                        "所选文件不包含视频轨道"
                    }
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val embeddedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val frameTimeUs = (duration.coerceAtMost(3_000L) / 2L) * 1_000L
                    val frame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    val thumbnailPath = frame?.let { bitmap ->
                        val thumbnail = File(directory, "$videoId-thumbnail.jpg")
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
                } finally {
                    retriever.release()
                }
                val durationMs = metadata.first
                require(durationMs > 0L) { "无法识别视频时长" }
                val title = metadata.second?.takeIf(String::isNotBlank)
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?.takeIf(String::isNotBlank) ?: "本地视频"
                videoRepository.upsertVideo(
                    VideoEntity(
                        id = videoId,
                        source = "local",
                        title = title,
                        thumbnail = metadata.third,
                        filePath = media.absolutePath,
                        durationMs = durationMs,
                        fileSize = media.length(),
                    ),
                )
                val jobId = "job-$videoId"
                processingJobRepository.upsertJob(ProcessingJobEntity(jobId, videoId))
                subtitlePipelineScheduler.enqueue(videoId, jobId)
                _uiState.value = _uiState.value.copy(localImportStatus = "本地视频已导入，正在生成学习内容")
            }.onFailure { error ->
                importDirectory?.deleteRecursively()
                _uiState.value = _uiState.value.copy(localImportStatus = error.message ?: "本地视频导入失败")
            }
        }
    }

    fun requestYoutubeLogin(task: HomeTaskUi) {
        if (DownloadRequestPolicy.site(task.sourceUrl) == com.sublingo.app.data.media.DownloadSite.YOUTUBE) {
            _uiState.value = _uiState.value.copy(
                youtubeLoginRequired = true,
                youtubeLoginUrl = task.sourceUrl,
                loginStatus = "请登录 YouTube，成功后会自动继续下载",
            )
        }
    }

    fun completeYoutubeLogin(cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        viewModelScope.launch {
            runCatching { secretStore.save(COOKIE_SECRET_ALIAS, cookieHeader) }
                .onSuccess {
                    val retryUrl = uiState.value.youtubeLoginUrl
                    _uiState.value = _uiState.value.copy(
                        url = retryUrl,
                        youtubeLoginRequired = false,
                        loginStatus = "登录成功，正在自动重试下载",
                    )
                    taskFlowProvider.createOrResume(DownloadRequest(url = retryUrl))
                }
                .onFailure { error ->
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

    fun refreshYoutubeSession(task: HomeTaskUi) {
        viewModelScope.launch {
            val cookie = secretStore.read(COOKIE_SECRET_ALIAS).orEmpty()
            if (YoutubeLoginCookiePolicy.hasAuthenticatedSession(cookie)) {
                _uiState.value = _uiState.value.copy(
                    url = task.sourceUrl,
                    taskActionStatus = _uiState.value.taskActionStatus +
                        (task.job.videoId to "已检测到 YouTube 登录状态，正在重试"),
                )
                taskFlowProvider.createOrResume(DownloadRequest(url = task.sourceUrl))
            } else {
                _uiState.value = _uiState.value.copy(
                    taskActionStatus = _uiState.value.taskActionStatus +
                        (task.job.videoId to "尚未检测到有效登录状态，请先登录 YouTube"),
                )
            }
        }
    }

    fun cancelTask(task: HomeTaskUi) {
        if (task.job.currentStage == ProcessingStage.DOWNLOAD && task.sourceUrl.isNotBlank()) {
            taskFlowProvider.cancel(task.sourceUrl)
        } else {
            subtitlePipelineScheduler.cancel(task.job.videoId)
            viewModelScope.launch {
                processingJobRepository.upsertJob(
                    task.job.copy(
                        state = ProcessingState.CANCELLED,
                        progress = 0,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (_uiState.value.youtubeLoginUrl == task.sourceUrl) {
            _uiState.value = _uiState.value.copy(youtubeLoginRequired = false, youtubeLoginUrl = "")
        }
    }

    fun toggleSelectionMode() {
        _uiState.value = _uiState.value.copy(
            selectionMode = !_uiState.value.selectionMode,
            selectedVideoIds = emptySet(),
        )
    }

    fun toggleVideoSelection(videoId: String) {
        val selected = _uiState.value.selectedVideoIds.toMutableSet()
        if (!selected.add(videoId)) selected.remove(videoId)
        _uiState.value = _uiState.value.copy(selectedVideoIds = selected)
    }

    fun deleteSelectedVideos() {
        val selected = _uiState.value.selectedVideoIds
        if (selected.isEmpty()) return
        val videos = _uiState.value.recentVideos.filter { it.id in selected }
        viewModelScope.launch {
            videos.forEach { video ->
                val files: List<File> = listOfNotNull(video.filePath, video.thumbnail).map(::File)
                files.forEach { file -> runCatching { file.delete() } }
                files.mapNotNull { file -> file.parentFile }.distinct().forEach { directory ->
                    // Media and thumbnails are stored in a dedicated per-video directory.
                    // Remove it only when it is one of SubLingo's known scoped locations.
                    if (directory.name == video.id && directory.parentFile?.name in setOf("downloads", "imports")) {
                        runCatching { directory.deleteRecursively() }
                    }
                }
            }
            videoRepository.deleteVideos(selected.toList())
            _uiState.value = _uiState.value.copy(selectionMode = false, selectedVideoIds = emptySet())
        }
    }

    fun retrySubtitlePipeline(videoId: String) {
        viewModelScope.launch {
            val jobId = "job-$videoId"
            val job = processingJobRepository.observeByVideoId(videoId).first()
            if (job != null) {
                processingJobRepository.upsertJob(
                    job.copy(
                        currentStage = ProcessingStage.AUDIO_EXTRACTION,
                        state = ProcessingState.PENDING,
                        progress = 0,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                subtitlePipelineScheduler.enqueue(videoId, job.id)
            } else {
                val newJob = ProcessingJobEntity(jobId, videoId, ProcessingStage.AUDIO_EXTRACTION, ProcessingState.PENDING)
                processingJobRepository.upsertJob(newJob)
                subtitlePipelineScheduler.enqueue(videoId, jobId)
            }
        }
    }

    private companion object {
        const val COOKIE_SECRET_ALIAS = "download.cookie.default"
    }
}
