package com.sublingo.app.domain.model

data class DownloadProbeResult(
    val source: String,
    val success: Boolean,
    val canonicalUrl: String? = null,
    val title: String? = null,
    val failureReason: String? = null,
)

data class SubtitleProbeResult(
    val videoId: String,
    val hasPlatformEnglishSubtitle: Boolean,
    val trackCount: Int = 0,
    val note: String? = null,
)

data class AsrProbeResult(
    val accepted: Boolean,
    val requestMode: String,
    val chunkCount: Int,
    val note: String? = null,
)

data class TranslationAlignmentResult(
    val inputCount: Int,
    val outputCount: Int,
    val exactMatch: Boolean,
    val missingIndexes: List<Int> = emptyList(),
)
