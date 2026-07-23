package com.sublingo.app.data.validation

import com.sublingo.app.domain.model.AsrProbeResult
import com.sublingo.app.domain.model.DownloadProbeResult
import com.sublingo.app.domain.model.SubtitleProbeResult
import com.sublingo.app.domain.model.TranslationAlignmentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ValidationStateHolder @javax.inject.Inject constructor() {
    private val _downloadProbe = MutableStateFlow<DownloadProbeResult?>(null)
    private val _subtitleProbe = MutableStateFlow<SubtitleProbeResult?>(null)
    private val _asrProbe = MutableStateFlow<AsrProbeResult?>(null)
    private val _translationProbe = MutableStateFlow<TranslationAlignmentResult?>(null)

    val downloadProbe: StateFlow<DownloadProbeResult?> = _downloadProbe.asStateFlow()
    val subtitleProbe: StateFlow<SubtitleProbeResult?> = _subtitleProbe.asStateFlow()
    val asrProbe: StateFlow<AsrProbeResult?> = _asrProbe.asStateFlow()
    val translationProbe: StateFlow<TranslationAlignmentResult?> = _translationProbe.asStateFlow()

    fun setDownloadProbe(value: DownloadProbeResult?) {
        _downloadProbe.value = value
    }

    fun setSubtitleProbe(value: SubtitleProbeResult?) {
        _subtitleProbe.value = value
    }

    fun setAsrProbe(value: AsrProbeResult?) {
        _asrProbe.value = value
    }

    fun setTranslationProbe(value: TranslationAlignmentResult?) {
        _translationProbe.value = value
    }
}
