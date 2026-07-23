package com.sublingo.app.domain.model

data class DownloadRequest(
    val url: String,
    val importLocalFilePath: String? = null,
    val onlyAudio: Boolean = false,
)

data class DownloadPlan(
    val source: String,
    val canonicalUrl: String,
    val title: String,
    val sourceType: String,
    val estimatedMinutes: Int? = null,
    val nextStepLabel: String = "等待执行",
)

data class TaskTransitionResult(
    val success: Boolean,
    val videoId: String,
    val jobId: String,
    val currentStage: String,
    val message: String,
    val progress: Int,
    val progressLabel: String,
)

data class DownloadSession(
    val videoId: String,
    val jobId: String,
    val title: String,
    val sourceUrl: String,
    val canonicalUrl: String,
    val coverUrl: String,
    val downloadUrl: String? = null,
    val outputPath: String? = null,
)
