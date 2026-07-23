package com.sublingo.app.work

import java.io.IOException

object BackgroundRetryPolicy {
    fun isTransientNetworkFailure(error: Throwable): Boolean {
        if (generateSequence(error) { it.cause }.any { it is IOException }) return true
        val message = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
        if (message.contains("403") || message.contains("401") || message.contains("sign in") || message.contains("not a bot")) return false
        return TRANSIENT_MARKERS.any(message::contains)
    }

    private val TRANSIENT_MARKERS = listOf(
        "timed out", "timeout", "connection reset", "connection refused", "network is unreachable",
        "temporary failure", "unable to resolve host", "name or service not known", "no route to host",
        "broken pipe", "unexpected end of stream", "http error 408", "http error 429",
        "http error 500", "http error 502", "http error 503", "http error 504",
    )
}
