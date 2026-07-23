package com.sublingo.app.data.remote

import com.sublingo.app.data.db.ProviderProfileEntity
import com.sublingo.app.security.SecretStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleClientTest {
    @Test fun onlyTransientHttpFailuresAreRetryable() {
        assertFalse(LlmHttpException(400, "bad model").isRetryable)
        assertFalse(LlmHttpException(401, "bad key").isRetryable)
        assertTrue(LlmHttpException(429, "rate limited").isRetryable)
        assertTrue(LlmHttpException(503, "unavailable").isRetryable)
    }

    @Test fun emptyFinalContentReportsSafeCompletionDiagnostics() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setBody(
                    """{"choices":[{"finish_reason":"length","message":{"content":"","reasoning_content":"thinking"}}],"usage":{"completion_tokens":4096}}""",
                ),
            )
            start()
        }
        try {
            val client = OpenAiCompatibleClient(OkHttpClient(), FakeSecretStore())
            val error = runCatching {
                client.complete(
                    ProviderProfileEntity(
                        id = "llm-default",
                        kind = "LLM",
                        name = "Test",
                        baseUrl = server.url("/v1").toString().trimEnd('/'),
                        model = "test-model",
                        secretAlias = "test-key",
                    ),
                    "extract",
                )
            }.exceptionOrNull()

            assertTrue(error is LlmEmptyContentException)
            assertEquals(
                "LLM 响应没有最终内容（finish_reason=length, reasoning_chars=8, completion_tokens=4096）",
                error?.message,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun cancellationStopsAnInFlightHttpCall() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE),
            )
            start()
        }
        try {
            val client = OpenAiCompatibleClient(OkHttpClient(), FakeSecretStore())
            val profile = ProviderProfileEntity(
                id = "llm-default",
                kind = "LLM",
                name = "Test",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                model = "test-model",
                secretAlias = "test-key",
            )
            val job = launch(start = CoroutineStart.UNDISPATCHED) { client.complete(profile, "translate") }
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)

            withTimeout(2_000) {
                job.cancelAndJoin()
            }
            assertTrue(job.isCancelled)
        } finally {
            server.shutdown()
        }
    }

    private class FakeSecretStore : SecretStore {
        override suspend fun save(alias: String, value: String) = Unit
        override suspend fun read(alias: String): String = "secret"
        override suspend fun delete(alias: String) = Unit
    }
}
