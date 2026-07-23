package com.sublingo.app.data.remote

import android.util.Base64
import com.sublingo.app.data.db.ProviderProfileEntity
import com.sublingo.app.data.media.AudioWavTranscoder
import com.sublingo.app.data.settings.SttProtocol
import com.sublingo.app.data.settings.sttProtocol
import com.sublingo.app.security.SecretStore
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import javax.inject.Inject
import kotlinx.coroutines.delay

data class AsrUtterance(val text: String, val startMs: Long, val endMs: Long)

class LlmHttpException(
    val statusCode: Int,
    detail: String,
) : IllegalStateException("LLM 请求失败 HTTP $statusCode：$detail") {
    val isRetryable: Boolean get() = statusCode == 408 || statusCode == 429 || statusCode >= 500
}

class LlmEmptyContentException(
    finishReason: String?,
    reasoningChars: Int,
    completionTokens: Int?,
) : IllegalStateException(
    "LLM 响应没有最终内容（finish_reason=${finishReason ?: "unknown"}, " +
        "reasoning_chars=$reasoningChars, completion_tokens=${completionTokens ?: "unknown"}）",
)

class SpeechToTextClient @Inject constructor(
    private val doubao: DoubaoClient,
    private val openAiAudio: OpenAiAudioTranscriptionClient,
) {
    suspend fun transcribe(profile: ProviderProfileEntity, audio: File, durationMs: Long): List<AsrUtterance> =
        when (sttProtocol(profile)) {
            SttProtocol.DOUBAO_BIGMODEL -> doubao.transcribe(profile, audio)
            SttProtocol.OPENAI_TRANSCRIPTION -> openAiAudio.transcribeMultipart(profile, audio, durationMs)
            SttProtocol.OPENAI_CHAT_AUDIO -> openAiAudio.transcribe(profile, audio, durationMs)
        }
}

class DoubaoClient @Inject constructor(
    private val http: OkHttpClient,
    private val secrets: SecretStore,
) {
    suspend fun transcribe(profile: ProviderProfileEntity, audio: File): List<AsrUtterance> {
        val key = requireNotNull(profile.secretAlias?.let { secrets.read(it) }) { "请先配置 Doubao API Key" }
        require(audio.length() <= 20L * 1024 * 1024) { "音频分片超过 20MB，请缩短分片" }
        val body = buildJsonObject {
            put("user", buildJsonObject { put("uid", "sublingo-android") })
            put("audio", buildJsonObject {
                put("data", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP))
                put("format", audio.extension.ifBlank { "m4a" })
            })
            put("request", buildJsonObject { put("model_name", "bigmodel"); put("enable_punc", true); put("enable_itn", true) })
        }.toString()
        val requestId = UUID.randomUUID().toString()
        val endpoint = requireNotNull(profile.baseUrl)
        val requestBuilder = Request.Builder().url(endpoint)
            .header("X-Api-Key", key)
            .header("X-Api-Resource-Id", profile.resourceId ?: "volc.bigasr.auc_turbo")
            .header("X-Api-Request-Id", requestId)
        if (profile.resourceId?.contains("turbo") == true) {
            val raw = execute(requestBuilder.header("X-Api-Sequence", "-1").post(body.toRequestBody(JSON)).build())
            val utterances = parseUtterances(raw)
            require(utterances.isNotEmpty()) {
                "Doubao 未返回可用转录结果：${extractErrorMessage(raw) ?: "响应中没有 utterances"}"
            }
            return utterances
        }
        execute(requestBuilder.header("X-Api-Sequence", "1").post(body.toRequestBody(JSON)).build())
        var utterances = emptyList<AsrUtterance>()
        var attempt = 0
        while (utterances.isEmpty() && attempt < 120) {
            delay(3_000)
            val raw = execute(
                Request.Builder().url(endpoint)
                    .header("X-Api-Key", key)
                    .header("X-Api-Resource-Id", profile.resourceId ?: "volc.bigasr.auc")
                    .header("X-Api-Request-Id", requestId)
                    .header("X-Api-Sequence", "-1")
                    .post("{}".toRequestBody(JSON)).build(),
            )
            utterances = parseUtterances(raw)
            attempt++
        }
        require(utterances.isNotEmpty()) { "Doubao 标准版轮询超时或未返回 utterances" }
        return utterances
    }

    private fun execute(request: Request): String = http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        val apiStatus = response.header("X-Api-Status-Code")
        val apiMessage = response.header("X-Api-Message")
        check(response.isSuccessful && (apiStatus == null || apiStatus == "20000000")) {
            "Doubao 请求失败：HTTP ${response.code}" +
                (apiStatus?.let { "，状态码 $it" } ?: "") +
                (apiMessage?.takeIf { it.isNotBlank() }?.let { "，$it" } ?: "") +
                extractErrorMessage(body)?.let { "，$it" }.orEmpty()
        }
        body
    }

    private fun extractErrorMessage(raw: String): String? = runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject
        listOf("message", "error", "msg").firstNotNullOfOrNull { key ->
            root[key]?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()?.take(300)

    private fun parseUtterances(raw: String): List<AsrUtterance> {
        val root = Json.parseToJsonElement(raw).jsonObject
        val result = root["result"]?.jsonObject ?: root
        return result["utterances"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val text = item["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isBlank()) null else AsrUtterance(
                text,
                item["start_time"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L,
                item["end_time"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L,
            )
        }
    }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}

class OpenAiAudioTranscriptionClient @Inject constructor(
    private val http: OkHttpClient,
    private val secrets: SecretStore,
    private val wavTranscoder: AudioWavTranscoder,
) {
    suspend fun transcribeMultipart(profile: ProviderProfileEntity, audio: File, durationMs: Long): List<AsrUtterance> {
        val key = requireNotNull(profile.secretAlias?.let { secrets.read(it) }) { "请先配置 STT API Key" }
        val wav = wavTranscoder.toMono16KhzWav(audio)
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", requireNotNull(profile.model) { "请配置 STT 模型" })
            .addFormDataPart("language", "en")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("file", "audio.wav", wav.toRequestBody(WAV))
            .build()
        val request = Request.Builder()
            .url(requireNotNull(profile.baseUrl).trimEnd('/') + "/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(requestBody)
            .build()
        return http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { "STT 请求失败 HTTP ${response.code}：${extractError(raw) ?: "服务端未返回错误详情"}" }
            parseMultipartResponse(raw, durationMs)
        }
    }

    suspend fun transcribe(profile: ProviderProfileEntity, audio: File, durationMs: Long): List<AsrUtterance> {
        val key = requireNotNull(profile.secretAlias?.let { secrets.read(it) }) { "请先配置 STT API Key" }
        val wav = wavTranscoder.toMono16KhzWav(audio)
        val dataUrl = "data:audio/wav;base64," + Base64.encodeToString(wav, Base64.NO_WRAP)
        require(dataUrl.toByteArray().size <= MAX_ENCODED_BYTES) { "WAV Base64 超过 MiMo 10MB 限制，请缩短音频分片" }
        val body = buildJsonObject {
            put("model", requireNotNull(profile.model) { "请配置 STT 模型" })
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_audio")
                            put("input_audio", buildJsonObject { put("data", dataUrl) })
                        })
                    })
                })
            })
            put("asr_options", buildJsonObject { put("language", "en") })
        }.toString()
        val request = Request.Builder()
            .url(requireNotNull(profile.baseUrl).trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toRequestBody(JSON))
            .build()
        val text = http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { "STT 请求失败 HTTP ${response.code}：${extractError(raw) ?: "服务端未返回错误详情"}" }
            val root = Json.parseToJsonElement(raw).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf(String::isNotEmpty)
                ?: error("STT 响应格式异常：缺少 choices[0].message.content")
        }
        return EstimatedTranscriptTiming.utterances(text, durationMs)
    }

    private fun extractError(raw: String): String? = runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.take(300)

    private fun parseMultipartResponse(raw: String, durationMs: Long): List<AsrUtterance> {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val segments = root?.get("segments")?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val text = item["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) null else AsrUtterance(
                text,
                ((item["start"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0) * 1_000).toLong(),
                ((item["end"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0) * 1_000).toLong(),
            )
        }
        if (segments.isNotEmpty()) return segments
        val text = root?.get("text")?.jsonPrimitive?.contentOrNull ?: raw.trim()
        return EstimatedTranscriptTiming.utterances(text, durationMs)
    }

    private companion object {
        const val MAX_ENCODED_BYTES = 10 * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
        val WAV = "audio/wav".toMediaType()
    }
}

object EstimatedTranscriptTiming {
    fun utterances(text: String, durationMs: Long): List<AsrUtterance> {
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map(String::trim).filter(String::isNotEmpty)
            .ifEmpty { listOf(text.trim()) }.filter(String::isNotEmpty)
        if (sentences.isEmpty()) return emptyList()
        val weights = sentences.map { sentence -> sentence.count { !it.isWhitespace() }.coerceAtLeast(1) }
        val totalWeight = weights.sum().coerceAtLeast(1)
        var cursor = 0L
        return sentences.mapIndexed { index, sentence ->
            val end = if (index == sentences.lastIndex) durationMs else
                cursor + durationMs * weights[index] / totalWeight
            AsrUtterance(sentence, cursor, end.coerceAtLeast(cursor + 1)).also { cursor = it.endMs }
        }
    }
}

class OpenAiCompatibleClient @Inject constructor(
    private val http: OkHttpClient,
    private val secrets: SecretStore,
) {
    suspend fun complete(profile: ProviderProfileEntity, prompt: String, maxTokens: Int? = null): String {
        val key = requireNotNull(profile.secretAlias?.let { secrets.read(it) }) { "请先配置 LLM API Key" }
        val body = buildJsonObject {
            put("model", requireNotNull(profile.model))
            put("temperature", 0.1)
            put("stream", false)
            maxTokens?.let {
                if (profile.presetId == "xiaomi-mimo") put("max_completion_tokens", it) else put("max_tokens", it)
            }
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", "Return valid JSON only.") })
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
        }.toString()
        val request = Request.Builder().url(profile.baseUrl!!.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toRequestBody(JSON)).build()
        val call = http.newCall(request).also {
            if (profile.presetId == "xiaomi-mimo") it.timeout().timeout(MIMO_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use(::parseCompletionResponse) }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }

    private fun parseCompletionResponse(response: Response): String {
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw LlmHttpException(
                response.code,
                extractLlmError(raw) ?: "服务端未返回错误详情",
            )
        }
        val root = Json.parseToJsonElement(raw).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("LLM 响应格式异常：缺少 choices[0]")
        val message = choice["message"]?.jsonObject
            ?: error("LLM 响应格式异常：缺少 choices[0].message")
        message["content"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { return it }
        throw LlmEmptyContentException(
            finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull,
            reasoningChars = message["reasoning_content"]?.jsonPrimitive?.contentOrNull?.length ?: 0,
            completionTokens = root["usage"]?.jsonObject?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
        )
    }

    private fun extractLlmError(raw: String): String? = runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.take(300)

    private companion object {
        const val MIMO_CALL_TIMEOUT_SECONDS = 60L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
