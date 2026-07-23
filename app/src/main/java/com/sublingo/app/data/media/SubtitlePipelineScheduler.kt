package com.sublingo.app.data.media

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit
import com.sublingo.app.worker.ExtractAudioWorker
import com.sublingo.app.worker.PipelineWorker
import com.sublingo.app.worker.TranscribeWorker
import com.sublingo.app.worker.TranslateWorker
import com.sublingo.app.worker.VocabWorker
import com.sublingo.app.data.vocabulary.VocabularyPipelineContract
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitlePipelineScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun enqueue(videoId: String, jobId: String) {
        val data = workDataOf(
            PipelineWorker.VIDEO_ID to videoId,
            PipelineWorker.JOB_ID to jobId,
            PipelineWorker.NOTIFY_ON_COMPLETION to true,
        )
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        workManager.beginUniqueWork(
            "subtitle-$videoId",
            ExistingWorkPolicy.KEEP,
            request<ExtractAudioWorker>(data, network),
        )
            .then(request<TranscribeWorker>(data, network))
            .then(request<TranslateWorker>(data, network))
            .then(request<VocabWorker>(data, network))
            .enqueue()
    }

    fun enqueueVocabularyRefresh(videoId: String, jobId: String) {
        val data = workDataOf(
            PipelineWorker.VIDEO_ID to videoId,
            PipelineWorker.JOB_ID to jobId,
            PipelineWorker.NOTIFY_ON_COMPLETION to false,
        )
        workManager.beginUniqueWork(
            "${VocabularyPipelineContract.REFRESH_WORK_PREFIX}-$videoId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<VocabWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        ).enqueue()
    }

    fun cancel(videoId: String) {
        workManager.cancelUniqueWork("subtitle-$videoId")
        workManager.cancelUniqueWork("${VocabularyPipelineContract.REFRESH_WORK_PREFIX}-$videoId")
    }

    private inline fun <reified T : androidx.work.ListenableWorker> request(
        data: androidx.work.Data,
        constraints: Constraints,
    ) = OneTimeWorkRequestBuilder<T>()
        .setInputData(data)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .addTag("sublingo-pipeline-${data.getString(PipelineWorker.VIDEO_ID).orEmpty()}")
        .build()
}
