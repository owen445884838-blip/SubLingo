package com.sublingo.app.domain.model

enum class ProcessingStage {
    METADATA,
    DOWNLOAD,
    SUBTITLE_DISCOVERY,
    AUDIO_EXTRACTION,
    TRANSCRIPTION,
    TRANSLATION,
    VOCABULARY,
}

enum class ProcessingState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    WAITING_FOR_USER,
}
