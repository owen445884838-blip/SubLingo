package com.sublingo.app.data.validation

import com.sublingo.app.domain.model.AsrProbeResult
import com.sublingo.app.domain.model.DownloadProbeResult
import com.sublingo.app.domain.model.SubtitleProbeResult
import com.sublingo.app.domain.model.TranslationAlignmentResult
import com.sublingo.app.domain.provider.AsrProbeProvider
import com.sublingo.app.domain.provider.DownloadProbeProvider
import com.sublingo.app.domain.provider.SubtitleProbeProvider
import com.sublingo.app.domain.provider.TranslationAlignmentProbeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeuristicDownloadProbeProvider @Inject constructor() : DownloadProbeProvider {
    override suspend fun probe(url: String): DownloadProbeResult {
        val lower = url.lowercase()
        val success = lower.contains("youtube") || lower.contains("bilibili") || lower.contains("xiaohongshu") || lower.contains("xhs")
        return DownloadProbeResult(
            source = url,
            success = success,
            canonicalUrl = if (success) url else null,
            title = if (success) "Heuristic detected media source" else null,
            failureReason = if (success) null else "Unsupported or unrecognized host",
        )
    }
}

@Singleton
class HeuristicSubtitleProbeProvider @Inject constructor() : SubtitleProbeProvider {
    override suspend fun probe(videoId: String): SubtitleProbeResult = SubtitleProbeResult(
        videoId = videoId,
        hasPlatformEnglishSubtitle = videoId.isNotBlank(),
        trackCount = if (videoId.isNotBlank()) 1 else 0,
        note = if (videoId.isNotBlank()) "Platform subtitle likely available for validation" else "No video id supplied",
    )
}

@Singleton
class HeuristicAsrProbeProvider @Inject constructor() : AsrProbeProvider {
    override suspend fun probe(audioBytes: ByteArray, chunkCount: Int): AsrProbeResult = AsrProbeResult(
        accepted = audioBytes.isNotEmpty(),
        requestMode = if (audioBytes.size <= 20_000_000) "base64" else "url",
        chunkCount = chunkCount.coerceAtLeast(1),
        note = if (audioBytes.isNotEmpty()) "Payload accepted for M-1 validation" else "Empty payload rejected",
    )
}

@Singleton
class HeuristicTranslationAlignmentProbeProvider @Inject constructor() : TranslationAlignmentProbeProvider {
    override suspend fun probe(indexes: List<Int>, translatedIndexes: List<Int>): TranslationAlignmentResult {
        val missing = indexes.filterNot(translatedIndexes::contains)
        return TranslationAlignmentResult(
            inputCount = indexes.size,
            outputCount = translatedIndexes.size,
            exactMatch = missing.isEmpty() && indexes.size == translatedIndexes.size,
            missingIndexes = missing,
        )
    }
}
