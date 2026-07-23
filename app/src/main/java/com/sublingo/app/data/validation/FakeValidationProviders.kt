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

class FakeDownloadProbeProvider @Inject constructor() : DownloadProbeProvider {
    override suspend fun probe(url: String): DownloadProbeResult = DownloadProbeResult(
        source = url,
        success = true,
        canonicalUrl = url,
        title = "Fake title",
    )
}

class FakeSubtitleProbeProvider @Inject constructor() : SubtitleProbeProvider {
    override suspend fun probe(videoId: String): SubtitleProbeResult = SubtitleProbeResult(
        videoId = videoId,
        hasPlatformEnglishSubtitle = true,
        trackCount = 1,
        note = "Fake platform subtitle available",
    )
}

class FakeAsrProbeProvider @Inject constructor() : AsrProbeProvider {
    override suspend fun probe(audioBytes: ByteArray, chunkCount: Int): AsrProbeResult = AsrProbeResult(
        accepted = true,
        requestMode = if (audioBytes.size < 20_000_000) "base64" else "url",
        chunkCount = chunkCount,
        note = "Fake Doubao ASR accepted",
    )
}

class FakeTranslationAlignmentProbeProvider @Inject constructor() : TranslationAlignmentProbeProvider {
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
