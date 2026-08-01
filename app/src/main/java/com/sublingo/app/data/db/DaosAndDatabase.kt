package com.sublingo.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM VideoEntity ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM VideoEntity WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<VideoEntity?>

    @Query("SELECT * FROM VideoEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VideoEntity?

    @Upsert
    suspend fun upsert(video: VideoEntity)

    @Update
    suspend fun update(video: VideoEntity)

    @Query("DELETE FROM VideoEntity WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE VideoEntity SET lastPlayedPositionMs = :positionMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, positionMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE VideoEntity SET vocabularyVersion = :version, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVocabularyVersion(id: String, version: Int, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ProcessingJobDao {
    @Query("SELECT * FROM ProcessingJobEntity WHERE videoId = :videoId LIMIT 1")
    fun observeByVideoId(videoId: String): Flow<ProcessingJobEntity?>

    @Query("SELECT * FROM ProcessingJobEntity ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProcessingJobEntity>>

    @Upsert
    suspend fun upsert(job: ProcessingJobEntity)

    @Query("DELETE FROM ProcessingJobEntity WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("SELECT * FROM ProcessingJobEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProcessingJobEntity?

    @Query("SELECT * FROM ProcessingJobEntity WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): ProcessingJobEntity?
}

@Dao
interface SubtitleTrackDao {
    @Query("SELECT * FROM SubtitleTrackEntity WHERE videoId = :videoId ORDER BY createdAt ASC")
    fun observeByVideoId(videoId: String): Flow<List<SubtitleTrackEntity>>

    @Upsert
    suspend fun upsert(track: SubtitleTrackEntity)

    @Query("SELECT * FROM SubtitleTrackEntity WHERE videoId = :videoId ORDER BY createdAt ASC")
    suspend fun getByVideoId(videoId: String): List<SubtitleTrackEntity>
}

@Dao
interface SubtitleCueDao {
    @Query("SELECT * FROM SubtitleCueEntity WHERE trackId = :trackId ORDER BY sequence ASC")
    fun observeByTrackId(trackId: String): Flow<List<SubtitleCueEntity>>

    @Upsert
    suspend fun upsertAll(cues: List<SubtitleCueEntity>)

    @Query("SELECT * FROM SubtitleCueEntity WHERE trackId = :trackId ORDER BY sequence ASC")
    suspend fun getByTrackId(trackId: String): List<SubtitleCueEntity>

    @Query("DELETE FROM SubtitleCueEntity WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)
}

@Dao
interface SubtitleWordAlignmentDao {
    @Query("SELECT * FROM SubtitleWordAlignmentEntity WHERE videoId = :videoId ORDER BY sequence ASC, ordinal ASC")
    fun observeByVideoId(videoId: String): Flow<List<SubtitleWordAlignmentEntity>>

    @Query("SELECT * FROM SubtitleWordAlignmentEntity WHERE videoId = :videoId ORDER BY sequence ASC, ordinal ASC")
    suspend fun getByVideoId(videoId: String): List<SubtitleWordAlignmentEntity>

    @Upsert
    suspend fun upsertAll(rows: List<SubtitleWordAlignmentEntity>)

    @Query("DELETE FROM SubtitleWordAlignmentEntity WHERE videoId = :videoId AND sequence = :sequence")
    suspend fun deleteBySequence(videoId: String, sequence: Int)

    @Query("DELETE FROM SubtitleWordAlignmentEntity WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}

@Dao
interface ProviderProfileDao {
    @Query("SELECT * FROM ProviderProfileEntity WHERE enabled = 1 ORDER BY name ASC")
    fun observeEnabled(): Flow<List<ProviderProfileEntity>>

    @Upsert
    suspend fun upsert(profile: ProviderProfileEntity)

    @Query("SELECT * FROM ProviderProfileEntity WHERE kind = :kind AND presetId = :presetId ORDER BY enabled DESC LIMIT 1")
    suspend fun getByPreset(kind: String, presetId: String): ProviderProfileEntity?

    @Query("UPDATE ProviderProfileEntity SET enabled = 0 WHERE kind = :kind")
    suspend fun disableKind(kind: String)

    @Query("SELECT * FROM ProviderProfileEntity WHERE kind = :kind AND enabled = 1 ORDER BY name ASC LIMIT 1")
    suspend fun getEnabled(kind: String): ProviderProfileEntity?
}

@Dao
interface AudioChunkDao {
    @Query("SELECT * FROM AudioChunkEntity WHERE jobId = :jobId ORDER BY chunkIndex ASC")
    suspend fun getByJobId(jobId: String): List<AudioChunkEntity>

    @Upsert
    suspend fun upsertAll(chunks: List<AudioChunkEntity>)

    @Upsert
    suspend fun upsert(chunk: AudioChunkEntity)

    @Query("DELETE FROM AudioChunkEntity WHERE jobId = :jobId")
    suspend fun deleteByJobId(jobId: String)
}

@Dao
interface TranslationBatchDao {
    @Query("SELECT * FROM TranslationBatchEntity WHERE jobId = :jobId ORDER BY batchIndex ASC")
    suspend fun getByJobId(jobId: String): List<TranslationBatchEntity>

    @Upsert
    suspend fun upsert(batch: TranslationBatchEntity)
}

@Dao
interface VocabularyLlmBatchDao {
    @Query("SELECT * FROM VocabularyLlmBatchEntity WHERE videoId = :videoId AND version = :version AND phase = :phase AND inputHash = :inputHash LIMIT 1")
    suspend fun get(videoId: String, version: Int, phase: String, inputHash: String): VocabularyLlmBatchEntity?

    @Upsert
    suspend fun upsert(batch: VocabularyLlmBatchEntity)
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM LexemeEntity WHERE language = :language AND normalizedLemma = :normalized LIMIT 1")
    suspend fun findLexeme(language: String, normalized: String): LexemeEntity?

    @Upsert suspend fun upsertLexeme(lexeme: LexemeEntity)
    @Upsert suspend fun upsertSenses(senses: List<LexemeSenseEntity>)
    @Upsert suspend fun upsertOccurrence(occurrence: WordOccurrenceEntity)
    @Upsert suspend fun upsertReviewCard(card: ReviewCardEntity)

    @Query("DELETE FROM LexemeEntity WHERE id = :lexemeId")
    suspend fun deleteLexeme(lexemeId: String)

    @Query("UPDATE LexemeEntity SET lemma = :lemma, normalizedLemma = :normalizedLemma, phonetic = :phonetic, updatedAt = :updatedAt WHERE id = :lexemeId")
    suspend fun updateLexeme(
        lexemeId: String,
        lemma: String,
        normalizedLemma: String,
        phonetic: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("SELECT COUNT(*) FROM ReviewCardEntity WHERE lexemeId = :lexemeId")
    suspend fun reviewCardCount(lexemeId: String): Int

    @Query("SELECT * FROM LexemeSenseEntity WHERE lexemeId = :lexemeId ORDER BY id ASC")
    suspend fun sensesForLexeme(lexemeId: String): List<LexemeSenseEntity>

    @Query("SELECT * FROM LexemeEntity WHERE EXISTS (SELECT 1 FROM WordOccurrenceEntity o WHERE o.lexemeId = LexemeEntity.id)")
    suspend fun lexemesWithOccurrences(): List<LexemeEntity>

    @Query("SELECT DISTINCT surfaceForm FROM WordOccurrenceEntity WHERE lexemeId = :lexemeId ORDER BY createdAt ASC")
    suspend fun surfaceFormsForLexeme(lexemeId: String): List<String>

    @Query("DELETE FROM LexemeSenseEntity WHERE lexemeId = :lexemeId AND source != 'USER'")
    suspend fun deleteNonUserSenses(lexemeId: String)

    @Query(
        """
        SELECT * FROM LexemeEntity l
        WHERE EXISTS (SELECT 1 FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id)
          AND INSTR(TRIM(l.normalizedLemma), ' ') = 0
          AND NOT EXISTS (SELECT 1 FROM LexemeSenseEntity s WHERE s.lexemeId = l.id AND s.source = 'USER')
          AND NOT EXISTS (SELECT 1 FROM LexemeSenseEntity s WHERE s.lexemeId = l.id AND s.source = :currentSource)
        ORDER BY l.normalizedLemma COLLATE NOCASE ASC
        """,
    )
    suspend fun lexemesNeedingStandardSense(currentSource: String): List<LexemeEntity>

    @Query("SELECT * FROM WordOccurrenceEntity WHERE videoId = :videoId ORDER BY createdAt ASC")
    suspend fun occurrencesForVideo(videoId: String): List<WordOccurrenceEntity>

    @Query("DELETE FROM WordOccurrenceEntity WHERE videoId = :videoId")
    suspend fun deleteOccurrencesForVideo(videoId: String)

    @Query(
        """
        SELECT o.id AS occurrenceId, o.surfaceForm AS surfaceForm, o.itemType AS itemType, l.lemma AS lemma
        FROM WordOccurrenceEntity o
        INNER JOIN LexemeEntity l ON l.id = o.lexemeId
        WHERE o.difficultyLevel = 'UNKNOWN' OR TRIM(o.difficultyLevel) = ''
        """,
    )
    suspend fun occurrencesNeedingDifficulty(): List<DifficultyBackfillRow>

    @Query(
        """
        UPDATE WordOccurrenceEntity
        SET difficultyLevel = :level, difficultySource = 'LOCAL', difficultyConfidence = :confidence
        WHERE id = :occurrenceId
        """,
    )
    suspend fun updateOccurrenceDifficulty(occurrenceId: String, level: String, confidence: Float = .65f)

    @Query(
        """
        SELECT l.id AS lexemeId, l.lemma AS lemma, l.normalizedLemma AS normalizedLemma,
               MIN(c.sequence) AS firstSequence
        FROM WordOccurrenceEntity o
        INNER JOIN LexemeEntity l ON l.id = o.lexemeId
        INNER JOIN SubtitleCueEntity c ON c.id = o.cueId
        WHERE o.videoId = :videoId
        GROUP BY l.id, l.lemma, l.normalizedLemma
        ORDER BY MIN(c.sequence) ASC, l.lemma COLLATE NOCASE ASC
        """,
    )
    fun observeTranscriptVocabulary(videoId: String): Flow<List<TranscriptVocabularyRow>>

    @Query(
        """
        SELECT c.sequence AS sequence, o.lexemeId AS lexemeId, o.surfaceForm AS surfaceForm, o.itemType AS itemType,
               l.lemma AS lemma, o.translationZh AS translationZh,
               o.difficultyLevel AS difficultyLevel,
               (SELECT GROUP_CONCAT(s.definitionZh, '|') FROM LexemeSenseEntity s
                WHERE s.lexemeId = o.lexemeId AND s.definitionZh IS NOT NULL) AS definitionZh
        FROM WordOccurrenceEntity o
        INNER JOIN LexemeEntity l ON l.id = o.lexemeId
        INNER JOIN SubtitleCueEntity c ON c.id = o.cueId
        WHERE o.videoId = :videoId
        ORDER BY c.sequence ASC, LENGTH(o.surfaceForm) DESC
        """,
    )
    fun observeTranscriptOccurrences(videoId: String): Flow<List<TranscriptOccurrenceRow>>

    @Query(
        """
        SELECT o.lexemeId AS lexemeId, o.contextEn AS contextEn, o.contextZh AS contextZh,
               o.surfaceForm AS surfaceForm, o.translationZh AS translationZh,
               o.videoId AS sourceVideoId, v.title AS sourceVideoTitle, c.startMs AS sourceStartMs,
               o.createdAt AS createdAt
        FROM WordOccurrenceEntity o
        INNER JOIN VideoEntity v ON v.id = o.videoId
        INNER JOIN SubtitleCueEntity c ON c.id = o.cueId
        ORDER BY o.createdAt ASC
        """,
    )
    fun observeReviewContextCandidates(): Flow<List<ReviewContextCandidateRow>>

    @Query(
        """
        SELECT o.lexemeId AS lexemeId, o.contextEn AS contextEn, o.contextZh AS contextZh,
               o.surfaceForm AS surfaceForm, o.translationZh AS translationZh,
               o.videoId AS sourceVideoId, v.title AS sourceVideoTitle, c.startMs AS sourceStartMs,
               o.createdAt AS createdAt
        FROM WordOccurrenceEntity o
        INNER JOIN VideoEntity v ON v.id = o.videoId
        INNER JOIN SubtitleCueEntity c ON c.id = o.cueId
        ORDER BY o.createdAt ASC
        """,
    )
    suspend fun reviewContextCandidates(): List<ReviewContextCandidateRow>

}

@Dao
interface ReviewDao {
    @Query(
        """
        SELECT c.id AS cardId, c.lexemeId AS lexemeId, c.isFavorite AS isFavorite, c.repetitions AS repetitions,
               c.intervalDays AS intervalDays, c.easeFactor AS easeFactor, c.dueAt AS dueAt,
               c.lastReviewedAt AS lastReviewedAt, c.createdAt AS createdAt,
               l.lemma AS lemma, l.normalizedLemma AS normalizedLemma, l.phonetic AS phonetic,
               l.audioUrl AS audioUrl,
               (SELECT s.id FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS senseId,
               (SELECT s.pos FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS partOfSpeech,
               (SELECT s.definitionEn FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS definitionEn,
               (SELECT s.definitionZh FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS definitionZh,
               (SELECT o.contextEn FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS contextEn,
               (SELECT o.contextZh FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS contextZh,
               (SELECT o.surfaceForm FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceSurfaceForm,
               (SELECT o.translationZh FROM WordOccurrenceEntity o
                WHERE o.lexemeId = l.id AND o.translationZh IS NOT NULL AND TRIM(o.translationZh) != ''
                  AND o.id = (SELECT firstOccurrence.id FROM WordOccurrenceEntity firstOccurrence
                              WHERE firstOccurrence.lexemeId = l.id ORDER BY firstOccurrence.createdAt ASC LIMIT 1)
                  AND NOT EXISTS (SELECT 1 FROM WordOccurrenceEntity other
                                  WHERE other.cueId = o.cueId AND other.lexemeId != o.lexemeId
                                    AND other.translationZh = o.translationZh)
                ORDER BY o.createdAt ASC LIMIT 1) AS contextualMeaningZh,
               (SELECT o.videoId FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceVideoId,
               (SELECT v.title FROM WordOccurrenceEntity o INNER JOIN VideoEntity v ON v.id = o.videoId
                WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceVideoTitle,
               (SELECT cue.startMs FROM WordOccurrenceEntity o INNER JOIN SubtitleCueEntity cue ON cue.id = o.cueId
                WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceStartMs
               ,(SELECT o.difficultyLevel FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id
                 ORDER BY CASE o.difficultyLevel WHEN 'C2' THEN 6 WHEN 'C1' THEN 5 WHEN 'B2' THEN 4 WHEN 'B1' THEN 3 WHEN 'A2' THEN 2 WHEN 'A1' THEN 1 ELSE 0 END DESC,
                          o.createdAt ASC LIMIT 1) AS difficultyLevel
        FROM ReviewCardEntity c
        INNER JOIN LexemeEntity l ON l.id = c.lexemeId
        WHERE EXISTS (SELECT 1 FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id)
           OR EXISTS (SELECT 1 FROM LexemeSenseEntity s WHERE s.lexemeId = l.id AND s.source = 'USER')
        ORDER BY c.dueAt ASC, c.repetitions ASC, c.createdAt ASC, l.lemma COLLATE NOCASE ASC
        """,
    )
    fun observeCards(): Flow<List<ReviewStudyCardRow>>

    @Query(
        """
        SELECT c.id AS cardId, c.lexemeId AS lexemeId, c.isFavorite AS isFavorite, c.repetitions AS repetitions,
               c.intervalDays AS intervalDays, c.easeFactor AS easeFactor, c.dueAt AS dueAt,
               c.lastReviewedAt AS lastReviewedAt, c.createdAt AS createdAt,
               l.lemma AS lemma, l.normalizedLemma AS normalizedLemma, l.phonetic AS phonetic,
               l.audioUrl AS audioUrl,
               (SELECT s.id FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS senseId,
               (SELECT s.pos FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS partOfSpeech,
               (SELECT s.definitionEn FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS definitionEn,
               (SELECT s.definitionZh FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS definitionZh,
               (SELECT o.contextEn FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS contextEn,
               (SELECT o.contextZh FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS contextZh,
               (SELECT o.surfaceForm FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceSurfaceForm,
               (SELECT o.translationZh FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id AND o.translationZh IS NOT NULL AND TRIM(o.translationZh) != '' ORDER BY o.createdAt ASC LIMIT 1) AS contextualMeaningZh,
               (SELECT o.videoId FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceVideoId,
               (SELECT v.title FROM WordOccurrenceEntity o INNER JOIN VideoEntity v ON v.id = o.videoId WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceVideoTitle,
               (SELECT cue.startMs FROM WordOccurrenceEntity o INNER JOIN SubtitleCueEntity cue ON cue.id = o.cueId WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceStartMs,
               (SELECT o.difficultyLevel FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY CASE o.difficultyLevel WHEN 'C2' THEN 6 WHEN 'C1' THEN 5 WHEN 'B2' THEN 4 WHEN 'B1' THEN 3 WHEN 'A2' THEN 2 WHEN 'A1' THEN 1 ELSE 0 END DESC, o.createdAt ASC LIMIT 1) AS difficultyLevel
        FROM ReviewCardEntity c
        INNER JOIN LexemeEntity l ON l.id = c.lexemeId
        WHERE EXISTS (SELECT 1 FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id)
           OR EXISTS (SELECT 1 FROM LexemeSenseEntity s WHERE s.lexemeId = l.id AND s.source = 'USER')
        """,
    )
    suspend fun allCards(): List<ReviewStudyCardRow>

    @Query(
        """
        SELECT c.id AS cardId, c.lexemeId AS lexemeId, c.isFavorite AS isFavorite, c.repetitions AS repetitions,
               c.intervalDays AS intervalDays, c.easeFactor AS easeFactor, c.dueAt AS dueAt,
               c.lastReviewedAt AS lastReviewedAt, c.createdAt AS createdAt,
               l.lemma AS lemma, l.normalizedLemma AS normalizedLemma, l.phonetic AS phonetic,
               l.audioUrl AS audioUrl,
               (SELECT s.id FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS senseId,
               (SELECT s.pos FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS partOfSpeech,
               (SELECT s.definitionEn FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS definitionEn,
               (SELECT s.definitionZh FROM LexemeSenseEntity s WHERE s.lexemeId = l.id ORDER BY CASE s.source WHEN 'USER' THEN 0 WHEN 'standard-en-zh-v2' THEN 1 WHEN 'local-en-zh' THEN 2 ELSE 3 END, CASE WHEN s.definitionZh IS NOT NULL AND TRIM(s.definitionZh) != '' THEN 0 ELSE 1 END, s.id ASC LIMIT 1) AS definitionZh,
               (SELECT o.contextEn FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS contextEn,
               (SELECT o.contextZh FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS contextZh,
               (SELECT o.surfaceForm FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceSurfaceForm,
               (SELECT o.translationZh FROM WordOccurrenceEntity o
                WHERE o.lexemeId = l.id AND o.translationZh IS NOT NULL AND TRIM(o.translationZh) != ''
                  AND o.id = (SELECT firstOccurrence.id FROM WordOccurrenceEntity firstOccurrence
                              WHERE firstOccurrence.lexemeId = l.id ORDER BY firstOccurrence.createdAt ASC LIMIT 1)
                  AND NOT EXISTS (SELECT 1 FROM WordOccurrenceEntity other
                                  WHERE other.cueId = o.cueId AND other.lexemeId != o.lexemeId
                                    AND other.translationZh = o.translationZh)
                ORDER BY o.createdAt ASC LIMIT 1) AS contextualMeaningZh,
               (SELECT o.videoId FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceVideoId,
               (SELECT v.title FROM WordOccurrenceEntity o INNER JOIN VideoEntity v ON v.id = o.videoId
                WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceVideoTitle,
               (SELECT cue.startMs FROM WordOccurrenceEntity o INNER JOIN SubtitleCueEntity cue ON cue.id = o.cueId
                WHERE o.lexemeId = l.id ORDER BY o.createdAt ASC LIMIT 1) AS sourceStartMs
               ,(SELECT o.difficultyLevel FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id
                 ORDER BY CASE o.difficultyLevel WHEN 'C2' THEN 6 WHEN 'C1' THEN 5 WHEN 'B2' THEN 4 WHEN 'B1' THEN 3 WHEN 'A2' THEN 2 WHEN 'A1' THEN 1 ELSE 0 END DESC,
                          o.createdAt ASC LIMIT 1) AS difficultyLevel
        FROM ReviewCardEntity c
        INNER JOIN LexemeEntity l ON l.id = c.lexemeId
        WHERE c.dueAt <= :now
          AND (EXISTS (SELECT 1 FROM WordOccurrenceEntity o WHERE o.lexemeId = l.id)
               OR EXISTS (SELECT 1 FROM LexemeSenseEntity s WHERE s.lexemeId = l.id AND s.source = 'USER'))
        ORDER BY CASE WHEN c.repetitions = 0 THEN 1 ELSE 0 END ASC,
                 c.dueAt ASC, c.createdAt ASC, l.lemma COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun dueCards(now: Long, limit: Int): List<ReviewStudyCardRow>

    @Query("SELECT * FROM ReviewCardEntity WHERE id = :cardId LIMIT 1")
    suspend fun card(cardId: String): ReviewCardEntity?

    @Query("UPDATE ReviewCardEntity SET isFavorite = :isFavorite WHERE id = :cardId")
    suspend fun setFavorite(cardId: String, isFavorite: Boolean)

    @Update
    suspend fun updateCard(card: ReviewCardEntity)

    @Insert
    suspend fun insertLog(log: ReviewLogEntity)

    @Query("DELETE FROM ReviewLogEntity WHERE id = :logId")
    suspend fun deleteLog(logId: String)

    @Query("SELECT * FROM ReviewLogEntity ORDER BY reviewedAt ASC, id ASC")
    fun observeLogs(): Flow<List<ReviewLogEntity>>
}

data class ReviewStudyCardRow(
    val cardId: String,
    val lexemeId: String,
    val isFavorite: Boolean = false,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val createdAt: Long,
    val lemma: String,
    val normalizedLemma: String,
    val phonetic: String?,
    val audioUrl: String?,
    val senseId: String?,
    val partOfSpeech: String?,
    val definitionEn: String?,
    val definitionZh: String?,
    val contextEn: String?,
    val contextZh: String?,
    val sourceSurfaceForm: String?,
    val contextualMeaningZh: String?,
    val sourceVideoId: String?,
    val sourceVideoTitle: String?,
    val sourceStartMs: Long?,
    val difficultyLevel: String?,
)

data class TranscriptVocabularyRow(
    val lexemeId: String,
    val lemma: String,
    val normalizedLemma: String,
    val firstSequence: Int,
)

data class TranscriptOccurrenceRow(
    val sequence: Int,
    val lexemeId: String,
    val surfaceForm: String,
    val itemType: String,
    val lemma: String,
    val translationZh: String?,
    val definitionZh: String?,
    val difficultyLevel: String,
)

data class ReviewContextCandidateRow(
    val lexemeId: String,
    val contextEn: String,
    val contextZh: String?,
    val surfaceForm: String,
    val translationZh: String?,
    val sourceVideoId: String,
    val sourceVideoTitle: String,
    val sourceStartMs: Long,
    val createdAt: Long,
)

data class DifficultyBackfillRow(
    val occurrenceId: String,
    val surfaceForm: String,
    val itemType: String,
    val lemma: String,
)

@Dao
interface DictionaryCacheDao {
    @Query("SELECT * FROM DictionaryCacheEntity WHERE query = :query LIMIT 1")
    suspend fun get(query: String): DictionaryCacheEntity?

    @Upsert suspend fun upsert(cache: DictionaryCacheEntity)
}

@Database(
    entities = [
        VideoEntity::class,
        ProcessingJobEntity::class,
        SubtitleTrackEntity::class,
        SubtitleCueEntity::class,
        SubtitleWordAlignmentEntity::class,
        ProviderProfileEntity::class,
        AudioChunkEntity::class,
        TranslationBatchEntity::class,
        VocabularyLlmBatchEntity::class,
        LexemeEntity::class,
        LexemeSenseEntity::class,
        WordOccurrenceEntity::class,
        ReviewCardEntity::class,
        ReviewLogEntity::class,
        DictionaryCacheEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@androidx.room.TypeConverters(AppTypeConverters::class)
abstract class SubLingoDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun subtitleTrackDao(): SubtitleTrackDao
    abstract fun subtitleCueDao(): SubtitleCueDao
    abstract fun subtitleWordAlignmentDao(): SubtitleWordAlignmentDao
    abstract fun providerProfileDao(): ProviderProfileDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun translationBatchDao(): TranslationBatchDao
    abstract fun vocabularyLlmBatchDao(): VocabularyLlmBatchDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun reviewDao(): ReviewDao
    abstract fun dictionaryCacheDao(): DictionaryCacheDao
}
