package com.sublingo.app.domain.provider

import androidx.work.WorkInfo
import com.sublingo.app.domain.model.DownloadPlan
import com.sublingo.app.domain.model.DownloadRequest
import com.sublingo.app.domain.model.TaskTransitionResult
import kotlinx.coroutines.flow.Flow

interface TaskPlannerProvider {
    suspend fun plan(request: DownloadRequest): DownloadPlan
}

interface TaskFlowProvider {
    suspend fun createOrResume(request: DownloadRequest): TaskTransitionResult
    fun observeProgress(url: String): Flow<List<WorkInfo>>
    fun cancel(url: String)
}
