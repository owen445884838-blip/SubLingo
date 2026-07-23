package com.sublingo.app.ui.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneticFormatterTest {
    @Test fun wrapsPlainPhoneticAndNormalizesExistingDelimiters() {
        assertEquals("/dei/", formatPhonetic("dei"))
        assertEquals("/dei/", formatPhonetic("/dei/"))
        assertEquals("/dei/", formatPhonetic("[dei]"))
    }

    @Test fun blankPhoneticStaysMissing() {
        assertNull(formatPhonetic("  "))
        assertNull(formatPhonetic(null))
    }
}
