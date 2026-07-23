package com.sublingo.app.data.settings

import com.sublingo.app.data.db.ProviderProfileEntity
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class SttPresetId(val storageId: String) {
    DOUBAO("doubao-flash"),
    CUSTOM("custom-stt"),
    ;

    companion object {
        fun fromStorageId(value: String?): SttPresetId = when (value) {
            DOUBAO.storageId, "doubao-standard" -> DOUBAO
            else -> CUSTOM
        }
    }
}

enum class SttProtocol(val storageId: String, val displayName: String) {
    DOUBAO_BIGMODEL("doubao-bigmodel", "Doubao 协议"),
    OPENAI_TRANSCRIPTION("openai-transcription", "OpenAI 转写协议"),
    OPENAI_CHAT_AUDIO("openai-chat-audio", "Chat 音频消息"),
    ;

    companion object {
        fun fromStorageId(value: String?): SttProtocol = entries.firstOrNull { it.storageId == value }
            ?: DOUBAO_BIGMODEL
    }
}

data class SttProviderPreset(
    val id: SttPresetId,
    val displayName: String,
    val protocol: SttProtocol,
    val baseUrl: String,
    val model: String = "",
    val resourceId: String = "",
    val apiKeyUrl: String?,
    val helperText: String,
)

object SttProviderPresets {
    val doubao = SttProviderPreset(
        id = SttPresetId.DOUBAO,
        displayName = "Doubao ASR",
        protocol = SttProtocol.DOUBAO_BIGMODEL,
        baseUrl = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash",
        resourceId = "volc.bigasr.auc_turbo",
        apiKeyUrl = "https://console.volcengine.com/speech/app",
        helperText = "支持长音频和原生句级时间戳；超过 2 小时会自动切换标准版。",
    )

    val custom = SttProviderPreset(
        id = SttPresetId.CUSTOM,
        displayName = "自定义",
        protocol = SttProtocol.OPENAI_TRANSCRIPTION,
        baseUrl = "",
        apiKeyUrl = null,
        helperText = "可接入标准 /audio/transcriptions、MiMo 风格的 Chat 音频消息，或 Doubao BigModel 协议。",
    )

    val all: List<SttProviderPreset> = listOf(doubao, custom)
    fun byId(id: SttPresetId): SttProviderPreset = all.first { it.id == id }
}

fun sttOptionsJson(protocol: SttProtocol): String = buildJsonObject {
    put("protocol", protocol.storageId)
}.toString()

fun sttProtocol(profile: ProviderProfileEntity): SttProtocol {
    val stored = runCatching {
        profile.optionsJson?.let { Json.parseToJsonElement(it).jsonObject["protocol"]?.jsonPrimitive?.contentOrNull }
    }.getOrNull()
    return stored?.let(SttProtocol::fromStorageId) ?: when (SttPresetId.fromStorageId(profile.presetId)) {
        SttPresetId.DOUBAO -> SttProtocol.DOUBAO_BIGMODEL
        SttPresetId.CUSTOM -> if (profile.resourceId.isNullOrBlank()) SttProtocol.OPENAI_CHAT_AUDIO else SttProtocol.DOUBAO_BIGMODEL
    }
}

fun validateSttProvider(
    name: String,
    baseUrl: String,
    protocol: SttProtocol,
    model: String,
    resourceId: String,
) {
    require(name.trim().isNotEmpty()) { "请输入 STT 供应商名称" }
    val normalizedUrl = normalizeLlmBaseUrl(baseUrl)
    val uri = runCatching { URI(normalizedUrl) }.getOrNull()
    require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
        "STT 地址必须是有效的 HTTPS 地址"
    }
    when (protocol) {
        SttProtocol.DOUBAO_BIGMODEL -> require(resourceId.trim().isNotEmpty()) { "请输入 Resource ID" }
        SttProtocol.OPENAI_TRANSCRIPTION, SttProtocol.OPENAI_CHAT_AUDIO -> require(model.trim().isNotEmpty()) { "请输入 STT 模型名称" }
    }
}

const val OPENAI_AUDIO_CHUNK_DURATION_MS: Long = 3L * 60_000
