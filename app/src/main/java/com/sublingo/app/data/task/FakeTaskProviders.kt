package com.sublingo.app.data.task

import com.sublingo.app.domain.model.DownloadPlan
import com.sublingo.app.domain.model.DownloadRequest
import com.sublingo.app.domain.provider.TaskPlannerProvider
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeTaskPlannerProvider @Inject constructor() : TaskPlannerProvider {
    override suspend fun plan(request: DownloadRequest): DownloadPlan {
        val isLocal = request.importLocalFilePath != null
        val hasUrl = request.url.isNotBlank()
        val host = request.url.takeIf { hasUrl }?.let { runCatching { URI(it).host.orEmpty() }.getOrDefault("") }.orEmpty()
        return DownloadPlan(
            source = request.url.ifBlank { request.importLocalFilePath.orEmpty() },
            canonicalUrl = when {
                hasUrl -> request.url
                isLocal -> "file://${request.importLocalFilePath.orEmpty()}"
                else -> ""
            },
            title = when {
                isLocal -> "本地视频导入"
                host.contains("bilibili", ignoreCase = true) -> "Bilibili 视频下载"
                host.contains("youtube", ignoreCase = true) -> "YouTube 视频下载"
                hasUrl -> "网页视频下载"
                else -> "等待输入来源"
            },
            sourceType = when {
                isLocal -> "local"
                hasUrl -> "remote"
                else -> "empty"
            },
            estimatedMinutes = when {
                request.onlyAudio -> 1
                hasUrl || isLocal -> 5
                else -> null
            },
            nextStepLabel = if (hasUrl || isLocal) "点击创建 / 恢复任务" else "请先输入 URL 或本地路径",
        )
    }
}
