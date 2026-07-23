package com.sublingo.app.data.settings

import com.sublingo.app.data.db.ProviderProfileEntity
import com.sublingo.app.domain.model.ProviderConfig
import java.net.URI

enum class LlmPresetId(val storageId: String) {
    DEEPSEEK("deepseek"),
    DOUBAO("doubao-ark"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStorageId(value: String?): LlmPresetId = entries.firstOrNull { it.storageId == value } ?: CUSTOM
    }
}

data class LlmProviderPreset(
    val id: LlmPresetId,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val apiKeyUrl: String?,
    val helperText: String,
)

object LlmProviderPresets {
    val deepSeek = LlmProviderPreset(
        id = LlmPresetId.DEEPSEEK,
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        apiKeyUrl = "https://platform.deepseek.com/api_keys",
        helperText = "适合翻译与生词提取，可按需改用 deepseek-reasoner。",
    )

    val doubao = LlmProviderPreset(
        id = LlmPresetId.DOUBAO,
        displayName = "Doubao",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        model = "doubao-seed-2-1-pro-260628",
        apiKeyUrl = "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey",
        helperText = "使用火山方舟 API；也可将模型改为控制台提供的模型 ID 或 Endpoint ID。",
    )

    val custom = LlmProviderPreset(
        id = LlmPresetId.CUSTOM,
        displayName = "自定义",
        baseUrl = "",
        model = "",
        apiKeyUrl = null,
        helperText = "支持具有 /chat/completions 接口和 Bearer API Key 的 OpenAI 兼容供应商。",
    )

    val all: List<LlmProviderPreset> = listOf(deepSeek, doubao, custom)

    fun byId(id: LlmPresetId): LlmProviderPreset = all.first { it.id == id }
}

fun upgradeKnownLlmPresetModel(profile: ProviderProfileEntity): ProviderProfileEntity = profile

fun normalizeLlmBaseUrl(value: String): String = value.trim().trimEnd('/')

fun validateLlmProvider(name: String, baseUrl: String, model: String) {
    require(name.trim().isNotEmpty()) { "请输入供应商名称" }
    val normalizedUrl = normalizeLlmBaseUrl(baseUrl)
    require(normalizedUrl.isNotEmpty()) { "请输入 LLM Base URL" }
    val uri = runCatching { URI(normalizedUrl) }.getOrNull()
    require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
        "LLM 地址必须是有效的 HTTPS 地址"
    }
    require(model.trim().isNotEmpty()) { "请输入模型名称或 Endpoint ID" }
}

object ProviderPreset {
    val deepSeek = ProviderConfig(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKeyAlias = "provider.deepseek.apiKey",
        model = "deepseek-chat",
    )

    val doubaoAsr = ProviderConfig(
        name = "Doubao ASR",
        baseUrl = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash",
        apiKeyAlias = "provider.doubao.asr.apiKey",
        resourceId = "volc.bigasr.auc_turbo",
    )
}
