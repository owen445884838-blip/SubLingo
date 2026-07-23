package com.sublingo.app.ui.transcript

import com.sublingo.app.data.db.SubtitleWordAlignmentEntity
import com.sublingo.app.data.vocabulary.VocabularyItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptWordAlignerTest {
    @Test fun `legacy sentence fallback is localized before it can hide word pairs`() {
        val english = "And then little things"
        val localized = localizeSentenceFallbackAlignments(
            english,
            listOf(
                SubtitleWordAlignmentEntity("a0", "video", 7, 0, "And", "然后"),
                SubtitleWordAlignmentEntity("a1", "video", 7, 1, english, "一些"),
                SubtitleWordAlignmentEntity("a2", "video", 7, 2, "little", "小"),
                SubtitleWordAlignmentEntity("a3", "video", 7, 3, "things", "细节"),
            ),
        )
        val highlights = localized.groupBy { it.englishSurface to it.englishOccurrence }.map { (_, pairs) ->
            TranscriptHighlight(pairs.first().id, pairs.first().englishSurface, pairs.map { it.chineseSurface })
        }
        val aligned = TranscriptWordAligner.align(english, "然后一些小细节", highlights)

        assertTrue(aligned.english.first { it.text == "And" }.alignmentId != null)
        assertTrue(aligned.english.first { it.text == "little" }.alignmentId != null)
        assertTrue(aligned.english.first { it.text == "things" }.alignmentId != null)
        assertTrue(aligned.chinese.filter { it.isWord }.all { it.alignmentId != null })
        val littleId = aligned.english.first { it.text == "little" }.alignmentId
        assertTrue(aligned.chinese.first { it.text == "一些小" }.alignmentIds.contains(littleId))
        assertTrue(aligned.english.none { it.text == english })
    }

    @Test fun `translation word map highlights all mapped bilingual tokens`() {
        val aligned = TranscriptWordAligner.align(
            "I like this game",
            "我喜欢这场比赛",
            listOf(
                TranscriptHighlight("translation-0", "I", listOf("我")),
                TranscriptHighlight("translation-1", "like", listOf("喜欢")),
                TranscriptHighlight("translation-2", "this game", listOf("这场比赛")),
            ),
        )

        assertTrue(aligned.english.filter { it.isWord }.all { it.alignmentId != null })
        assertTrue(aligned.chinese.filter { it.isWord }.all { it.alignmentId != null })
    }

    @Test fun `one English token can highlight multiple translated Chinese segments`() {
        val aligned = TranscriptWordAligner.align(
            "I did not go",
            "我没有去",
            listOf(TranscriptHighlight("translation-did-not", "did not", listOf("没", "有"))),
        )

        val id = aligned.english.first { it.text == "did not" }.alignmentId
        assertTrue(id != null)
        assertTrue(aligned.chinese.any { it.text == "没有" && it.alignmentIds.contains(id) })
    }
    @Test fun `repeated English words map by occurrence`() {
        val aligned = TranscriptWordAligner.align(
            "very very good",
            "真的非常好",
            listOf(
                TranscriptHighlight("first-very", "very", listOf("真"), englishOccurrence = 0),
                TranscriptHighlight("second-very", "very", listOf("非常"), englishOccurrence = 1),
            ),
        )

        val veryTokens = aligned.english.filter { it.text == "very" }
        assertTrue(veryTokens.size == 2)
        assertTrue(veryTokens[0].alignmentId != veryTokens[1].alignmentId)
    }
    @Test fun highlightsOnlyExtractedVocabularyAndExactChineseMatch() {
        val aligned = TranscriptWordAligner.align(
            "The resilience of a forest ecosystem depends on roots.",
            "森林生态系统的韧性取决于根系。",
            listOf(
                TranscriptHighlight("resilience", "resilience", listOf("韧性")),
                TranscriptHighlight("ecosystem", "ecosystem", listOf("生态系统")),
            ),
        )

        assertTrue(aligned.english.first { it.text == "resilience" }.alignmentId != null)
        assertTrue(aligned.english.first { it.text == "ecosystem" }.alignmentId != null)
        assertNull(aligned.english.first { it.text == "of" }.alignmentId)
        assertNull(aligned.english.first { it.text == "on" }.alignmentId)
        assertTrue(aligned.chinese.any { it.text == "韧性" && it.alignmentId != null })
        assertTrue(aligned.chinese.any { it.text == "生态系统" && it.alignmentId != null })
    }

    @Test fun prepositionHighlightsOnlyWhenPartOfExtractedPhrase() {
        val aligned = TranscriptWordAligner.align(
            "It depends on the roots and grows in spring.",
            "它取决于根系并在春天生长。",
            listOf(TranscriptHighlight("depends-on", "depends on", listOf("取决于"), VocabularyItemType.PHRASAL_VERB)),
        )
        val dependsId = aligned.english.first { it.text == "depends on" }.alignmentId
        assertTrue(dependsId != null)
        assertNull(aligned.english.first { it.text == "in" }.alignmentId)
        assertTrue(aligned.chinese.any { it.text == "取决于" && it.alignmentId == dependsId })
    }

    @Test fun completePhraseRendersAsOneContinuousEnglishAndChineseHighlight() {
        val aligned = TranscriptWordAligner.align(
            "They show a video at school.",
            "他们在学校播放一段视频。",
            listOf(TranscriptHighlight("show-video", "show a video", listOf("播放一段视频"), VocabularyItemType.COLLOCATION)),
        )
        assertTrue(aligned.english.any { it.text == "show a video" && it.alignmentId != null })
        assertTrue(aligned.chinese.any { it.text == "播放一段视频" && it.alignmentId != null })
    }

    @Test fun uncertainChineseCorrespondenceIsNotFabricated() {
        val aligned = TranscriptWordAligner.align(
            "The ecosystem is resilient.",
            "这个系统适应能力很强。",
            listOf(TranscriptHighlight("resilient", "resilient", listOf("韧性的"))),
        )
        assertTrue(aligned.english.first { it.text == "resilient" }.alignmentId != null)
        assertTrue(aligned.chinese.all { it.alignmentId == null })
    }

    @Test fun playbackHighlightsOnlyDuringExtractedVocabularyWindow() {
        val aligned = TranscriptWordAligner.align(
            "A resilient forest ecosystem survives.",
            "有韧性的森林生态系统能够生存。",
            listOf(TranscriptHighlight("ecosystem", "forest ecosystem", listOf("森林生态系统"), VocabularyItemType.COLLOCATION)),
        )
        assertNull(TranscriptWordAligner.activeAlignmentId(aligned, 1_100L, 1_000L, 6_000L))
        assertTrue(TranscriptWordAligner.activeAlignmentId(aligned, 3_500L, 1_000L, 6_000L) != null)
        assertTrue(TranscriptWordAligner.activeAlignmentId(aligned, 5_900L, 1_000L, 6_000L) != null)
    }

    @Test fun previousPhraseStaysHighlightedUntilTheNextPhraseStarts() {
        val aligned = TranscriptWordAligner.align(
            "They show a video at school today.",
            "他们今天在学校播放一段视频。",
            listOf(
                TranscriptHighlight("show-video", "show a video", listOf("播放一段视频"), VocabularyItemType.COLLOCATION),
                TranscriptHighlight("school", "school", listOf("学校")),
            ),
        )
        val first = TranscriptWordAligner.activeAlignmentId(aligned, 3_000, 0, 10_000)
        val between = TranscriptWordAligner.activeAlignmentId(aligned, 5_000, 0, 10_000)
        assertEquals(first, between)
    }

    @Test fun punctuationNeverReceivesHighlight() {
        val aligned = TranscriptWordAligner.align(
            "Hello, ecosystem!",
            "你好，生态系统！",
            listOf(TranscriptHighlight("ecosystem", "ecosystem", listOf("生态系统"))),
        )
        assertTrue(aligned.english.filter { !it.isWord }.all { it.alignmentId == null })
        assertTrue(aligned.chinese.filter { !it.isWord }.all { it.alignmentId == null })
    }

    @Test fun sharedChineseSpanKeepsEveryEnglishAlignment() {
        val aligned = TranscriptWordAligner.align(
            "The cancellation of plans feels freeing.",
            "取消计划让人感觉解脱。",
            listOf(
                TranscriptHighlight("cancel", "cancellation", listOf("取消")),
                TranscriptHighlight("cancel-plans", "cancellation of plans", listOf("取消计划"), VocabularyItemType.COLLOCATION),
            ),
        )
        val phraseId = aligned.english.first { it.text == "cancellation of plans" }.alignmentId
        val cancellationId = phraseId
        val cancelChinese = aligned.chinese.first { it.text.contains("取消") }
        assertTrue(cancellationId in cancelChinese.alignmentIds)
        assertTrue(phraseId in cancelChinese.alignmentIds)
    }

    @Test fun displayLayerDoesNotInventPhraseFromAdjacentWords() {
        val aligned = TranscriptWordAligner.align(
            "They show a video at school.",
            "他们在学校播放一段视频。",
            listOf(
                TranscriptHighlight("show", "show", listOf("播放")),
                TranscriptHighlight("video", "video", listOf("视频")),
            ),
        )
        assertTrue(aligned.english.none { it.text == "show a video" })
        assertTrue(aligned.english.any { it.text == "show" && it.alignmentId != null })
        assertTrue(aligned.english.any { it.text == "video" && it.alignmentId != null })
    }

    @Test fun typedLotOfTimeCollocationRendersExactlyAsPersisted() {
        val aligned = TranscriptWordAligner.align(
            "You a lot of time see headlines like this.",
            "你经常看到这样的标题。",
            listOf(TranscriptHighlight("lot-of-time", "lot of time", listOf("经常"), VocabularyItemType.COLLOCATION)),
        )
        val phrase = aligned.english.single { it.alignmentId != null }
        assertEquals("lot of time", phrase.text)
        assertTrue(aligned.chinese.any { it.text == "经常" && it.alignmentId == phrase.alignmentId })
    }

    @Test fun conversationalAndGrammaticalChunksRenderAsWholeBilingualHighlights() {
        val cases = listOf(
            Triple(
                "Thank you for coming today.",
                "谢谢你的到来。",
                TranscriptHighlight(
                    "thanks-coming",
                    "Thank you for coming",
                    listOf("谢谢你的到来"),
                    VocabularyItemType.FORMULAIC_EXPRESSION,
                ),
            ),
            Triple(
                "By the way, this is useful.",
                "顺便说一下，这很有用。",
                TranscriptHighlight(
                    "by-the-way",
                    "By the way",
                    listOf("顺便说一下"),
                    VocabularyItemType.DISCOURSE_MARKER,
                ),
            ),
            Triple(
                "I do appreciate your help.",
                "我确实很感谢你的帮助。",
                TranscriptHighlight(
                    "do-appreciate",
                    "do appreciate",
                    listOf("确实很感谢"),
                    VocabularyItemType.GRAMMATICAL_CHUNK,
                ),
            ),
        )

        cases.forEach { (english, chinese, highlight) ->
            val aligned = TranscriptWordAligner.align(english, chinese, listOf(highlight))
            val phrase = aligned.english.single { it.alignmentId != null }
            assertEquals(highlight.surfaceForm.lowercase(), phrase.text.lowercase())
            assertTrue(aligned.chinese.any { it.alignmentId == phrase.alignmentId })
        }
    }

    @Test fun wordRowIsNotExpandedByDisplayHeuristics() {
        val aligned = TranscriptWordAligner.align(
            "I read it a lot.",
            "我经常读它。",
            listOf(TranscriptHighlight("lot", "lot", listOf("经常"))),
        )
        assertTrue(aligned.english.any { it.text == "lot" && it.alignmentId != null })
        assertTrue(aligned.english.none { it.text == "a lot" })
    }
}
