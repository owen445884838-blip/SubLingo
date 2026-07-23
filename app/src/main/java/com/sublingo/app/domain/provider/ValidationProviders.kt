package com.sublingo.app.domain.provider

import com.sublingo.app.domain.model.AsrProbeResult
import com.sublingo.app.domain.model.DownloadProbeResult
import com.sublingo.app.domain.model.SubtitleProbeResult
import com.sublingo.app.domain.model.TranslationAlignmentResult

interface DownloadProbeProvider {
    suspend fun probe(url: String): DownloadProbeResult
}

interface SubtitleProbeProvider {
    suspend fun probe(videoId: String): SubtitleProbeResult
}

interface AsrProbeProvider {
    suspend fun probe(audioBytes: ByteArray, chunkCount: Int): AsrProbeResult
}

interface TranslationAlignmentProbeProvider {
    suspend fun probe(indexes: List<Int>, translatedIndexes: List<Int>): TranslationAlignmentResult
}
