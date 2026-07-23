package com.sublingo.app.data.settings

import com.sublingo.app.domain.model.ProviderConfig

object ProviderRegistry {
    val defaults: List<ProviderConfig> = listOf(
        ProviderPreset.deepSeek,
        ProviderPreset.doubaoAsr,
    )

    val llmPresets: List<LlmProviderPreset> = LlmProviderPresets.all
    val sttPresets: List<SttProviderPreset> = SttProviderPresets.all
}
