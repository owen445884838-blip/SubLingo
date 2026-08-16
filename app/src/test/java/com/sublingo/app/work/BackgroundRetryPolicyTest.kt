package com.sublingo.app.work

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRetryPolicyTest {
    @Test fun retriesTemporaryNetworkFailures() {
        assertTrue(BackgroundRetryPolicy.isTransientNetworkFailure(IOException("connection reset")))
        assertTrue(BackgroundRetryPolicy.isTransientNetworkFailure(IllegalStateException("HTTP Error 503")))
        assertTrue(BackgroundRetryPolicy.isTransientNetworkFailure(IllegalStateException("unable to resolve host")))
    }

    @Test fun doesNotLoopAuthenticationFailures() {
        assertFalse(BackgroundRetryPolicy.isTransientNetworkFailure(IllegalStateException("HTTP Error 403: Forbidden")))
        assertFalse(BackgroundRetryPolicy.isTransientNetworkFailure(IllegalStateException("Sign in to confirm you're not a bot")))
    }

    @Test fun doesNotRetryAnIdleDownloaderRoutingFailure() {
        assertFalse(
            BackgroundRetryPolicy.isTransientNetworkFailure(
                IllegalStateException("下载请求超时，请检查网络或 VPN 分流设置"),
            ),
        )
    }
}
