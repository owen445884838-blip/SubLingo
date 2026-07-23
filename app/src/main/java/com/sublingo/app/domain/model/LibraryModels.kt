package com.sublingo.app.domain.model

data class VideoSummary(
    val id: String,
    val title: String,
    val source: String,
    val durationMs: Long,
    val filePath: String?,
    val progressLabel: String,
)

data class PlayerRequest(
    val videoId: String,
    val startPositionMs: Long = 0L,
)
