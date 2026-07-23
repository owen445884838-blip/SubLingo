package com.sublingo.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.domain.model.ProcessingState

@Entity(indices = [Index(value = ["source", "remoteVideoId"], unique = false)])
data class VideoEntity(
    @PrimaryKey val id: String,
    val originalUrl: String? = null,
    val canonicalUrl: String? = null,
    val source: String? = null,
    val remoteVideoId: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val filePath: String? = null,
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val language: String? = null,
    val lastPlayedPositionMs: Long = 0L,
    val vocabularyVersion: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(VideoEntity::class, ["id"], ["videoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("videoId")],
)
data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val currentStage: ProcessingStage = ProcessingStage.METADATA,
    val state: ProcessingState = ProcessingState.PENDING,
    val progress: Int = 0,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(VideoEntity::class, ["id"], ["videoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("videoId"), Index("sourceTrackId")],
)
data class SubtitleTrackEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val language: String,
    val kind: String,
    val sourceTrackId: String? = null,
    val providerId: String? = null,
    val model: String? = null,
    val promptVersion: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(SubtitleTrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["trackId", "sequence"], unique = true)],
)
data class SubtitleCueEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val sequence: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isUserEdited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(VideoEntity::class, ["id"], ["videoId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("videoId"),
        Index(value = ["videoId", "sequence", "ordinal"], unique = true),
    ],
)
data class SubtitleWordAlignmentEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val sequence: Int,
    val ordinal: Int,
    val englishSurface: String,
    val chineseSurface: String,
    val englishOccurrence: Int = 0,
    val source: String = "TRANSLATION",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val presetId: String? = null,
    val baseUrl: String? = null,
    val model: String? = null,
    val resourceId: String? = null,
    val optionsJson: String? = null,
    val secretAlias: String? = null,
    val enabled: Boolean = true,
)

@Entity(
    foreignKeys = [ForeignKey(ProcessingJobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["jobId", "chunkIndex"], unique = true)],
)
data class AudioChunkEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val chunkIndex: Int,
    val startOffsetMs: Long,
    val durationMs: Long,
    val filePath: String,
    val remoteTaskId: String? = null,
    val state: String = "PENDING",
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(ProcessingJobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["jobId", "batchIndex"], unique = true)],
)
data class TranslationBatchEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val batchIndex: Int,
    val sourceTrackId: String,
    val firstSequence: Int,
    val lastSequence: Int,
    val state: String = "PENDING",
    val attemptCount: Int = 0,
    val tokenUsage: Int? = null,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(VideoEntity::class, ["id"], ["videoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["videoId", "version", "phase", "inputHash"], unique = true)],
)
data class VocabularyLlmBatchEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val version: Int,
    val phase: String,
    val inputHash: String,
    val responseJson: String? = null,
    val state: String = "PENDING",
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(indices = [Index(value = ["language", "normalizedLemma"], unique = true)])
data class LexemeEntity(
    @PrimaryKey val id: String,
    val lemma: String,
    val normalizedLemma: String,
    val language: String = "en",
    val phonetic: String? = null,
    val audioUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(LexemeEntity::class, ["id"], ["lexemeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lexemeId")],
)
data class LexemeSenseEntity(
    @PrimaryKey val id: String,
    val lexemeId: String,
    val pos: String? = null,
    val definitionEn: String,
    val definitionZh: String? = null,
    val source: String,
)

@Entity(
    foreignKeys = [
        ForeignKey(LexemeEntity::class, ["id"], ["lexemeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VideoEntity::class, ["id"], ["videoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubtitleCueEntity::class, ["id"], ["cueId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["lexemeId", "cueId", "surfaceForm"], unique = true), Index("videoId"), Index("cueId")],
)
data class WordOccurrenceEntity(
    @PrimaryKey val id: String,
    val lexemeId: String,
    val videoId: String,
    val cueId: String,
    val surfaceForm: String,
    val itemType: String = "WORD",
    val contextEn: String,
    val contextZh: String? = null,
    val translationZh: String? = null,
    val alignmentVersion: Int = 0,
    val difficultyLevel: String = "UNKNOWN",
    val difficultySource: String = "LOCAL",
    val difficultyConfidence: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(LexemeEntity::class, ["id"], ["lexemeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["lexemeId"], unique = true)],
)
data class ReviewCardEntity(
    @PrimaryKey val id: String,
    val lexemeId: String,
    val isFavorite: Boolean = false,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val dueAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(ReviewCardEntity::class, ["id"], ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cardId"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val rating: String,
    val reviewedAt: Long,
    val previousRepetitions: Int,
    val previousIntervalDays: Int,
    val previousEaseFactor: Double,
    val previousDueAt: Long,
    val nextRepetitions: Int,
    val nextIntervalDays: Int,
    val nextEaseFactor: Double,
    val nextDueAt: Long,
)

@Entity
data class DictionaryCacheEntity(
    @PrimaryKey val query: String,
    val responseJson: String? = null,
    val state: String,
    val errorMessage: String? = null,
    val expiresAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
