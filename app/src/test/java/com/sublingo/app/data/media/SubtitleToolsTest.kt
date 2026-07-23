package com.sublingo.app.data.media

import com.sublingo.app.data.db.SubtitleCueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleToolsTest {

    @Test
    fun `translation parser preserves valid objects around malformed output`() {
        val raw = """
            ```json
            [
              {"index":127,"textZh":"上一句"},
              {"index":128,">> 它适用于任何镜头。"},
              {"index":129,"textZh":"下一句"}
            ]
            ```
        """.trimIndent()

        assertEquals(
            listOf(
                TranslationAlignment.Item(127, "上一句"),
                TranslationAlignment.Item(129, "下一句"),
            ),
            TranslationResponseParser.parse(raw),
        )
    }

    @Test
    fun `translation parser keeps duplicate and blank items for alignment validation`() {
        val parsed = TranslationResponseParser.parse(
            """[{"index":5,"textZh":"译文一"},{"index":5,"textZh":"译文二"},{"index":6,"textZh":"  "}]""",
        )

        assertEquals(setOf(5, 6), TranslationAlignment.validate(setOf(5, 6), parsed))
    }

    @Test fun `translation parser reads reverse Chinese to English word pairs`() {
        val parsed = TranslationResponseParser.parseAligned(
            """[{"index":7,"textZh":"我喜欢这场比赛","wordPairs":[{"en":"I","zh":"我"},{"en":"like","zh":"喜欢"},{"en":"this game","zh":"这场比赛"}]}]""",
        )

        assertEquals("我喜欢这场比赛", parsed.single().item.text)
        assertEquals(
            listOf(
                TranslationWordPair("I", "我", 0),
                TranslationWordPair("like", "喜欢", 0),
                TranslationWordPair("this game", "这场比赛", 0),
            ),
            parsed.single().wordPairs,
        )
    }

    @Test fun `translation parser preserves contextual Chinese semantic units`() {
        val parsed = TranslationResponseParser.parseAligned(
            """[{"index":11,"textZh":"这是一项突破性的人工智能功能","wordPairs":[{"en":"This is","zh":"这是"},{"en":"a groundbreaking AI feature","zh":"一项突破性的人工智能功能"}]}]""",
        ).single()

        val repaired = TranslationWordMapRepair.fillUncoveredChinese(
            "This is a groundbreaking AI feature.",
            parsed,
        )

        assertEquals(listOf("这是", "一项突破性的人工智能功能"), repaired.wordPairs.map { it.chinese })
        assertEquals("a groundbreaking AI feature", repaired.wordPairs.last().english)
    }
    @Test fun parsesSrtAndVttTimestamps() {
        val content = """
            WEBVTT

            00:00:01.250 --> 00:00:03.500
            Hello world

            2
            00:04,000 --> 00:06,125
            Second line
        """.trimIndent()
        val cues = SubtitleParser.parse(content)
        assertEquals(2, cues.size)
        assertEquals(1_250, cues[0].startMs)
        assertEquals(6_125, cues[1].endMs)
    }

    @Test fun removesRollingCaptionSnapshotsAndRepeatedPrefixes() {
        val content = """
            WEBVTT

            00:00:19.830 --> 00:00:19.840
            Bye.

            00:00:19.840 --> 00:00:21.390
            Bye. >> Okay, goodbye. [music]

            00:00:21.390 --> 00:00:21.400
            >> Okay, goodbye. [music]

            00:00:21.400 --> 00:00:22.230
            >> Okay, goodbye. [music] >> Okay, goodbye.

            00:00:22.230 --> 00:00:22.240
            >> Okay, goodbye.

            00:00:22.240 --> 00:00:25.550
            >> Okay, goodbye. >> Bye.
        """.trimIndent()

        val cues = SubtitleParser.parse(content)

        assertEquals(
            listOf("Bye. >> Okay, goodbye. [music]", ">> Okay, goodbye.", ">> Bye."),
            cues.map { it.text },
        )
        assertEquals(listOf(0, 1, 2), cues.map { it.sequence })
    }

    @Test fun reportsOnlyMissingDuplicateExtraOrBlankIndexes() {
        val invalid = TranslationAlignment.validate(
            setOf(1, 2, 3),
            listOf(
                TranslationAlignment.Item(1, "一"),
                TranslationAlignment.Item(2, ""),
                TranslationAlignment.Item(4, "额外"),
            ),
        )
        assertEquals(setOf(2, 3, 4), invalid)
    }

    @Test fun dynamicBatchesPreserveEveryCueExactlyOnce() {
        val cues = (0 until 100).map { index ->
            SubtitleCueEntity("c$index", "track", index, index * 1000L, index * 1000L + 900, "Sentence $index ".repeat(12))
        }
        val batches = TranslationAlignment.batches(cues, tokenBudget = 220)
        assertTrue(batches.size > 1)
        assertEquals((0 until 100).toList(), batches.flatten().map { it.sequence })
    }

    @Test fun xiaomiTranslationUsesSmallerBatchesWithoutDroppingOrReorderingCues() {
        val cues = (0 until 120).map { index ->
            SubtitleCueEntity("c$index", "track", index, index * 1000L, index * 1000L + 900, "Natural subtitle sentence number $index with several words.")
        }

        val xiaomi = TranslationAlignment.batchesForProvider(cues, "xiaomi-mimo")
        val default = TranslationAlignment.batchesForProvider(cues, "deepseek")

        assertTrue(xiaomi.size > default.size)
        assertTrue(xiaomi.all { it.size == 1 })
        assertEquals(cues.map { it.sequence }, xiaomi.flatten().map { it.sequence })
        assertEquals(cues.map { it.sequence }, default.flatten().map { it.sequence })
    }

    @Test fun deepSeekTranslationLimitsOutputHeavyBatches() {
        val cues = (0 until 40).map { index ->
            SubtitleCueEntity("c$index", "track", index, index * 1000L, index * 1000L + 900, "Short subtitle number $index.")
        }

        val batches = TranslationAlignment.batchesForProvider(cues, "deepseek")

        assertTrue(batches.all { it.size <= 12 })
        assertEquals(cues.map { it.sequence }, batches.flatten().map { it.sequence })
    }

    @Test fun fillsOnlyUncoveredChineseSpansWithConservativeSentenceFallback() {
        val parsed = ParsedTranslation(
            item = TranslationAlignment.Item(1, "好的，欢迎你今天首次体验。"),
            wordPairs = listOf(
                TranslationWordPair("welcome", "欢迎"),
                TranslationWordPair("your first look", "首次体验"),
            ),
        )

        val repaired = TranslationWordMapRepair.fillUncoveredChinese(
            "All right, welcome to your first look.",
            parsed,
        )

        assertEquals(listOf("好的", "欢迎", "你今天", "首次体验"), repaired.wordPairs.map { it.chinese })
        assertEquals("welcome", repaired.wordPairs.first { it.chinese == "好的" }.english)
        assertEquals("your first look", repaired.wordPairs.first { it.chinese == "你今天" }.english)
    }

    @Test fun normalizesOutOfOrderModelPairsBeforeFillingGaps() {
        val parsed = ParsedTranslation(
            item = TranslationAlignment.Item(1, "我喜欢这场比赛"),
            wordPairs = listOf(
                TranslationWordPair("this game", "这场比赛"),
                TranslationWordPair("I", "我"),
                TranslationWordPair("like", "喜欢"),
            ),
        )

        val repaired = TranslationWordMapRepair.fillUncoveredChinese("I like this game", parsed)

        assertEquals(listOf("我", "喜欢", "这场比赛"), repaired.wordPairs.map { it.chinese })
    }

    @Test fun removesParaphrasedEnglishSurfacesBeforeConservativeRepair() {
        val parsed = ParsedTranslation(
            item = TranslationAlignment.Item(2, "我们有一款新手机"),
            wordPairs = listOf(
                TranslationWordPair("we have", "我们有"),
                TranslationWordPair("new phone", "新手机"),
            ),
        )

        val repaired = TranslationWordMapRepair.fillUncoveredChinese(
            "So we've got a new phone.",
            parsed,
        )

        assertEquals(listOf("我们有一款", "新手机"), repaired.wordPairs.map { it.chinese })
        assertEquals("new phone", repaired.wordPairs.first().english)
    }

    @Test fun preservesTheRealOrderOfRepeatedChineseSurfaces() {
        val parsed = ParsedTranslation(
            item = TranslationAlignment.Item(8, "新的设计有新的铰链"),
            wordPairs = listOf(
                TranslationWordPair("hinge", "铰链"),
                TranslationWordPair("new", "新的"),
                TranslationWordPair("design", "设计"),
            ),
        )

        val repaired = TranslationWordMapRepair.fillUncoveredChinese(
            "The new design has a new hinge.",
            parsed,
        )

        assertEquals(listOf("新的", "设计", "有新的", "铰链"), repaired.wordPairs.map { it.chinese })
        assertEquals("hinge", repaired.wordPairs[2].english)
    }

    @Test fun discardsOverlappingChinesePairsBeforeFillingGaps() {
        val parsed = ParsedTranslation(
            item = TranslationAlignment.Item(9, "这款手机更轻"),
            wordPairs = listOf(
                TranslationWordPair("this phone", "这款手机"),
                TranslationWordPair("phone", "手机"),
                TranslationWordPair("lighter", "更轻"),
            ),
        )

        val repaired = TranslationWordMapRepair.fillUncoveredChinese(
            "This phone is lighter.",
            parsed,
        )

        assertEquals(listOf("这款手机", "更轻"), repaired.wordPairs.map { it.chinese })
    }

    @Test fun fallsBackToTheSentenceOnlyWhenTheModelReturnedNoUsableLocalPair() {
        val parsed = ParsedTranslation(
            item = TranslationAlignment.Item(10, "完全缺失"),
            wordPairs = emptyList(),
        )

        val repaired = TranslationWordMapRepair.fillUncoveredChinese("Completely missing.", parsed)

        assertEquals(listOf(TranslationWordPair("Completely missing.", "完全缺失")), repaired.wordPairs)
    }
}
