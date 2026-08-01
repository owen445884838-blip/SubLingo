package com.sublingo.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.sublingo.app.data.db.AudioChunkDao
import com.sublingo.app.data.db.AudioChunkEntity
import com.sublingo.app.data.db.ProcessingJobDao
import com.sublingo.app.data.db.ProviderProfileDao
import com.sublingo.app.data.db.SubtitleCueDao
import com.sublingo.app.data.db.SubtitleCueEntity
import com.sublingo.app.data.db.SubtitleTrackDao
import com.sublingo.app.data.db.SubtitleTrackEntity
import com.sublingo.app.data.db.SubtitleWordAlignmentDao
import com.sublingo.app.data.db.SubtitleWordAlignmentEntity
import com.sublingo.app.data.db.TranslationBatchDao
import com.sublingo.app.data.db.TranslationBatchEntity
import com.sublingo.app.data.db.VideoDao
import com.sublingo.app.data.media.AudioSegmenter
import com.sublingo.app.data.media.TranslationAlignment
import com.sublingo.app.data.media.TranslationResponseParser
import com.sublingo.app.data.media.TranslationWordMapRepair
import com.sublingo.app.data.media.YoutubeDlRuntime
import com.sublingo.app.security.SecretStore
import com.sublingo.app.data.remote.SpeechToTextClient
import com.sublingo.app.data.settings.OPENAI_AUDIO_CHUNK_DURATION_MS
import com.sublingo.app.data.settings.SttProtocol
import com.sublingo.app.data.settings.sttProtocol
import com.sublingo.app.data.settings.upgradeKnownLlmPresetModel
import com.sublingo.app.data.remote.OpenAiCompatibleClient
import com.sublingo.app.data.remote.LlmHttpException
import com.sublingo.app.data.remote.LlmEmptyContentException
import com.sublingo.app.data.remote.DictionaryClient
import com.sublingo.app.data.vocabulary.SelectedVocabulary
import com.sublingo.app.data.vocabulary.VocabularyPreprocessor
import com.sublingo.app.data.vocabulary.VocabularySelection
import com.sublingo.app.data.vocabulary.VocabularyItemType
import com.sublingo.app.data.vocabulary.VocabularyPipelineContract
import com.sublingo.app.data.vocabulary.VocabularyExecutionGate
import com.sublingo.app.data.vocabulary.VocabularyLlmPolicy
import com.sublingo.app.data.vocabulary.VocabularyRequestBudget
import com.sublingo.app.data.vocabulary.ContextualChineseMeaningResolver
import com.sublingo.app.data.vocabulary.PhraseAuditPlanner
import com.sublingo.app.data.vocabulary.LlmJsonResponseParser
import com.sublingo.app.data.vocabulary.StandardDictionarySenseRepairer
import com.sublingo.app.data.vocabulary.VocabularyLexemeIdentity
import com.sublingo.app.data.vocabulary.VocabularyLemmaRepairPolicy
import com.sublingo.app.data.vocabulary.VocabularyDifficulty
import com.sublingo.app.data.vocabulary.VocabularyDifficultyClassifier
import com.sublingo.app.data.storage.AppStorageCleaner
import com.sublingo.app.data.db.LexemeEntity
import com.sublingo.app.data.db.ReviewCardEntity
import com.sublingo.app.data.db.VocabularyDao
import com.sublingo.app.data.db.VocabularyLlmBatchDao
import com.sublingo.app.data.db.VocabularyLlmBatchEntity
import com.sublingo.app.data.db.WordOccurrenceEntity
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.domain.model.ProcessingState
import com.sublingo.app.domain.model.PipelinePolicies
import com.sublingo.app.work.BackgroundWorkNotifications
import com.sublingo.app.work.BackgroundRetryPolicy
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

abstract class PipelineWorker(
    context: Context,
    params: WorkerParameters,
    protected val jobDao: ProcessingJobDao,
) : CoroutineWorker(context, params) {
    protected val videoId get() = inputData.getString(VIDEO_ID).orEmpty()
    protected val jobId get() = inputData.getString(JOB_ID) ?: "job-$videoId"

    protected suspend fun running(stage: ProcessingStage, progress: Int) {
        setForeground(createForegroundInfo(stage, progress))
        val current = jobDao.getById(jobId) ?: return
        jobDao.upsert(
            current.copy(
                currentStage = stage,
                state = ProcessingState.RUNNING,
                progress = progress,
                lastErrorCode = null,
                lastErrorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    protected suspend fun <T> withForegroundHeartbeat(
        stage: ProcessingStage,
        progress: Int,
        block: suspend () -> T,
    ): T = coroutineScope {
        running(stage, progress)
        val heartbeat = launch {
            while (true) {
                delay(20_000)
                running(stage, progress)
            }
        }
        try {
            block()
        } finally {
            heartbeat.cancelAndJoin()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(ProcessingStage.SUBTITLE_DISCOVERY, 0)
    }

    private fun createForegroundInfo(stage: ProcessingStage, progress: Int): ForegroundInfo {
        val text = when (stage) {
            ProcessingStage.METADATA -> "正在读取视频信息"
            ProcessingStage.DOWNLOAD -> "正在下载视频"
            ProcessingStage.SUBTITLE_DISCOVERY -> "正在准备语音转录"
            ProcessingStage.AUDIO_EXTRACTION -> "正在提取与分片音频"
            ProcessingStage.TRANSCRIPTION -> "正在后台转录字幕"
            ProcessingStage.TRANSLATION -> "正在后台翻译字幕"
            ProcessingStage.VOCABULARY -> "正在后台生成生词"
        }
        return BackgroundWorkNotifications.foregroundInfo(
            context = applicationContext,
            notificationId = 30_000 + (videoId.hashCode() and 0x3fff),
            title = "SubLingo 视频处理",
            text = text,
            progress = progress,
            cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
        )
    }

    protected suspend fun failed(stage: ProcessingStage, error: Throwable): Result {
        error.throwIfCancellation()
        Log.e("${stage.name}Worker", "Pipeline stage failed for video=$videoId job=$jobId", error)
        val current = jobDao.getById(jobId)
        val needsConfiguration = error is UserConfigurationRequiredException
        val shouldRetry = !needsConfiguration && runAttemptCount < 5 && BackgroundRetryPolicy.isTransientNetworkFailure(error)
        if (current != null) jobDao.upsert(
            current.copy(
                currentStage = stage,
                state = when {
                    needsConfiguration -> ProcessingState.WAITING_FOR_USER
                    shouldRetry -> ProcessingState.PENDING
                    else -> ProcessingState.FAILED
                },
                attemptCount = runAttemptCount + 1,
                lastErrorCode = when {
                    needsConfiguration -> "PROVIDER_CONFIGURATION_REQUIRED"
                    shouldRetry -> "NETWORK_RETRY"
                    else -> "PIPELINE_FAILED"
                },
                lastErrorMessage = if (shouldRetry) "网络暂时不可用，任务将在后台自动重试" else error.message,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return if (shouldRetry) Result.retry() else Result.failure(workDataOf(ERROR to (error.message ?: "处理失败")))
    }

    companion object {
        const val VIDEO_ID = "video_id"
        const val JOB_ID = "job_id"
        const val NOTIFY_ON_COMPLETION = "notify_on_completion"
        const val ERROR = "error"
    }
}

private class UserConfigurationRequiredException(message: String) : IllegalStateException(message)

private fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
}

@HiltWorker
class SubtitleDiscoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    jobDao: ProcessingJobDao,
) : PipelineWorker(context, params, jobDao) {
    override suspend fun doWork(): Result {
        return try {
            running(ProcessingStage.SUBTITLE_DISCOVERY, 20)
            // Compatibility shim for WorkManager chains persisted by older app builds. Platform
            // captions are intentionally never fetched; the next worker always extracts audio for
            // the configured in-app ASR provider.
            Result.success(workDataOf("subtitle_found" to false, "asr_required" to true))
        } catch (error: Throwable) { failed(ProcessingStage.SUBTITLE_DISCOVERY, error) }
    }
}

@HiltWorker
class ExtractAudioWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    jobDao: ProcessingJobDao,
    private val videoDao: VideoDao,
    private val trackDao: SubtitleTrackDao,
    private val cueDao: SubtitleCueDao,
    private val chunkDao: AudioChunkDao,
    private val providerDao: ProviderProfileDao,
    private val audioSegmenter: AudioSegmenter,
    private val youtubeDlRuntime: YoutubeDlRuntime,
    private val secretStore: SecretStore,
) : PipelineWorker(context, params, jobDao) {
    override suspend fun doWork(): Result {
        return try {
            val usableEnglishTrack = trackDao.getByVideoId(videoId)
                .filter { it.language.startsWith("en", true) && it.kind == "ASR" }
                .any { cueDao.getByTrackId(it.id).isNotEmpty() }
            if (usableEnglishTrack) return Result.success()
            running(ProcessingStage.AUDIO_EXTRACTION, 35)
            val sttProfile = providerDao.getEnabled("STT")
            val chunkDuration = if (sttProfile?.let(::sttProtocol) in setOf(SttProtocol.OPENAI_CHAT_AUDIO, SttProtocol.OPENAI_TRANSCRIPTION)) {
                OPENAI_AUDIO_CHUNK_DURATION_MS
            } else PipelinePolicies.DEFAULT_CHUNK_DURATION_MS
            val existing = chunkDao.getByJobId(jobId)
            val existingMatches = existing.isNotEmpty() && existing.all { chunk ->
                File(chunk.filePath).isFile && (chunk.durationMs <= chunkDuration + 5_000L)
            }
            if (existingMatches) return Result.success()
            if (existing.isNotEmpty()) {
                chunkDao.deleteByJobId(jobId)
                existing.map { File(it.filePath) }.forEach(File::delete)
            }
            val video = requireNotNull(videoDao.getById(videoId)) { "视频不存在" }
            PipelinePolicies.asrMode(video.durationMs)
            val input = File(requireNotNull(video.filePath) { "视频文件不存在" })
            val audioDir = File(applicationContext.getExternalFilesDir(null), "audio/$jobId")
            val downloadedCompanionAudio = input.parentFile?.listFiles().orEmpty()
                .filter { file ->
                    file.isFile && file != input && file.extension.lowercase() in setOf("m4a", "aac", "mp3", "opus", "ogg") && file.length() > 0L
                }
                .maxByOrNull(File::length)
            val segments = if (downloadedCompanionAudio != null) {
                Log.i("ExtractAudioWorker", "Using downloaded companion audio ${downloadedCompanionAudio.name} (${downloadedCompanionAudio.length()} bytes)")
                audioSegmenter.split(downloadedCompanionAudio, audioDir, chunkDuration)
            } else runCatching {
                audioSegmenter.split(input, audioDir, chunkDuration)
            }.getOrElse { extractionError ->
                val sourceUrl = video.originalUrl
                    ?: throw IllegalStateException("媒体不包含可用音频轨道，且本地导入视频无法在线补充音频", extractionError)
                val repairedAudio = downloadMissingAudio(sourceUrl, audioDir)
                audioSegmenter.split(repairedAudio, audioDir, chunkDuration)
            }
            require(segments.isNotEmpty()) { "音频分片生成结果为空" }
            chunkDao.upsertAll(segments.map { AudioChunkEntity("$jobId-${it.index}", jobId, it.index, it.startOffsetMs, it.durationMs, it.file.absolutePath) })
            val persistedChunks = chunkDao.getByJobId(jobId)
            require(persistedChunks.size == segments.size) {
                "音频分片入库失败：生成 ${segments.size}，入库 ${persistedChunks.size}"
            }
            Log.i("ExtractAudioWorker", "Persisted ${persistedChunks.size} audio chunks for job=$jobId")
            Result.success()
        } catch (error: Throwable) { failed(ProcessingStage.AUDIO_EXTRACTION, error) }
    }

    private suspend fun downloadMissingAudio(url: String, audioDir: File): File {
        youtubeDlRuntime.ensureInitialized()
        audioDir.mkdirs()
        audioDir.listFiles().orEmpty().filter { it.name.startsWith("source-audio") }.forEach(File::delete)
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-update")
            addOption("--no-playlist")
            addOption("--hls-prefer-native")
            addOption("-f", "bestaudio[acodec!=none]/bestaudio")
            addOption("-o", File(audioDir, "source-audio.%(ext)s").absolutePath)
            secretStore.read("download.cookie.default")?.takeIf { it.isNotBlank() }?.let { cookie ->
                addOption("--add-header", "Cookie:$cookie")
            }
        }
        YoutubeDL.getInstance().execute(request, "audio-repair-$videoId")
        return audioDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("source-audio.") && it.length() > 0L }
            .maxByOrNull(File::length)
            ?: error("无法从原视频补充下载音频流")
    }
}

@HiltWorker
class TranscribeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    jobDao: ProcessingJobDao,
    private val trackDao: SubtitleTrackDao,
    private val cueDao: SubtitleCueDao,
    private val chunkDao: AudioChunkDao,
    private val providerDao: ProviderProfileDao,
    private val videoDao: VideoDao,
    private val speechToText: SpeechToTextClient,
) : PipelineWorker(context, params, jobDao) {
    override suspend fun doWork(): Result {
        return try {
            running(ProcessingStage.TRANSCRIPTION, 55)
            val configuredProfile = providerDao.getEnabled("STT")
                ?: throw UserConfigurationRequiredException("请先在设置中配置 STT 供应商，然后重试语音字幕转录")
            val duration = requireNotNull(videoDao.getById(videoId)) { "视频不存在" }.durationMs
            val profile = if (PipelinePolicies.asrMode(duration) == "STANDARD" && configuredProfile.presetId == "doubao-flash") {
                configuredProfile.copy(
                    baseUrl = configuredProfile.baseUrl?.removeSuffix("/flash"),
                    resourceId = "volc.bigasr.auc",
                )
            } else configuredProfile
            var chunks = chunkDao.getByJobId(jobId)
            if (chunks.isEmpty()) {
                val recoveredFiles = File(applicationContext.getExternalFilesDir(null), "audio/$jobId")
                    .listFiles().orEmpty()
                    .filter { it.isFile && it.name.matches(Regex("chunk-\\d{3}\\.m4a")) && it.length() > 0L }
                    .sortedBy(File::getName)
                if (recoveredFiles.isNotEmpty()) {
                    val recoveredChunkDuration = if (sttProtocol(profile) in setOf(SttProtocol.OPENAI_CHAT_AUDIO, SttProtocol.OPENAI_TRANSCRIPTION)) {
                        OPENAI_AUDIO_CHUNK_DURATION_MS
                    } else PipelinePolicies.DEFAULT_CHUNK_DURATION_MS
                    chunks = recoveredFiles.mapIndexed { index, file ->
                        AudioChunkEntity(
                            id = "$jobId-$index",
                            jobId = jobId,
                            chunkIndex = index,
                            startOffsetMs = index * recoveredChunkDuration,
                            durationMs = minOf(
                                recoveredChunkDuration,
                                (duration - index * recoveredChunkDuration).coerceAtLeast(0L),
                            ),
                            filePath = file.absolutePath,
                        )
                    }
                    chunkDao.upsertAll(chunks)
                    Log.i("TranscribeWorker", "Recovered ${chunks.size} audio chunks from disk for job=$jobId")
                }
            }
            require(chunks.isNotEmpty()) { "没有可转录的音频分片" }
            val trackId = "track-$videoId-en-asr"
            if (chunks.all { it.state == "SUCCEEDED" } && cueDao.getByTrackId(trackId).isNotEmpty()) return Result.success()
            // A newly generated ASR track invalidates vocabulary occurrences tied to the previous
            // platform-caption cue IDs. VocabWorker will rebuild them after translation completes.
            videoDao.updateVocabularyVersion(videoId, 0)
            trackDao.upsert(SubtitleTrackEntity(trackId, videoId, "en", "ASR", providerId = profile.id, model = profile.model ?: profile.resourceId))
            val stored = cueDao.getByTrackId(trackId).toMutableList()
            var sequence = stored.size
            chunks.forEachIndexed { index, chunk ->
                if (chunk.state == "SUCCEEDED") return@forEachIndexed
                try {
                    running(ProcessingStage.TRANSCRIPTION, 55 + (index * 15 / chunks.size.coerceAtLeast(1)))
                    val utterances = withForegroundHeartbeat(
                        ProcessingStage.TRANSCRIPTION,
                        55 + (index * 15 / chunks.size.coerceAtLeast(1)),
                    ) {
                        speechToText.transcribe(profile, File(chunk.filePath), chunk.durationMs)
                    }
                    val cues = utterances.map { utterance ->
                        SubtitleCueEntity("$trackId-$sequence", trackId, sequence++, chunk.startOffsetMs + utterance.startMs, chunk.startOffsetMs + utterance.endMs, utterance.text)
                    }
                    cueDao.upsertAll(cues)
                    chunkDao.upsert(chunk.copy(state = "SUCCEEDED", lastError = null, updatedAt = System.currentTimeMillis()))
                } catch (error: Throwable) {
                    chunkDao.upsert(chunk.copy(state = "FAILED", attemptCount = chunk.attemptCount + 1, lastError = error.message, updatedAt = System.currentTimeMillis()))
                    throw error
                }
            }
            Result.success(workDataOf("track_id" to trackId))
        } catch (error: Throwable) { failed(ProcessingStage.TRANSCRIPTION, error) }
    }
}

@HiltWorker
class TranslateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    jobDao: ProcessingJobDao,
    private val trackDao: SubtitleTrackDao,
    private val cueDao: SubtitleCueDao,
    private val providerDao: ProviderProfileDao,
    private val batchDao: TranslationBatchDao,
    private val wordAlignmentDao: SubtitleWordAlignmentDao,
    private val videoDao: VideoDao,
    private val llm: OpenAiCompatibleClient,
) : PipelineWorker(context, params, jobDao) {
    private var translationContextBySequence: Map<Int, SubtitleCueEntity> = emptyMap()

    override suspend fun doWork(): Result {
        return try {
            running(ProcessingStage.TRANSLATION, 75)
            val tracks = trackDao.getByVideoId(videoId)
            val source = requireNotNull(tracks.firstOrNull { it.language.startsWith("en", true) && it.kind == "ASR" }) { "缺少 ASR 英文字幕" }
            val storedProfile = providerDao.getEnabled("LLM")
                ?: throw UserConfigurationRequiredException("英文字幕已生成，请先在设置中配置 LLM 模型供应商，然后重试翻译")
            val profile = upgradeKnownLlmPresetModel(storedProfile)
            if (profile != storedProfile) providerDao.upsert(profile)
            val sourceCues = cueDao.getByTrackId(source.id)
            translationContextBySequence = sourceCues.associateBy { it.sequence }
            val targetId = "track-$videoId-zh"
            val existingTarget = tracks.firstOrNull { it.id == targetId && it.sourceTrackId == source.id }
            val expectedSequences = sourceCues.map { it.sequence }.toSet()
            val storedSequences = cueDao.getByTrackId(targetId).map { it.sequence }.toSet()
            val mappedSequences = wordAlignmentDao.getByVideoId(videoId).map { it.sequence }.toSet()
            if (existingTarget?.promptVersion == TRANSLATION_PROMPT_VERSION && storedSequences == expectedSequences && mappedSequences.containsAll(expectedSequences)) {
                return Result.success()
            }
            if (existingTarget?.sourceTrackId != source.id || existingTarget.promptVersion != TRANSLATION_PROMPT_VERSION) {
                wordAlignmentDao.deleteByVideoId(videoId)
                videoDao.updateVocabularyVersion(videoId, 0)
            }
            trackDao.upsert(SubtitleTrackEntity(targetId, videoId, "zh-CN", "TRANSLATION", source.id, profile.id, profile.model, TRANSLATION_PROMPT_VERSION))
            val existingBatches = batchDao.getByJobId(jobId)
            val translationBatches = TranslationAlignment.batchesForProvider(sourceCues, profile.presetId.orEmpty())
            translationBatches.forEachIndexed { batchIndex, batch ->
                running(ProcessingStage.TRANSLATION, 75 + (batchIndex * 13 / translationBatches.size.coerceAtLeast(1)))
                val batchId = "$jobId-translation-$batchIndex"
                val existing = existingBatches.firstOrNull {
                    it.id == batchId &&
                        it.sourceTrackId == source.id &&
                        it.firstSequence == batch.first().sequence &&
                        it.lastSequence == batch.last().sequence
                }
                val completedSequences = wordAlignmentDao.getByVideoId(videoId).mapTo(mutableSetOf()) { it.sequence }
                if (existing?.state == "SUCCEEDED" && batch.all { it.sequence in completedSequences }) return@forEachIndexed
                val pendingBatch = batch.filter { it.sequence !in completedSequences }
                if (pendingBatch.isEmpty()) {
                    batchDao.upsert(
                        (existing ?: TranslationBatchEntity(batchId, jobId, batchIndex, source.id, batch.first().sequence, batch.last().sequence))
                            .copy(state = "SUCCEEDED", lastError = null, updatedAt = System.currentTimeMillis()),
                    )
                    return@forEachIndexed
                }
                val record = existing ?: TranslationBatchEntity(batchId, jobId, batchIndex, source.id, batch.first().sequence, batch.last().sequence)
                try {
                    translateResilient(profile, pendingBatch) { completed ->
                        persistTranslationResults(targetId, pendingBatch, completed)
                    }
                    batchDao.upsert(record.copy(state = "SUCCEEDED", lastError = null, updatedAt = System.currentTimeMillis()))
                } catch (error: Throwable) {
                    error.throwIfCancellation()
                    batchDao.upsert(record.copy(state = "FAILED", attemptCount = record.attemptCount + 1, lastError = error.message, updatedAt = System.currentTimeMillis()))
                    throw error
                }
            }
            jobDao.getById(jobId)?.let { jobDao.upsert(it.copy(currentStage = ProcessingStage.TRANSLATION, state = ProcessingState.RUNNING, progress = 88, updatedAt = System.currentTimeMillis())) }
            Result.success()
        } catch (error: Throwable) { failed(ProcessingStage.TRANSLATION, error) }
    }

    private suspend fun translateWithTargetedRetry(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        batch: List<SubtitleCueEntity>,
    ): List<com.sublingo.app.data.media.ParsedTranslation> {
        val first = repairTranslationWordMaps(batch, requestTranslation(profile, batch))
        val invalid = invalidTranslationIndexes(batch, first)
        val accepted = first.filter { it.item.index !in invalid && batch.any { cue -> cue.sequence == it.item.index } }.associateBy { it.item.index }.toMutableMap()
        val missing = batch.filter { it.sequence in invalid }
        if (missing.isNotEmpty()) {
            val groups = if (missing.size == 1) listOf(missing) else missing.chunked((missing.size + 1) / 2)
            groups.forEach { group ->
                val retry = repairTranslationWordMaps(group, requestTranslation(profile, group))
                val retryInvalid = invalidTranslationIndexes(group, retry)
                retry.filter { it.item.index !in retryInvalid }.forEach { accepted[it.item.index] = it }
                group.filter { it.sequence in retryInvalid }.forEach { cue ->
                    val single = requestTranslation(profile, listOf(cue))
                    val structuralInvalid = TranslationAlignment.validate(
                        setOf(cue.sequence),
                        single.map { it.item },
                    )
                    check(structuralInvalid.isEmpty()) { "翻译缺少 index ${cue.sequence}" }
                    val parsed = single.single { it.item.index == cue.sequence }
                    val repaired = TranslationWordMapRepair.fillUncoveredChinese(cue.text, parsed)
                    val remainingInvalid = invalidTranslationIndexes(listOf(cue), listOf(repaired))
                    check(remainingInvalid.isEmpty()) { "逐词映射缺少 index ${cue.sequence}" }
                    if (repaired.wordPairs.size > parsed.wordPairs.size) {
                        Log.w(
                            "TranslateWorker",
                            "Filled ${repaired.wordPairs.size - parsed.wordPairs.size} uncovered translation spans " +
                                "for video=$videoId sequence=${cue.sequence}",
                        )
                    }
                    accepted[cue.sequence] = repaired
                }
            }
        }
        check(accepted.keys == batch.map { it.sequence }.toSet()) { "翻译 index 未完整对齐" }
        return accepted.values.sortedBy { it.item.index }
    }

    private fun repairTranslationWordMaps(
        source: List<SubtitleCueEntity>,
        translated: List<com.sublingo.app.data.media.ParsedTranslation>,
    ): List<com.sublingo.app.data.media.ParsedTranslation> {
        val sourceBySequence = source.associateBy { it.sequence }
        return translated.map { parsed ->
            val sourceText = sourceBySequence[parsed.item.index]?.text
            if (sourceText.isNullOrBlank() || parsed.item.text.isBlank()) parsed
            else TranslationWordMapRepair.fillUncoveredChinese(sourceText, parsed)
        }
    }

    private suspend fun translateResilient(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        batch: List<SubtitleCueEntity>,
        depth: Int = 0,
        persist: suspend (List<com.sublingo.app.data.media.ParsedTranslation>) -> Unit,
    ): List<com.sublingo.app.data.media.ParsedTranslation> {
        return try {
            translateWithTargetedRetry(profile, batch).also { persist(it) }
        } catch (error: Throwable) {
            error.throwIfCancellation()
            if (!error.isTranslationTimeout()) throw error
            if (batch.size == 1 && batch.single().text.length >= MIN_FRAGMENTABLE_CUE_CHARS) {
                Log.w(
                    "TranslateWorker",
                    "Single-cue translation timed out for video=$videoId sequence=${batch.single().sequence} " +
                        "chars=${batch.single().text.length}; splitting text",
                )
                return translateCueInFragments(profile, batch.single()).let { translated ->
                    persist(listOf(translated))
                    listOf(translated)
                }
            }
            if (batch.size <= 1 || depth >= MAX_TRANSLATION_SPLIT_DEPTH) throw error

            val midpoint = batch.size / 2
            val halves = listOf(batch.subList(0, midpoint), batch.subList(midpoint, batch.size))
            Log.w(
                "TranslateWorker",
                "Translation timed out for video=$videoId cues=${batch.size} " +
                    "range=${batch.first().sequence}-${batch.last().sequence} depth=$depth; splitting",
            )
            halves.flatMap { half -> translateResilient(profile, half, depth + 1, persist) }
                .sortedBy { it.item.index }
        }
    }

    private suspend fun translateCueInFragments(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        cue: SubtitleCueEntity,
    ): com.sublingo.app.data.media.ParsedTranslation {
        val fragments = splitTextFragment(TextFragment(0, cue.text), TARGET_FRAGMENT_CHARS)
        val translated = fragments.flatMap { fragment ->
            translateTextFragment(profile, cue, fragment, 0)
        }
        val combined = com.sublingo.app.data.media.ParsedTranslation(
            item = TranslationAlignment.Item(cue.sequence, translated.joinToString("") { it.item.text }),
            wordPairs = translated.flatMap { it.wordPairs },
        )
        check(invalidTranslationIndexes(listOf(cue), listOf(combined)).isEmpty()) {
            "拆分翻译或逐词映射缺少 index ${cue.sequence}"
        }
        return combined
    }

    private suspend fun translateTextFragment(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        original: SubtitleCueEntity,
        fragment: TextFragment,
        depth: Int,
    ): List<com.sublingo.app.data.media.ParsedTranslation> {
        val fragmentCue = original.copy(text = fragment.text)
        return try {
            val parsed = translateWithTargetedRetry(profile, listOf(fragmentCue)).single()
            listOf(
                parsed.copy(
                    wordPairs = parsed.wordPairs.map { pair ->
                        pair.copy(
                            englishOccurrence = absoluteEnglishOccurrence(
                                original.text,
                                fragment,
                                pair.english,
                                pair.englishOccurrence,
                            ),
                        )
                    },
                ),
            )
        } catch (error: Throwable) {
            error.throwIfCancellation()
            if (!error.isTranslationTimeout() || fragment.text.length < MIN_FRAGMENTABLE_CUE_CHARS || depth >= MAX_FRAGMENT_SPLIT_DEPTH) throw error
            splitTextFragment(fragment, (fragment.text.length / 2).coerceAtLeast(MIN_FRAGMENTABLE_CUE_CHARS / 2))
                .flatMap { child -> translateTextFragment(profile, original, child, depth + 1) }
        }
    }

    private suspend fun persistTranslationResults(
        targetId: String,
        source: List<SubtitleCueEntity>,
        translated: List<com.sublingo.app.data.media.ParsedTranslation>,
    ) {
        cueDao.upsertAll(translated.map { parsed ->
            val original = source.first { it.sequence == parsed.item.index }
            SubtitleCueEntity("$targetId-${parsed.item.index}", targetId, parsed.item.index, original.startMs, original.endMs, parsed.item.text)
        })
        translated.forEach { parsed ->
            wordAlignmentDao.deleteBySequence(videoId, parsed.item.index)
            val sourceText = source.first { it.sequence == parsed.item.index }.text
            wordAlignmentDao.upsertAll(
                parsed.wordPairs.mapIndexedNotNull { ordinal, pair ->
                    pair.takeIf {
                        containsEnglishSurface(sourceText, it.english) && parsed.item.text.contains(it.chinese)
                    }?.let {
                        SubtitleWordAlignmentEntity(
                            id = "align-$videoId-${parsed.item.index}-$ordinal",
                            videoId = videoId,
                            sequence = parsed.item.index,
                            ordinal = ordinal,
                            englishSurface = it.english,
                            chineseSurface = it.chinese,
                            englishOccurrence = it.englishOccurrence,
                            source = it.source,
                        )
                    }
                },
            )
        }
    }

    private fun splitTextFragment(fragment: TextFragment, targetChars: Int): List<TextFragment> {
        if (fragment.text.length <= targetChars) return listOf(fragment)
        val result = mutableListOf<TextFragment>()
        var cursor = 0
        while (cursor < fragment.text.length) {
            val limit = (cursor + targetChars).coerceAtMost(fragment.text.length)
            var end = if (limit == fragment.text.length) limit else {
                val punctuation = (limit downTo cursor + targetChars / 2).firstOrNull { index ->
                    fragment.text.getOrNull(index - 1) in FRAGMENT_BOUNDARIES
                }
                punctuation ?: (limit downTo cursor + targetChars / 2).firstOrNull { index ->
                    fragment.text.getOrNull(index - 1)?.isWhitespace() == true
                } ?: limit
            }
            if (end <= cursor) end = limit
            val raw = fragment.text.substring(cursor, end)
            val leading = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val text = raw.trim()
            if (text.isNotEmpty()) result += TextFragment(fragment.startOffset + cursor + leading, text)
            cursor = end
            while (cursor < fragment.text.length && fragment.text[cursor].isWhitespace()) cursor++
        }
        return result
    }

    private fun absoluteEnglishOccurrence(
        original: String,
        fragment: TextFragment,
        surface: String,
        localOccurrence: Int,
    ): Int {
        val matches = englishSurfaceRegex(surface).findAll(original).toList()
        val localMatches = englishSurfaceRegex(surface).findAll(fragment.text).toList()
        val localStart = localMatches.getOrNull(localOccurrence.coerceAtLeast(0))?.range?.first
            ?: localMatches.firstOrNull()?.range?.first
            ?: return localOccurrence.coerceAtLeast(0)
        val absoluteStart = fragment.startOffset + localStart
        return matches.indexOfFirst { it.range.first == absoluteStart }.takeIf { it >= 0 }
            ?: matches.count { it.range.first < absoluteStart }
    }

    private fun invalidTranslationIndexes(
        source: List<SubtitleCueEntity>,
        translated: List<com.sublingo.app.data.media.ParsedTranslation>,
    ): Set<Int> {
        val sourceBySequence = source.associateBy { it.sequence }
        return TranslationAlignment.validate(sourceBySequence.keys, translated.map { it.item }) +
            translated.filter { parsed ->
                val sourceText = sourceBySequence[parsed.item.index]?.text.orEmpty()
                parsed.item.text.isNotBlank() && !hasCompleteWordMap(sourceText, parsed)
            }.map { it.item.index }
    }

    private fun hasCompleteWordMap(
        sourceText: String,
        parsed: com.sublingo.app.data.media.ParsedTranslation,
    ): Boolean {
        if (parsed.wordPairs.isEmpty()) return false
        val covered = BooleanArray(parsed.item.text.length)
        var searchStart = 0
        parsed.wordPairs.forEach { pair ->
            if (!containsEnglishSurface(sourceText, pair.english)) return false
            var start = parsed.item.text.indexOf(pair.chinese, searchStart)
            if (start < 0) start = parsed.item.text.indexOf(pair.chinese)
            if (start < 0) return false
            for (index in start until start + pair.chinese.length) covered[index] = true
            searchStart = start + pair.chinese.length
        }
        return parsed.item.text.indices.all { index ->
            !parsed.item.text[index].isLetterOrDigit() || covered[index]
        }
    }

    private suspend fun requestTranslation(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        cues: List<SubtitleCueEntity>,
    ): List<com.sublingo.app.data.media.ParsedTranslation> {
        val input = kotlinx.serialization.json.buildJsonArray {
            cues.forEach { cue ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("index", cue.sequence)
                    put("textEn", cue.text)
                    translationContextBySequence[cue.sequence - 1]?.text?.let { put("contextBeforeEn", it) }
                    translationContextBySequence[cue.sequence + 1]?.text?.let { put("contextAfterEn", it) }
                })
            }
        }.toString()
        return parseAlignedTranslation(
            withForegroundHeartbeat(ProcessingStage.TRANSLATION, 80) {
                llm.complete(
                profile,
                "Translate every textEn into natural Simplified Chinese. Use contextBeforeEn and contextAfterEn only to resolve the current sentence's meaning, references, omitted subjects, terminology, and phrase boundaries; never translate or copy their extra content into textZh. Keep exactly one output per index and do not merge or split subtitle items. After the full textZh is finalized, segment it by contextual semantics into natural Chinese meaning units, then reverse-map each unit to the shortest complete English word or phrase copied verbatim from the current textEn. Preserve inseparable names, terminology, idioms, phrasal verbs, collocations, grammatical constructions, quantities, and modifier-head phrases as one semantic unit when splitting them would distort the contextual meaning. Do not mechanically split character by character or by dictionary word boundaries. The ordered zh units must cover every non-punctuation character of textZh exactly once, without gaps or overlaps. Function particles may join the semantic unit that licenses them. Output only [{index,textZh,wordPairs:[{en,zh,occurrence}]}], where occurrence is the zero-based occurrence of that exact en surface in the current textEn. Every zh must be an exact contiguous substring of textZh and every en an exact contiguous substring of the current textEn. Input: $input",
                TRANSLATION_MAX_OUTPUT_TOKENS,
                )
            },
        )
    }

    private fun parseAlignedTranslation(raw: String): List<com.sublingo.app.data.media.ParsedTranslation> {
        return TranslationResponseParser.parseAligned(raw)
    }

    private fun containsEnglishSurface(text: String, surface: String): Boolean {
        return surface.isNotBlank() && englishSurfaceRegex(surface).containsMatchIn(text)
    }

    private fun englishSurfaceRegex(surface: String): Regex {
        val escaped = surface.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?i)(?<![A-Za-z0-9])$escaped(?![A-Za-z0-9])")
    }

    private data class TextFragment(val startOffset: Int, val text: String)

    private companion object {
        const val TRANSLATION_MAX_OUTPUT_TOKENS = 8_192
        const val TRANSLATION_PROMPT_VERSION = "translation-v3-context-semantic-map"
        const val MAX_TRANSLATION_SPLIT_DEPTH = 4
        const val MAX_FRAGMENT_SPLIT_DEPTH = 2
        const val MIN_FRAGMENTABLE_CUE_CHARS = 48
        const val TARGET_FRAGMENT_CHARS = 64
        val FRAGMENT_BOUNDARIES = setOf(',', ';', ':', '.', '!', '?')
    }
}

private fun Throwable.isTranslationTimeout(): Boolean {
    if (this is SocketTimeoutException) return true
    if (this is InterruptedIOException && message?.contains("timeout", ignoreCase = true) == true) return true
    return cause?.takeIf { it !== this }?.isTranslationTimeout() == true
}

@HiltWorker
class VocabWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    jobDao: ProcessingJobDao,
    private val videoDao: VideoDao,
    private val trackDao: SubtitleTrackDao,
    private val cueDao: SubtitleCueDao,
    private val providerDao: ProviderProfileDao,
    private val vocabularyDao: VocabularyDao,
    private val vocabularyLlmBatchDao: VocabularyLlmBatchDao,
    private val dictionary: DictionaryClient,
    private val llm: OpenAiCompatibleClient,
    private val senseRepairer: StandardDictionarySenseRepairer,
    private val storageCleaner: AppStorageCleaner,
) : PipelineWorker(context, params, jobDao) {
    override suspend fun doWork(): Result = VocabularyExecutionGate.withVideoLock(videoId) {
        val video = videoDao.getById(videoId)
        if (video != null && video.vocabularyVersion >= VocabularyPipelineContract.VERSION) {
            Log.i("VocabWorker", "video=$videoId vocabulary version already current; skipping duplicate worker")
            return@withVideoLock Result.success(workDataOf("vocabulary_skipped" to true))
        }
        doVocabularyWork()
    }

    private suspend fun doVocabularyWork(): Result {
        return try {
            running(ProcessingStage.VOCABULARY, 90)
            val tracks = trackDao.getByVideoId(videoId)
            val englishTrack = requireNotNull(tracks.firstOrNull { it.language.startsWith("en", true) && it.kind == "ASR" }) { "缺少 ASR 英文字幕" }
            val chineseTrack = tracks.firstOrNull { it.language.startsWith("zh", true) && it.sourceTrackId == englishTrack.id }
            val englishCues = cueDao.getByTrackId(englishTrack.id)
            require(englishCues.isNotEmpty()) { "英文字幕为空" }
            val storedProfile = providerDao.getEnabled("LLM")
                ?: throw UserConfigurationRequiredException("请先配置 LLM 模型供应商以提取生词")
            val profile = upgradeKnownLlmPresetModel(storedProfile)
            if (profile != storedProfile) providerDao.upsert(profile)
            val policy = VocabularyLlmPolicy.forPreset(profile.presetId)
            val requestBudget = VocabularyRequestBudget(policy.maxRequestsPerRun)
            val candidates = VocabularyPreprocessor.extract(englishCues)
            if (candidates.isEmpty()) return completeWithoutVocabulary()
            val zhBySequence = chineseTrack?.let { cueDao.getByTrackId(it.id).associateBy(SubtitleCueEntity::sequence) }.orEmpty()
            val extracted = if (policy.remoteExtractionEnabled) {
                val cueBatches = policy.batches(englishCues)
                Log.i("VocabWorker", "video=$videoId candidates=${candidates.size} cueBatches=${cueBatches.size}")
                extractVocabularyWithLlm(profile, cueBatches, zhBySequence, policy, requestBudget)
            } else {
                Log.i("VocabWorker", "video=$videoId provider=${profile.presetId} using local vocabulary extraction")
                running(ProcessingStage.VOCABULARY, 95)
                emptyList()
            }
            // Word-first extraction intentionally skips the phrase-only second LLM pass. Genuine
            // fixed expressions may still be returned by the primary request, while local content
            // words guarantee broad coverage without several extra batches.
            Log.i("VocabWorker", "video=$videoId extracted=${extracted.size} phraseAudit=disabled")
            val selected = VocabularySelection.sanitize(
                extracted + localVocabulary(candidates, englishCues),
                candidates,
                englishCues.mapTo(mutableSetOf()) { it.id },
                englishCues.associate { it.id to it.text },
                englishCues.associate { cue -> cue.id to zhBySequence[cue.sequence]?.text.orEmpty() },
            )
            val alignedSelected = alignMissingTranslations(profile, selected, englishCues, zhBySequence, policy, requestBudget)
            Log.i(
                "VocabWorker",
                "video=$videoId selected=${alignedSelected.size} aligned=${alignedSelected.count { !it.translationZh.isNullOrBlank() }}",
            )
            // Remove version-4 occurrences whose Chinese spans may have come
            // from a different cue or from a dictionary-definition fallback.
            vocabularyDao.deleteOccurrencesForVideo(videoId)
            val cueById = englishCues.associateBy { it.id }
            val dictionaryEntries = mutableMapOf<String, com.sublingo.app.data.remote.DictionaryEntry?>()
            alignedSelected.forEach { item ->
                val cue = cueById[item.sourceCueId] ?: return@forEach
                val normalized = item.lemma.lowercase().trim().replace(Regex("\\s+"), " ")
                val existingLexeme = vocabularyDao.findLexeme("en", normalized)
                val lexemeId = VocabularyLexemeIdentity.resolve(normalized, existingLexeme?.id)
                val dictionaryEntry = if (dictionaryEntries.containsKey(normalized)) {
                    dictionaryEntries[normalized]
                } else {
                    dictionary.lookup(normalized).also { dictionaryEntries[normalized] = it }
                }
                val lexeme = (existingLexeme ?: LexemeEntity(lexemeId, item.lemma, normalized)).copy(
                        lemma = item.lemma,
                        phonetic = dictionaryEntry?.phonetic ?: existingLexeme?.phonetic,
                        audioUrl = dictionaryEntry?.audioUrl ?: existingLexeme?.audioUrl,
                        updatedAt = System.currentTimeMillis(),
                    )
                vocabularyDao.upsertLexeme(lexeme)
                if (dictionaryEntry != null) {
                    senseRepairer.refreshLexeme(lexeme, dictionaryEntry)
                }
                val contextualTranslation = item.translationZh
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.takeIf { translation -> zhBySequence[cue.sequence]?.text?.contains(translation) == true }
                    ?: ContextualChineseMeaningResolver.resolve(
                        contextZh = zhBySequence[cue.sequence]?.text,
                        alignedMeaningZh = null,
                        definitionZh = dictionaryEntry?.senses?.mapNotNull { it.definitionZh }?.joinToString("；"),
                    )
                val difficulty = VocabularyDifficultyClassifier.classify(
                    surfaceForm = item.surfaceForm,
                    lemma = item.lemma,
                    itemType = item.itemType,
                    llmLevel = item.difficultyLevel.takeUnless { it == VocabularyDifficulty.UNKNOWN }?.name,
                )
                vocabularyDao.upsertOccurrence(
                    WordOccurrenceEntity(
                        id = "occ-${lexeme.id}-${cue.id}-${item.surfaceForm.lowercase().hashCode().toUInt().toString(16)}",
                        lexemeId = lexeme.id,
                        videoId = videoId,
                        cueId = cue.id,
                        surfaceForm = item.surfaceForm,
                        itemType = item.itemType.name,
                        contextEn = cue.text,
                        contextZh = zhBySequence[cue.sequence]?.text,
                        translationZh = contextualTranslation,
                        alignmentVersion = if (contextualTranslation != null) 2 else 0,
                        difficultyLevel = difficulty.name,
                        difficultySource = if (item.difficultyLevel == VocabularyDifficulty.UNKNOWN) "LOCAL" else "LLM",
                        difficultyConfidence = if (item.difficultyLevel == VocabularyDifficulty.UNKNOWN) .65f else .9f,
                    ),
                )
                if (vocabularyDao.reviewCardCount(lexeme.id) == 0) {
                    vocabularyDao.upsertReviewCard(ReviewCardEntity("card-${lexeme.id}", lexeme.id))
                }
            }
            jobDao.getById(jobId)?.let { job ->
                jobDao.upsert(job.copy(currentStage = ProcessingStage.VOCABULARY, state = ProcessingState.SUCCEEDED, progress = 100, updatedAt = System.currentTimeMillis()))
            }
            videoDao.updateVocabularyVersion(videoId, VocabularyPipelineContract.VERSION)
            notifyVideoProcessingCompleted()
            cleanCompletedPipelineFiles()
            Log.i("VocabWorker", "video=$videoId completed vocabulary=${alignedSelected.size} version=${VocabularyPipelineContract.VERSION}")
            Result.success(workDataOf("vocabulary_count" to alignedSelected.size))
        } catch (error: Throwable) { failed(ProcessingStage.VOCABULARY, error) }
    }

    private suspend fun extractVocabularyWithLlm(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        cueBatches: List<List<SubtitleCueEntity>>,
        zhBySequence: Map<Int, SubtitleCueEntity>,
        policy: VocabularyLlmPolicy,
        requestBudget: VocabularyRequestBudget,
    ): List<SelectedVocabulary> {
        val extracted = mutableListOf<SelectedVocabulary>()
        cueBatches.forEachIndexed { index, batch ->
            currentCoroutineContext().ensureActive()
            extracted += extractVocabularyBatch(profile, batch, zhBySequence, policy, requestBudget, depth = 0)
            running(ProcessingStage.VOCABULARY, 90 + ((index + 1) * 5 / cueBatches.size))
        }
        return extracted
    }

    private suspend fun extractVocabularyBatch(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        batch: List<SubtitleCueEntity>,
        zhBySequence: Map<Int, SubtitleCueEntity>,
        policy: VocabularyLlmPolicy,
        requestBudget: VocabularyRequestBudget,
        depth: Int,
    ): List<SelectedVocabulary> {
        val payload = kotlinx.serialization.json.buildJsonArray {
            batch.forEach { cue ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("sourceCueId", cue.id)
                    put("sourceTextEn", cue.text)
                    put("sourceTextZh", zhBySequence[cue.sequence]?.text.orEmpty())
                })
            }
        }
        return runCatching {
            val prompt = VocabularyPipelineContract.extractionPrompt(payload.toString())
            cachedLlmResult(profile, PHASE_EXTRACTION, prompt, VOCABULARY_MAX_OUTPUT_TOKENS, policy, requestBudget) { raw ->
                parseVocabularyItems(raw, allowWord = true)
            }
        }.getOrElse { error ->
            error.throwIfCancellation()
            if (error is VocabularyRequestLimitReached) {
                Log.w("VocabWorker", "Vocabulary request budget exhausted; using local fallback for ${batch.size} cues")
                emptyList()
            } else if (error is LlmHttpException && !error.isRetryable) {
                requestBudget.disable()
                Log.w("VocabWorker", "Vocabulary request rejected with HTTP ${error.statusCode}; not splitting invalid request")
                emptyList()
            } else if (error is LlmEmptyContentException) {
                requestBudget.disable()
                Log.w("VocabWorker", "Vocabulary model returned no final content; disabling remaining LLM requests", error)
                emptyList()
            } else if (batch.size > 1 && depth < policy.maxSplitDepth) {
                Log.w("VocabWorker", "Vocabulary response failed at depth=$depth; retrying two smaller halves", error)
                val middle = batch.size / 2
                extractVocabularyBatch(profile, batch.subList(0, middle), zhBySequence, policy, requestBudget, depth + 1) +
                    extractVocabularyBatch(profile, batch.subList(middle, batch.size), zhBySequence, policy, requestBudget, depth + 1)
            } else {
                Log.w("VocabWorker", "Vocabulary retry limit reached; local content-word fallback remains available", error)
                emptyList()
            }
        }
    }

    /**
     * A valid general vocabulary response can still silently omit common conversational chunks
     * because the model prioritizes harder dictionary words. This second, phrase-only audit gives
     * formulaic expressions, discourse markers, and grammatical chunks their own output budget.
     */
    private suspend fun auditPhraseCoverageWithLlm(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        cues: List<SubtitleCueEntity>,
        zhBySequence: Map<Int, SubtitleCueEntity>,
    ): List<SelectedVocabulary> = cues.chunked(PhraseAuditPlanner.BATCH_SIZE).flatMap { batch ->
        auditPhraseCoverageBatch(profile, batch, zhBySequence)
    }

    private suspend fun auditPhraseCoverageBatch(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        batch: List<SubtitleCueEntity>,
        zhBySequence: Map<Int, SubtitleCueEntity>,
    ): List<SelectedVocabulary> {
        val payload = bilingualCuePayload(batch, zhBySequence)
        val policy = VocabularyLlmPolicy.forPreset(profile.presetId)
        val requestBudget = VocabularyRequestBudget(policy.maxRequestsPerRun)
        return runCatching {
            val prompt = VocabularyPipelineContract.phraseAuditPrompt(payload.toString())
            cachedLlmResult(profile, PHASE_PHRASE_AUDIT, prompt, PHRASE_AUDIT_MAX_OUTPUT_TOKENS, policy, requestBudget) { raw ->
                parsePhraseAuditItems(raw, batch.mapTo(linkedSetOf(), SubtitleCueEntity::id))
            }
        }.getOrElse { error ->
            error.throwIfCancellation()
            if (batch.size > 1) {
                Log.w("VocabWorker", "Phrase-coverage audit failed; retrying two smaller halves", error)
                val middle = batch.size / 2
                auditPhraseCoverageBatch(profile, batch.subList(0, middle), zhBySequence) +
                    auditPhraseCoverageBatch(profile, batch.subList(middle, batch.size), zhBySequence)
            } else {
                Log.w("VocabWorker", "Single-cue phrase-coverage audit failed", error)
                emptyList()
            }
        }
    }

    private fun parseVocabularyItems(raw: String, allowWord: Boolean): List<SelectedVocabulary> {
        val array = LlmJsonResponseParser.array(raw, "Vocabulary")
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val surface = obj.stringValue("surfaceForm", "surface", "word", "phrase") ?: return@mapNotNull null
            val lemma = obj.stringValue("lemma", "canonicalForm", "canonical_form") ?: return@mapNotNull null
            val cueId = obj.stringValue("sourceCueId", "cueId", "source_cue_id") ?: return@mapNotNull null
            val itemType = parseVocabularyItemType(obj.stringValue("itemType", "type", "item_type")) ?: return@mapNotNull null
            if (!allowWord && itemType == VocabularyItemType.WORD) return@mapNotNull null
            SelectedVocabulary(
                surfaceForm = surface,
                lemma = lemma,
                sourceCueId = cueId,
                itemType = itemType,
                translationZh = obj.stringValue("translationZh", "translation_zh", "chinese", "textZh"),
                difficultyLevel = VocabularyDifficulty.parse(obj.stringValue("cefrLevel", "cefr_level", "difficultyLevel")),
                difficultySource = "LLM",
                difficultyConfidence = .9f,
            )
        }
    }

    private fun parsePhraseAuditItems(raw: String, expectedCueIds: Set<String>): List<SelectedVocabulary> {
        val rows = LlmJsonResponseParser.array(raw, "Phrase audit")
        val returnedCueIds = mutableSetOf<String>()
        val parsed = rows.flatMap { element ->
            val obj = element.jsonObject
            val cueId = obj.stringValue("sourceCueId", "cueId", "source_cue_id")
                ?: error("Phrase audit row is missing sourceCueId")
            require(cueId in expectedCueIds && returnedCueIds.add(cueId)) {
                "Phrase audit returned an unknown or duplicate cue: $cueId"
            }
            val items = obj["items"]?.jsonArray ?: error("Phrase audit row is missing items for cue: $cueId")
            items.mapNotNull { itemElement ->
                val item = itemElement.jsonObject
                val surface = item.stringValue("surfaceForm", "surface", "phrase") ?: return@mapNotNull null
                val lemma = item.stringValue("lemma", "canonicalForm", "canonical_form") ?: return@mapNotNull null
                val itemType = parseVocabularyItemType(item.stringValue("itemType", "type", "item_type")) ?: return@mapNotNull null
                if (itemType == VocabularyItemType.WORD) return@mapNotNull null
                SelectedVocabulary(
                    surfaceForm = surface,
                    lemma = lemma,
                    sourceCueId = cueId,
                    itemType = itemType,
                    translationZh = item.stringValue("translationZh", "translation_zh", "chinese", "textZh"),
                    difficultyLevel = VocabularyDifficulty.parse(item.stringValue("cefrLevel", "cefr_level", "difficultyLevel")),
                    difficultySource = "LLM",
                    difficultyConfidence = .9f,
                )
            }
        }
        return parsed
    }

    private fun bilingualCuePayload(
        batch: List<SubtitleCueEntity>,
        zhBySequence: Map<Int, SubtitleCueEntity>,
    ) = kotlinx.serialization.json.buildJsonArray {
        batch.forEach { cue ->
            add(kotlinx.serialization.json.buildJsonObject {
                put("sourceCueId", cue.id)
                put("sourceTextEn", cue.text)
                put("sourceTextZh", zhBySequence[cue.sequence]?.text.orEmpty())
            })
        }
    }

    /**
     * Large extraction responses often omit per-item Chinese spans even when the
     * vocabulary itself is valid. Repair only the missing alignments in bounded
     * batches so the model has enough output budget to return every mapping.
     */
    private suspend fun alignMissingTranslations(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        selected: List<SelectedVocabulary>,
        englishCues: List<SubtitleCueEntity>,
        zhBySequence: Map<Int, SubtitleCueEntity>,
        policy: VocabularyLlmPolicy,
        requestBudget: VocabularyRequestBudget,
    ): List<SelectedVocabulary> {
        val cueById = englishCues.associateBy(SubtitleCueEntity::id)
        val validExisting = selected.associateBy({ alignmentKey(it) }) { item ->
            item.translationZh
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.takeIf { translation ->
                    val cue = cueById[item.sourceCueId]
                    cue != null && zhBySequence[cue.sequence]?.text?.contains(translation) == true
                }
        }.filterValues { it != null }.mapValues { it.value!! }
        // The primary request already aligns its selected words. A separate repair pass is reserved
        // for the few genuine fixed phrases; bulk local WORD fallback uses dictionary meanings and
        // stays out of transcript highlighting instead of creating many additional LLM batches.
        val missing = selected.filter {
            it.itemType != VocabularyItemType.WORD && alignmentKey(it) !in validExisting
        }
        if (missing.isEmpty()) return selected

        val repaired = mutableMapOf<String, String>()
        missing.chunked(ALIGNMENT_ITEMS_PER_BATCH).forEachIndexed { batchIndex, batch ->
            val payload = kotlinx.serialization.json.buildJsonArray {
                batch.forEach { item ->
                    val cue = cueById[item.sourceCueId] ?: return@forEach
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("sourceCueId", cue.id)
                        put("surfaceForm", item.surfaceForm)
                        put("itemType", item.itemType.name)
                        put("sourceTextEn", cue.text)
                        put("sourceTextZh", zhBySequence[cue.sequence]?.text.orEmpty())
                    })
                }
            }
            val mappings = runCatching {
                val prompt = "Align every supplied English surfaceForm to its contextual Simplified Chinese subtitle span. " +
                        "translationZh must be the shortest exact, contiguous substring copied verbatim from sourceTextZh. " +
                        "Never return a dictionary definition or paraphrase. Use null only if no reliable span exists. " +
                        "Return {\"items\":[{\"sourceCueId\",\"surfaceForm\",\"itemType\",\"translationZh\"}]}. Input: $payload"
                cachedLlmResult(profile, PHASE_ALIGNMENT, prompt, ALIGNMENT_MAX_OUTPUT_TOKENS, policy, requestBudget, ::parseAlignmentItems)
            }.getOrElse { error ->
                error.throwIfCancellation()
                Log.w("VocabWorker", "Alignment repair batch ${batchIndex + 1} failed", error)
                emptyList()
            }
            mappings.forEach { mapping ->
                val cue = cueById[mapping.sourceCueId] ?: return@forEach
                val chinese = zhBySequence[cue.sequence]?.text.orEmpty()
                val translation = mapping.translationZh?.trim().orEmpty()
                if (translation.isNotBlank() && chinese.contains(translation)) {
                    repaired[alignmentKey(mapping)] = translation
                }
            }
            Log.i("VocabWorker", "alignment batch=${batchIndex + 1} requested=${batch.size} accepted=${mappings.count { mapping -> repaired[alignmentKey(mapping)] != null }}")
        }
        return selected.map { item ->
            item.copy(translationZh = validExisting[alignmentKey(item)] ?: repaired[alignmentKey(item)])
        }
    }

    private fun parseAlignmentItems(raw: String): List<SelectedVocabulary> = runCatching {
        val root = Json.parseToJsonElement(raw)
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is kotlinx.serialization.json.JsonObject -> root["items"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            else -> kotlinx.serialization.json.JsonArray(emptyList())
        }
        array.mapNotNull { element ->
            val obj = element.jsonObject
            val cueId = obj.stringValue("sourceCueId", "cueId", "source_cue_id") ?: return@mapNotNull null
            val surface = obj.stringValue("surfaceForm", "surface", "word", "phrase") ?: return@mapNotNull null
            SelectedVocabulary(
                surfaceForm = surface,
                lemma = surface,
                sourceCueId = cueId,
                itemType = parseVocabularyItemType(obj.stringValue("itemType", "type", "item_type")) ?: VocabularyItemType.WORD,
                translationZh = obj.stringValue("translationZh", "translation_zh", "chinese", "textZh"),
            )
        }
    }.getOrElse { error ->
        // Also tolerate a fenced response or explanatory prefix/suffix.
        val arrayText = raw.substringAfter('[', "").substringBeforeLast(']', "")
        if (arrayText.isBlank()) throw error
        parseAlignmentItems("[$arrayText]")
    }

    private fun kotlinx.serialization.json.JsonObject.stringValue(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> this[name]?.jsonPrimitive?.contentOrNull }

    private fun parseVocabularyItemType(raw: String?): VocabularyItemType? =
        runCatching { VocabularyItemType.valueOf(raw.orEmpty().trim().uppercase()) }.getOrNull()

    private fun alignmentKey(item: SelectedVocabulary): String =
        "${item.sourceCueId}\u0000${item.surfaceForm.lowercase().trim().replace(Regex("\\s+"), " ")}"

    private suspend fun <T> cachedLlmResult(
        profile: com.sublingo.app.data.db.ProviderProfileEntity,
        phase: String,
        prompt: String,
        maxTokens: Int,
        policy: VocabularyLlmPolicy,
        requestBudget: VocabularyRequestBudget,
        parse: (String) -> T,
    ): T {
        val inputHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${profile.id}\u0000${profile.model}\u0000$maxTokens\u0000$prompt".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        val existing = vocabularyLlmBatchDao.get(videoId, VocabularyPipelineContract.VERSION, phase, inputHash)
        existing?.responseJson?.takeIf { existing.state == "SUCCEEDED" && it.isNotBlank() }?.let { cached ->
            runCatching { parse(cached) }.onSuccess {
                Log.i("VocabWorker", "video=$videoId phase=$phase cache hit hash=${inputHash.take(8)}")
                return it
            }.onFailure { error ->
                Log.w("VocabWorker", "Discarding invalid cached response phase=$phase hash=${inputHash.take(8)}", error)
            }
        }
        if (existing != null && existing.state != "SUCCEEDED" && existing.attemptCount >= policy.maxAttemptsPerInput) {
            throw VocabularyInputAttemptsExhausted(inputHash.take(8), existing.attemptCount)
        }
        if (!requestBudget.tryAcquire()) throw VocabularyRequestLimitReached(policy.maxRequestsPerRun)
        currentCoroutineContext().ensureActive()
        val id = "vocab-llm-${videoId}-${VocabularyPipelineContract.VERSION}-$phase-${inputHash.take(16)}"
        val record = existing ?: VocabularyLlmBatchEntity(
            id = id,
            videoId = videoId,
            version = VocabularyPipelineContract.VERSION,
            phase = phase,
            inputHash = inputHash,
        )
        val runningRecord = record.copy(
            state = "RUNNING",
            attemptCount = record.attemptCount + 1,
            lastError = null,
            updatedAt = System.currentTimeMillis(),
        )
        vocabularyLlmBatchDao.upsert(runningRecord)
        return try {
            val raw = withForegroundHeartbeat(ProcessingStage.VOCABULARY, 93) {
                llm.complete(profile, prompt, maxTokens)
            }
            val parsed = parse(raw)
            vocabularyLlmBatchDao.upsert(
                runningRecord.copy(
                    responseJson = raw,
                    state = "SUCCEEDED",
                    lastError = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            parsed
        } catch (error: Throwable) {
            error.throwIfCancellation()
            vocabularyLlmBatchDao.upsert(
                runningRecord.copy(
                    state = "FAILED",
                    lastError = error.message,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            throw error
        }
    }

    private suspend fun localVocabulary(
        candidates: List<com.sublingo.app.data.vocabulary.VocabularyCandidate>,
        englishCues: List<SubtitleCueEntity>,
    ): List<SelectedVocabulary> = buildList {
        candidates.forEach { candidate ->
            val lemma = resolveDictionaryLemma(candidate.surfaceForm, candidate.normalized)
            englishCues.forEach { cue ->
                Regex("[A-Za-z][A-Za-z'-]{1,}").findAll(cue.text)
                    .firstOrNull { match -> VocabularyPreprocessor.normalize(match.value) == candidate.normalized }
                    ?.let { match ->
                        add(
                            SelectedVocabulary(
                                match.value,
                                lemma,
                                cue.id,
                                itemType = VocabularyItemType.WORD,
                                difficultyLevel = VocabularyDifficultyClassifier.classify(match.value, lemma),
                                difficultySource = "LOCAL",
                                difficultyConfidence = .65f,
                            ),
                        )
                    }
            }
        }
    }

    private suspend fun resolveDictionaryLemma(surfaceForm: String, fallback: String): String {
        VocabularyLemmaRepairPolicy.dictionaryLemmaCandidates(surfaceForm).forEach { candidate ->
            if (dictionary.lookup(candidate, allowRemote = false) != null) return candidate
        }
        return fallback
    }

    private companion object {
        const val VOCABULARY_MAX_OUTPUT_TOKENS = 8_192
        const val PHRASE_AUDIT_MAX_OUTPUT_TOKENS = 8_192
        const val ALIGNMENT_ITEMS_PER_BATCH = 100
        const val ALIGNMENT_MAX_OUTPUT_TOKENS = 6_000
        const val PHASE_EXTRACTION = "EXTRACTION_V12_WORD_FIRST"
        const val PHASE_PHRASE_AUDIT = "PHRASE_AUDIT_V12"
        const val PHASE_ALIGNMENT = "ALIGNMENT_V12_FIXED_PHRASES"
    }

    private suspend fun completeWithoutVocabulary(): Result {
        jobDao.getById(jobId)?.let { job ->
            jobDao.upsert(job.copy(currentStage = ProcessingStage.VOCABULARY, state = ProcessingState.SUCCEEDED, progress = 100, updatedAt = System.currentTimeMillis()))
        }
        videoDao.updateVocabularyVersion(videoId, VocabularyPipelineContract.VERSION)
        notifyVideoProcessingCompleted()
        cleanCompletedPipelineFiles()
        return Result.success(workDataOf("vocabulary_count" to 0))
    }

    private suspend fun cleanCompletedPipelineFiles() {
        runCatching { storageCleaner.cleanCompletedPipeline(jobId) }
            .onFailure { error -> Log.w("VocabWorker", "Unable to clean completed pipeline files for job=$jobId", error) }
    }

    private suspend fun notifyVideoProcessingCompleted() {
        if (!inputData.getBoolean(NOTIFY_ON_COMPLETION, false)) return
        val title = videoDao.getById(videoId)?.title
        runCatching {
            BackgroundWorkNotifications.notifyVideoProcessingCompleted(
                context = applicationContext,
                videoId = videoId,
                videoTitle = title,
            )
        }.onFailure { error ->
            // A notification failure must not turn successfully generated learning
            // content into a failed WorkManager pipeline.
            Log.w("VocabWorker", "Unable to post completion notification for video=$videoId", error)
        }
    }
}

private class VocabularyRequestLimitReached(limit: Int) : IllegalStateException(
    "Vocabulary LLM request limit reached ($limit)",
)

private class VocabularyInputAttemptsExhausted(hash: String, attempts: Int) : IllegalStateException(
    "Vocabulary LLM input $hash already failed $attempts times",
)

/**
 * Upgrade compatibility shim for work enqueued by builds that had a separate
 * vocabulary-alignment stage. Alignment is now persisted directly by
 * [VocabWorker], but WorkManager stores worker class names in its database and
 * may still try to instantiate this class after an APK upgrade.
 *
 * Keep this class until old persisted work has naturally expired/pruned.
 */
@Deprecated("Vocabulary alignment is handled by VocabWorker")
@HiltWorker
class VocabularyAlignmentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
