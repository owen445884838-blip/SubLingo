package com.sublingo.app.data.remote

import com.sublingo.app.domain.provider.ChatCompletionProvider
import com.sublingo.app.domain.provider.DoubaoAsrProvider
import com.sublingo.app.domain.provider.ValidationLogProvider
import com.sublingo.app.domain.provider.VideoSourceClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeChatCompletionProvider @Inject constructor() : ChatCompletionProvider {
    override suspend fun complete(prompt: String): String = "{\"prompt\":\"$prompt\",\"result\":\"fake completion\"}"
}

@Singleton
class FakeDoubaoAsrProvider @Inject constructor() : DoubaoAsrProvider {
    override suspend fun recognize(audioBytes: ByteArray, useBase64: Boolean): String =
        "{\"accepted\":true,\"mode\":\"${if (useBase64) "base64" else "url"}\",\"size\":${audioBytes.size}}"
}

@Singleton
class FakeVideoSourceClient @Inject constructor() : VideoSourceClient {
    override suspend fun inspect(url: String): String =
        "{\"url\":\"$url\",\"source\":\"heuristic\"}"
}

@Singleton
class InMemoryValidationLogProvider @Inject constructor() : ValidationLogProvider {
    private val events = MutableStateFlow<List<String>>(emptyList())

    override fun observeEvents(): Flow<List<String>> = events.asStateFlow()

    override suspend fun record(event: String) {
        events.value = events.value + event
    }
}
