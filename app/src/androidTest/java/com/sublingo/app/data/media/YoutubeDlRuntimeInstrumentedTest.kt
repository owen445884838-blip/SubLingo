package com.sublingo.app.data.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YoutubeDlRuntimeInstrumentedTest {
    @Test
    fun requiredPythonRuntimeExecutesOnTheDevicePageSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val capabilities = YoutubeDlRuntime(context).ensureInitialized()

        assertTrue(capabilities.pageSizeBytes >= 4_096L)
        if (capabilities.pageSizeBytes > 4_096L) {
            assertFalse(capabilities.ffmpegAvailable)
            assertFalse(capabilities.aria2cAvailable)
        }

        val request = YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }
        val response = YoutubeDL.getInstance().execute(request, "page-size-runtime-test")
        assertEquals(0, response.exitCode)
        assertEquals(YoutubeDlRuntime.PACKAGED_YTDLP_VERSION, response.out.trim())
    }
}
