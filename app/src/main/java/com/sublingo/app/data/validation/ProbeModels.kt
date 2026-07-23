package com.sublingo.app.data.validation

import com.sublingo.app.domain.model.AsrProbeResult
import com.sublingo.app.domain.model.DownloadProbeResult
import com.sublingo.app.domain.model.SubtitleProbeResult
import com.sublingo.app.domain.model.TranslationAlignmentResult

object ProbeSamples {
    val download = DownloadProbeResult(
        source = "https://www.youtube.com/watch?v=fake",
        success = true,
        canonicalUrl = "https://www.youtube.com/watch?v=fake",
        title = "YouTube sample",
    )

    val subtitle = SubtitleProbeResult(
        videoId = "fake-video-id",
        hasPlatformEnglishSubtitle = true,
        trackCount = 1,
        note = "Platform English subtitle detected",
    )

    val asr = AsrProbeResult(
        accepted = true,
        requestMode = "base64",
        chunkCount = 3,
        note = "Audio accepted for validation",
    )

    val translation = TranslationAlignmentResult(
        inputCount = 3,
        outputCount = 3,
        exactMatch = true,
        missingIndexes = emptyList(),
    )
}
