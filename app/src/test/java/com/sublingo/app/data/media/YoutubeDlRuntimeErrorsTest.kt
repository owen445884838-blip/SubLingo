package com.sublingo.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeDlRuntimeErrorsTest {
    @Test
    fun `linkage failure does not expose an obfuscated class name`() {
        val error = NoClassDefFoundError("ef.e")

        assertEquals(
            "安装包中的运行时组件不兼容，请更新应用后重试",
            YoutubeDlRuntimeErrors.userMessage(error),
        )
    }

    @Test
    fun `specific nested initialization failure is retained`() {
        val error = IllegalStateException(
            "failed to initialize",
            IllegalArgumentException("运行时压缩包已损坏"),
        )

        assertEquals("运行时压缩包已损坏", YoutubeDlRuntimeErrors.userMessage(error))
    }

    @Test
    fun `message-free failure has an actionable fallback`() {
        assertEquals(
            "无法解压或启动运行时组件，请确认存储空间充足后重试",
            YoutubeDlRuntimeErrors.userMessage(IllegalStateException()),
        )
    }
}
