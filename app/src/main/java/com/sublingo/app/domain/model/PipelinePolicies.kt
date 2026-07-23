package com.sublingo.app.domain.model

object PipelinePolicies {
    const val MAX_MEDIA_DURATION_MS: Long = 5L * 60 * 60_000
    const val FLASH_MAX_DURATION_MS: Long = 2L * 60 * 60_000
    const val DEFAULT_CHUNK_DURATION_MS: Long = 15L * 60_000
    const val MAX_BASE64_CHUNK_BYTES = 20 * 1024 * 1024L

    fun asrMode(durationMs: Long): String {
        require(durationMs in 1L..MAX_MEDIA_DURATION_MS) { "仅支持 5 小时以内的视频" }
        return if (durationMs <= FLASH_MAX_DURATION_MS) "FLASH" else "STANDARD"
    }

    fun pendingChunkIndexes(states: List<String>): List<Int> = states.mapIndexedNotNull { index, state ->
        index.takeUnless { state == "SUCCEEDED" }
    }
}
