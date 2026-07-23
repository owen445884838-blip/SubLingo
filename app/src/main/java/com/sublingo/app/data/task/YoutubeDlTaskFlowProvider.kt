package com.sublingo.app.data.task

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.await
import java.util.concurrent.TimeUnit
import com.sublingo.app.domain.model.DownloadRequest
import com.sublingo.app.domain.model.TaskTransitionResult
import com.sublingo.app.domain.provider.TaskFlowProvider
import com.sublingo.app.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.sublingo.app.data.db.ProcessingJobDao
import com.sublingo.app.data.db.VideoDao
import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.data.db.ProcessingJobEntity
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.domain.model.ProcessingState
import kotlinx.coroutines.launch

@Singleton
class YoutubeDlTaskFlowProvider @Inject constructor(
    private val workManager: WorkManager,
    private val jobDao: ProcessingJobDao,
    private val videoDao: VideoDao,
) : TaskFlowProvider {
    override suspend fun createOrResume(request: DownloadRequest): TaskTransitionResult {
        if (request.url.isBlank()) {
            return TaskTransitionResult(false, "", "", "IDLE", "缺少视频链接", 0, "未启动")
        }
        val uniqueName = uniqueName(request.url)
        val videoId = stableVideoId(request.url)
        val jobId = "job-$videoId"
        val previousVideo = videoDao.getById(videoId)
        videoDao.upsert(
            (previousVideo ?: VideoEntity(id = videoId)).copy(
                originalUrl = request.url,
                canonicalUrl = request.url,
                source = "youtubedl-android",
                title = previousVideo?.title?.takeUnless { it == "等待下载" } ?: "等待下载",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val previousJob = jobDao.getById(jobId)
        jobDao.upsert(
            (previousJob ?: ProcessingJobEntity(jobId, videoId)).copy(
                currentStage = ProcessingStage.DOWNLOAD,
                state = ProcessingState.PENDING,
                progress = 0,
                lastErrorCode = null,
                lastErrorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_URL to request.url))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(uniqueName)
            .addTag(DOWNLOAD_QUEUE_TAG)
            .build()
        // APPEND_OR_REPLACE is not appropriate here because every URL has its own independent
        // unique chain. REPLACE guarantees a stale/backed-off WorkSpec cannot keep a user retry
        // waiting, while the enqueue operation itself is observed so scheduling failures surface.
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workRequest).await()
        return TaskTransitionResult(
            success = true,
            videoId = videoId,
            jobId = jobId,
            currentStage = "DOWNLOAD",
            message = "已提交到 youtubedl-android 下载队列",
            progress = 0,
            progressLabel = "已排队，等待后台执行",
        )
    }

    override fun observeProgress(url: String): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkFlow(uniqueName(url))
    }

    override fun cancel(url: String) {
        workManager.cancelUniqueWork(uniqueName(url))
        val videoId = stableVideoId(url)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val job = jobDao.getById("job-$videoId")
            if (job != null) jobDao.upsert(job.copy(state = ProcessingState.CANCELLED, progress = 0, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun uniqueName(url: String): String {
        val hex = url.trim().lowercase().hashCode().toUInt().toString(16).take(8)
        return "youtube-dl-$hex"
    }

    private fun stableVideoId(url: String): String = "video-${url.trim().lowercase().hashCode().toUInt().toString(16).take(8)}"

    private companion object { const val DOWNLOAD_QUEUE_TAG = "sublingo-download-queue" }
}
