package com.sublingo.app.ui.review

import org.junit.Assert.assertEquals
import org.junit.Test

class DefinitionFormattingTest {
    @Test fun semicolonSeparatedSensesRenderOnePerLine() {
        assertEquals("n. 工作\nvi. 运转\nvt. 使用", formatDefinitionForReading("n. 工作；vi. 运转; vt. 使用"))
    }
}
