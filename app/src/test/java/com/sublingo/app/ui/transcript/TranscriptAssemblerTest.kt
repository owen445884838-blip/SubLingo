package com.sublingo.app.ui.transcript

import com.sublingo.app.data.db.SubtitleCueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptAssemblerTest {
    @Test fun alignsTracksBySequenceAndKeepsMissingTranslation() {
        val english = listOf(
            SubtitleCueEntity("en-0", "en", 0, 100, 500, "Hello"),
            SubtitleCueEntity("en-1", "en", 1, 600, 900, "World"),
        )
        val chinese = listOf(SubtitleCueEntity("zh-0", "zh", 0, 100, 500, "你好"))
        val rows = TranscriptAssembler.assemble(english, chinese)
        assertEquals(2, rows.size)
        assertEquals("你好", rows[0].chinese)
        assertEquals("World", rows[1].english)
        assertNull(rows[1].chinese)
        assertEquals(600L, rows[1].startMs)
    }

    @Test fun cleansAlreadyPersistedRollingBilingualCaptions() {
        val english = listOf(
            SubtitleCueEntity("en-0", "en", 0, 19_830, 19_840, "Bye."),
            SubtitleCueEntity("en-1", "en", 1, 19_840, 21_390, "Bye. >> Okay, goodbye. [music]"),
            SubtitleCueEntity("en-2", "en", 2, 21_390, 21_400, ">> Okay, goodbye. [music]"),
            SubtitleCueEntity("en-3", "en", 3, 21_400, 22_230, ">> Okay, goodbye. [music] >> Okay, goodbye."),
            SubtitleCueEntity("en-4", "en", 4, 22_230, 22_240, ">> Okay, goodbye."),
            SubtitleCueEntity("en-5", "en", 5, 22_240, 25_550, ">> Okay, goodbye. >> Bye."),
        )
        val chinese = listOf(
            SubtitleCueEntity("zh-0", "zh", 0, 19_830, 19_840, "再见。"),
            SubtitleCueEntity("zh-1", "zh", 1, 19_840, 21_390, "再见。>> 好的，再见。[音乐]"),
            SubtitleCueEntity("zh-2", "zh", 2, 21_390, 21_400, ">> 好的，再见。[音乐]"),
            SubtitleCueEntity("zh-3", "zh", 3, 21_400, 22_230, ">> 好的，再见。[音乐] >> 好的，再见。"),
            SubtitleCueEntity("zh-4", "zh", 4, 22_230, 22_240, ">> 好的，再见。"),
            SubtitleCueEntity("zh-5", "zh", 5, 22_240, 25_550, ">> 好的，再见。 >> 再见。"),
        )

        val rows = TranscriptAssembler.assemble(english, chinese)

        assertEquals(listOf(1, 3, 5), rows.map { it.sequence })
        assertEquals(listOf("Bye. >> Okay, goodbye. [music]", ">> Okay, goodbye.", ">> Bye."), rows.map { it.english })
        assertEquals(listOf("再见。>> 好的，再见。[音乐]", ">> 好的，再见。", ">> 再见。"), rows.map { it.chinese })
    }

    @Test fun doesNotRenderChineseOnlySnapshotsRemovedFromTheEnglishRollingTrack() {
        val english = listOf(
            SubtitleCueEntity("en-0", "en", 0, 1_000, 1_010, "Start"),
            SubtitleCueEntity("en-1", "en", 1, 1_010, 2_000, "Start of a sentence"),
            SubtitleCueEntity("en-2", "en", 2, 2_000, 2_010, "of a sentence"),
            SubtitleCueEntity("en-3", "en", 3, 2_010, 3_000, "of a sentence with more words"),
        )
        val chinese = english.map { cue -> cue.copy(id = "zh-${cue.sequence}", trackId = "zh", text = "译文 ${cue.sequence}") }

        val rows = TranscriptAssembler.assemble(english, chinese)

        assertEquals(listOf(1, 3), rows.map { it.sequence })
    }
}
