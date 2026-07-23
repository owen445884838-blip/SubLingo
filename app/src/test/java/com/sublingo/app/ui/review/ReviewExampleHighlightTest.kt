package com.sublingo.app.ui.review

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sublingo.app.data.vocabulary.ContextualChineseMeaningResolver

class ReviewExampleHighlightTest {
    @Test fun highlightsCompletePhraseCaseInsensitively() {
        val result = highlightedExample("I Take it for granted every day.", "take it for granted", Color.Black)
        val backgrounds = result.spanStyles.filter { it.item.background != Color.Unspecified }
        assertEquals(1, backgrounds.size)
        assertEquals("Take it for granted", result.substring(backgrounds.single().start, backgrounds.single().end))
    }

    @Test fun missingTargetLeavesSentenceUnhighlighted() {
        val result = highlightedExample("A normal sentence.", "understand", Color.Black)
        assertTrue(result.spanStyles.none { it.item.background != Color.Unspecified })
    }

    @Test fun highlightsExactDictionaryMeaningInChineseExample() {
        val sentence = "苹果智能目前表现一般。"
        val target = ContextualChineseMeaningResolver.resolve(sentence, null, "n. 苹果, 家伙")
        val result = highlightedExample(sentence, target, Color.Gray)
        val backgrounds = result.spanStyles.filter { it.item.background != Color.Unspecified }

        assertEquals(1, backgrounds.size)
        assertEquals("苹果", result.substring(backgrounds.single().start, backgrounds.single().end))
    }
}
