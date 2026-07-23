package com.sublingo.app.ui.transcript

import com.sublingo.app.data.vocabulary.VocabularyItemType
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptFollowPolicyTest {
    @Test fun activeCueUsesAStableReadableTopOffset() {
        assertTrue(TRANSCRIPT_FOLLOW_SCROLL_OFFSET_PX == 0)
    }

    @Test fun prePositionedNextCueStaysUnhighlightedUntilPlaybackStarts() {
        val aligned = TranscriptWordAligner.align(
            "They show a video.",
            "他们播放一段视频。",
            listOf(TranscriptHighlight("show-video", "show a video", listOf("播放一段视频"), VocabularyItemType.COLLOCATION)),
        )
        assertTrue(displayedTranscriptAlignmentId(aligned, 900, 1_000, 2_000) == null)
        assertTrue(displayedTranscriptAlignmentId(aligned, 1_500, 1_000, 2_000) != null)
    }
}
