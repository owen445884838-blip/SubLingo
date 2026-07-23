package com.sublingo.app.data.settings

import com.sublingo.app.data.db.ProviderProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProviderPresetsTest {
    @Test fun exposesDeepSeekDoubaoAndCustomProvider() {
        assertEquals(
            listOf(LlmPresetId.DEEPSEEK, LlmPresetId.DOUBAO, LlmPresetId.CUSTOM),
            LlmProviderPresets.all.map { it.id },
        )
        assertEquals("https://api.deepseek.com/v1", LlmProviderPresets.deepSeek.baseUrl)
        assertEquals("https://ark.cn-beijing.volces.com/api/v3", LlmProviderPresets.doubao.baseUrl)
        assertTrue(LlmProviderPresets.all.take(2).all { it.apiKeyUrl?.startsWith("https://") == true })
    }

    @Test fun storageIdsRoundTripAndUnknownLegacyProviderBecomesCustom() {
        LlmPresetId.entries.forEach { preset ->
            assertEquals(preset, LlmPresetId.fromStorageId(preset.storageId))
        }
        assertEquals(LlmPresetId.CUSTOM, LlmPresetId.fromStorageId("another-provider"))
        assertEquals(LlmPresetId.CUSTOM, LlmPresetId.fromStorageId("xiaomi-mimo"))
    }

    @Test fun normalizesTrailingSlashWithoutRemovingApiVersionPath() {
        assertEquals("https://example.com/openai/v1", normalizeLlmBaseUrl(" https://example.com/openai/v1/// "))
    }

    @Test fun validatesCustomOpenAiCompatibleConfiguration() {
        validateLlmProvider("My LLM", "https://example.com/openai/v1", "model-a")

        assertThrows(IllegalArgumentException::class.java) {
            validateLlmProvider("", "https://example.com/v1", "model-a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLlmProvider("My LLM", "http://example.com/v1", "model-a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLlmProvider("My LLM", "https://example.com/v1", "")
        }
    }
}
