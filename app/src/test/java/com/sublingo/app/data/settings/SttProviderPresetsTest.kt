package com.sublingo.app.data.settings

import com.sublingo.app.data.db.ProviderProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SttProviderPresetsTest {
    @Test fun exposesDoubaoAndCustomOnly() {
        assertEquals(listOf(SttPresetId.DOUBAO, SttPresetId.CUSTOM), SttProviderPresets.all.map { it.id })
        assertTrue(SttProviderPresets.all.none { it.displayName.contains("DeepSeek") })
        assertTrue(SttProviderPresets.all.none { it.displayName.contains("MiMo") })
        assertEquals(SttPresetId.CUSTOM, SttPresetId.fromStorageId("xiaomi-mimo-asr"))
    }

    @Test fun persistedProtocolRoundTripsThroughOptionsJson() {
        val profile = ProviderProfileEntity(
            id = "stt", kind = "STT", name = "Custom", presetId = "custom-stt",
            optionsJson = sttOptionsJson(SttProtocol.OPENAI_CHAT_AUDIO),
        )
        assertEquals(SttProtocol.OPENAI_CHAT_AUDIO, sttProtocol(profile))
    }

    @Test fun validatesFieldsRequiredByEachProtocol() {
        validateSttProvider("Chat ASR", "https://example.com/v1", SttProtocol.OPENAI_CHAT_AUDIO, "audio-model", "")
        validateSttProvider("Whisper", "https://example.com/v1", SttProtocol.OPENAI_TRANSCRIPTION, "whisper-1", "")
        validateSttProvider("Doubao", "https://example.com/asr", SttProtocol.DOUBAO_BIGMODEL, "", "resource")
        assertThrows(IllegalArgumentException::class.java) {
            validateSttProvider("Custom", "https://example.com/v1", SttProtocol.OPENAI_CHAT_AUDIO, "", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateSttProvider("Custom", "http://example.com/asr", SttProtocol.DOUBAO_BIGMODEL, "", "resource")
        }
    }
}
