package com.sublingo.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProviderStorageIdentityTest {
    @Test fun `each provider preset gets an independent profile and encrypted secret alias`() {
        assertEquals("llm-deepseek", providerProfileId("LLM", "deepseek"))
        assertEquals("provider.llm.deepseek", providerSecretAlias("LLM", "deepseek"))
        assertNotEquals(
            providerSecretAlias("LLM", "deepseek"),
            providerSecretAlias("LLM", "xiaomi-mimo"),
        )
        assertNotEquals(
            providerSecretAlias("STT", "doubao-flash"),
            providerSecretAlias("STT", "xiaomi-mimo-asr"),
        )
    }
}
