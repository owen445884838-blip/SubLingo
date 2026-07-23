package com.sublingo.app.domain.provider

import kotlinx.coroutines.flow.Flow

interface ChatCompletionProvider {
    suspend fun complete(prompt: String): String
}

interface DoubaoAsrProvider {
    suspend fun recognize(audioBytes: ByteArray, useBase64: Boolean): String
}

interface VideoSourceClient {
    suspend fun inspect(url: String): String
}

interface ValidationLogProvider {
    fun observeEvents(): Flow<List<String>>
    suspend fun record(event: String)
}
