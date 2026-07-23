package com.sublingo.app.domain.usecase

import com.sublingo.app.domain.provider.AsrProbeProvider
import com.sublingo.app.domain.provider.DownloadProbeProvider
import com.sublingo.app.domain.provider.SubtitleProbeProvider
import com.sublingo.app.domain.provider.TranslationAlignmentProbeProvider
import javax.inject.Inject

class RunDownloadProbeUseCase @Inject constructor(
    private val provider: DownloadProbeProvider,
) {
    suspend operator fun invoke(url: String) = provider.probe(url)
}

class RunSubtitleProbeUseCase @Inject constructor(
    private val provider: SubtitleProbeProvider,
) {
    suspend operator fun invoke(videoId: String) = provider.probe(videoId)
}

class RunAsrProbeUseCase @Inject constructor(
    private val provider: AsrProbeProvider,
) {
    suspend operator fun invoke(audioBytes: ByteArray, chunkCount: Int) = provider.probe(audioBytes, chunkCount)
}

class RunTranslationAlignmentProbeUseCase @Inject constructor(
    private val provider: TranslationAlignmentProbeProvider,
) {
    suspend operator fun invoke(indexes: List<Int>, translatedIndexes: List<Int>) = provider.probe(indexes, translatedIndexes)
}
