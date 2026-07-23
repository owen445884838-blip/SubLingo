package com.sublingo.app.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sublingo.app.R
import com.sublingo.app.ui.components.SubLingoLogo
import com.sublingo.app.data.settings.LlmPresetId
import com.sublingo.app.data.settings.LlmProviderPreset
import com.sublingo.app.data.settings.LlmProviderPresets
import com.sublingo.app.data.settings.SttPresetId
import com.sublingo.app.data.settings.SttProtocol
import com.sublingo.app.data.settings.SttProviderPreset
import com.sublingo.app.data.settings.SttProviderPresets

private val Cream = Color(0xFFFDFAF0)
private val Ink = Color(0xFF2E303A)
private val Muted = Color(0xFF747688)
private val Lavender = Color(0xFFE8E9FF)
private val Peach = Color(0xFFFFF0E6)
private val Rose = Color(0xFFFFEAE6)
private val Mint = Color(0xFFE6F5EA)
private val Gold = Color(0xFFFDCF44)

private enum class SettingsEditor { SECURITY, LLM, STT, DICTIONARY, COOKIE, THEME, SUBTITLE }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    var editor by remember { mutableStateOf<SettingsEditor?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        SubLingoLogo(modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(42.dp))
        Text("设置", color = Ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(34.dp))

        SettingsSection("账户与安全") {
            SettingsGroup(Lavender) {
                SettingsRow(
                    iconRes = R.drawable.ic_settings_key,
                    title = "API 密钥",
                    subtitle = if (state.llmConfigured || state.sttConfigured) "已安全存储" else "尚未配置",
                    subtitleIconRes = if (state.llmConfigured || state.sttConfigured) R.drawable.ic_settings_lock else null,
                    onClick = { editor = SettingsEditor.SECURITY },
                )
            }
        }
        SettingsSection("供应商管理") {
            SettingsGroup(Peach) {
                SettingsRow(R.drawable.ic_settings_psychology, "LLM 模型供应商", "${state.activeLlmProviderName} · ${if (state.llmConfigured) "已配置" else "待配置"}", onClick = { editor = SettingsEditor.LLM })
                SettingsDivider()
                SettingsRow(R.drawable.ic_settings_graphic_eq, "语音转文字 (STT) 供应商", "${state.activeSttProviderName} · ${if (state.sttConfigured) "已配置" else "待配置"}", onClick = { editor = SettingsEditor.STT })
                SettingsDivider()
                SettingsRow(R.drawable.ic_settings_menu_book, "词典", dictionarySummary(state), onClick = { editor = SettingsEditor.DICTIONARY })
            }
        }
        SettingsSection("下载偏好") {
            SettingsGroup(Rose) {
                SettingsRow(R.drawable.ic_settings_cookie, "Cookie 管理", if (state.cookieConfigured) "已加密保存" else "用于受限视频下载", onClick = { editor = SettingsEditor.COOKIE })
            }
        }
        SettingsSection("外观设置") {
            SettingsGroup(Mint) {
                SettingsRow(R.drawable.ic_settings_palette, "主题模式", "系统默认", onClick = { editor = SettingsEditor.THEME })
                SettingsDivider()
                SettingsRow(R.drawable.ic_settings_text_fields, "字幕大小", "中", onClick = { editor = SettingsEditor.SUBTITLE })
            }
        }

        if (state.saveStatus.isNotBlank()) {
            Surface(color = if (state.saveStatus.contains("失败") || state.saveStatus.contains("请输入")) Color(0xFFFFDAD6) else Mint, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text(state.saveStatus, Modifier.padding(16.dp), color = Ink, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    editor?.let { selected ->
        SettingsDialog(selected, state, viewModel, onDismiss = { editor = null; viewModel.clearStatus() })
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, Modifier.padding(horizontal = 12.dp), color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(16.dp))
    content()
    Spacer(Modifier.height(34.dp))
}

@Composable
private fun SettingsGroup(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    @DrawableRes subtitleIconRes: Int? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(999.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(iconRes), contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    subtitleIconRes?.let {
                        Icon(painterResource(it), contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                    }
                    Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(painterResource(R.drawable.ic_settings_chevron_right), contentDescription = null, tint = Muted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 88.dp), color = Color.White.copy(alpha = .65f))
}

@Composable
private fun SettingsDialog(editor: SettingsEditor, state: SettingsUiState, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val title = when (editor) {
        SettingsEditor.SECURITY -> "API 密钥与安全"
        SettingsEditor.LLM -> "LLM 模型供应商"
        SettingsEditor.STT -> "STT 供应商"
        SettingsEditor.DICTIONARY -> "词典配置"
        SettingsEditor.COOKIE -> "Cookie 管理"
        SettingsEditor.THEME -> "主题模式"
        SettingsEditor.SUBTITLE -> "字幕大小"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = Cream,
        title = { Text(title, color = Ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (editor) {
                    SettingsEditor.SECURITY -> {
                        InfoBlock("密钥不会写入 Room 或日志。应用使用 Android Keystore 生成不可导出的 AES 密钥，并以 AES-GCM 加密保存 API Key 与 Cookie。")
                        ConfigStatus(state.activeLlmProviderName, state.llmConfigured)
                        ConfigStatus(state.activeSttProviderName, state.sttConfigured)
                        ConfigStatus("下载 Cookie", state.cookieConfigured)
                    }
                    SettingsEditor.LLM -> {
                        LlmPresetTabs(state.selectedLlmPreset, viewModel::selectLlmPreset)
                        val preset = LlmProviderPresets.byId(state.selectedLlmPreset)
                        InfoBlock(preset.helperText)
                        preset.apiKeyUrl?.let { apiKeyUrl ->
                            Button(
                                onClick = {
                                    runCatching { uriHandler.openUri(apiKeyUrl) }
                                        .onFailure { viewModel.showStatus("无法打开供应商网页，请稍后重试") }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("打开 ${preset.displayName} API Key 页面 ↗", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        if (state.selectedLlmPreset == LlmPresetId.CUSTOM) {
                            SettingField("供应商名称", state.llmProviderName, viewModel::updateLlmProviderName, placeholder = "例如：我的 LLM")
                        }
                        SettingField("Base URL", state.llmBaseUrl, viewModel::updateLlmBaseUrl, placeholder = "https://example.com/v1")
                        SettingField("模型 / Endpoint ID", state.llmModel, viewModel::updateLlmModel)
                        ProviderApiKeyField(
                            configured = state.canReuseLlmApiKey,
                            editing = state.isEditingLlmApiKey,
                            value = state.llmApiKey,
                            providerName = state.llmProviderName,
                            onValueChange = viewModel::updateLlmApiKey,
                            onChange = viewModel::changeLlmApiKey,
                        )
                    }
                    SettingsEditor.STT -> {
                        SttPresetTabs(state.selectedSttPreset, viewModel::selectSttPreset)
                        val preset = SttProviderPresets.byId(state.selectedSttPreset)
                        InfoBlock(preset.helperText)
                        preset.apiKeyUrl?.let { apiKeyUrl ->
                            Button(
                                onClick = {
                                    runCatching { uriHandler.openUri(apiKeyUrl) }
                                        .onFailure { viewModel.showStatus("无法打开供应商网页，请稍后重试") }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("打开 ${preset.displayName} API Key 页面 ↗", fontWeight = FontWeight.ExtraBold) }
                        }
                        if (state.selectedSttPreset == SttPresetId.CUSTOM) {
                            SettingField("供应商名称", state.sttProviderName, viewModel::updateSttProviderName, placeholder = "例如：我的 STT")
                            SttProtocolTabs(state.sttProtocol, viewModel::updateSttProtocol)
                        }
                        SettingField("Endpoint / Base URL", state.sttBaseUrl, viewModel::updateSttBaseUrl, placeholder = "https://example.com/v1")
                        when (state.sttProtocol) {
                            SttProtocol.DOUBAO_BIGMODEL -> SettingField("Resource ID", state.sttResourceId, viewModel::updateSttResourceId)
                            SttProtocol.OPENAI_TRANSCRIPTION, SttProtocol.OPENAI_CHAT_AUDIO -> SettingField("模型", state.sttModel, viewModel::updateSttModel, placeholder = "例如：whisper-1")
                        }
                        ProviderApiKeyField(
                            configured = state.canReuseSttApiKey,
                            editing = state.isEditingSttApiKey,
                            value = state.sttApiKey,
                            providerName = state.sttProviderName,
                            onValueChange = viewModel::updateSttApiKey,
                            onChange = viewModel::changeSttApiKey,
                        )
                    }
                    SettingsEditor.COOKIE -> {
                        InfoBlock("Cookie 仅用于需要登录、反机器人验证或高清权限的视频下载，并会加密保存在本机。支持浏览器请求头中的 name=value; name2=value 格式，也支持完整 Netscape cookies.txt 内容。请勿粘贴来源不明的 Cookie。")
                        SettingField(
                            "Cookie",
                            state.cookie,
                            viewModel::updateCookie,
                            secret = true,
                            singleLine = false,
                            placeholder = if (state.cookieConfigured) {
                                "输入新 Cookie 将覆盖现有值"
                            } else {
                                "YouTube: SID=...; HSID=...\nBilibili: SESSDATA=...; bili_jct=..."
                            },
                        )
                    }
                    SettingsEditor.DICTIONARY -> {
                        InfoBlock("基础英汉词典随应用提供。完整离线包使用 MIT 许可的 ECDICT，安装后优先离线查询音标、词性和英汉释义；不包含真人发音，发音使用系统 TTS。")
                        DictionaryPackStatus(state)
                    }
                    SettingsEditor.THEME -> InfoBlock("当前跟随系统与 SubLingo 浅色视觉规范。完整深色主题将在后续稳定性阶段加入。")
                    SettingsEditor.SUBTITLE -> InfoBlock("当前字幕使用中等字号。播放器内支持英文、中文分别显隐；字号调节将在播放器设置中继续完善。")
                }
                if (state.saveStatus.isNotBlank()) Text(state.saveStatus, color = if (state.saveStatus.contains("已")) Color(0xFF2E7D32) else Color(0xFFBA1A1A), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            when (editor) {
                SettingsEditor.LLM -> SaveButton(state.isSaving) { viewModel.saveLlm() }
                SettingsEditor.STT -> SaveButton(state.isSaving) { viewModel.saveStt() }
                SettingsEditor.COOKIE -> SaveButton(state.isSaving) { viewModel.saveCookie() }
                SettingsEditor.DICTIONARY -> {
                    if (state.offlineDictionary.installed) {
                        TextButton(onClick = viewModel::deleteOfflineDictionary) { Text("删除完整词典", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold) }
                    } else {
                        Button(
                            onClick = viewModel::downloadOfflineDictionary,
                            enabled = !state.offlineDictionary.downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                            shape = RoundedCornerShape(999.dp),
                        ) { Text(if (state.offlineDictionary.downloading) "下载中…" else "下载完整词典", fontWeight = FontWeight.ExtraBold) }
                    }
                }
                else -> TextButton(onClick = onDismiss) { Text("完成", color = Ink, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            if (editor in setOf(SettingsEditor.LLM, SettingsEditor.STT, SettingsEditor.COOKIE)) {
                TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
            }
        },
    )
}

@Composable
private fun LlmPresetTabs(selected: LlmPresetId, onSelect: (LlmPresetId) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LlmProviderPresets.all.forEach { preset ->
            LlmPresetTab(preset, selected == preset.id) { onSelect(preset.id) }
        }
    }
}

@Composable
private fun LlmPresetTab(preset: LlmProviderPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Gold else Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Text(
            preset.displayName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SttPresetTabs(selected: SttPresetId, onSelect: (SttPresetId) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SttProviderPresets.all.forEach { preset ->
            SttPresetTab(preset, selected == preset.id) { onSelect(preset.id) }
        }
    }
}

@Composable
private fun SttPresetTab(preset: SttProviderPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Gold else Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Text(
            preset.displayName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SttProtocolTabs(selected: SttProtocol, onSelect: (SttProtocol) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("接口协议", color = Muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SttProtocol.entries.forEach { protocol ->
                Surface(
                    onClick = { onSelect(protocol) },
                    color = if (selected == protocol) Gold else Color.White,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(protocol.displayName, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingField(label: String, value: String, onValueChange: (String) -> Unit, secret: Boolean = false, singleLine: Boolean = true, placeholder: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ink, focusedLabelColor = Ink),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProviderApiKeyField(
    configured: Boolean,
    editing: Boolean,
    value: String,
    providerName: String,
    onValueChange: (String) -> Unit,
    onChange: () -> Unit,
) {
    if (configured && !editing) {
        Surface(color = Mint, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("API Key 已加密保存", Modifier.weight(1f), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                TextButton(onClick = onChange) { Text("更改", color = Ink, fontWeight = FontWeight.ExtraBold) }
            }
        }
    } else {
        SettingField(
            "API Key",
            value,
            onValueChange,
            secret = true,
            placeholder = if (configured) "输入新 API Key 将覆盖当前值" else "请输入 ${providerName.ifBlank { "此供应商" }} API Key",
        )
    }
}

@Composable private fun SaveButton(saving: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(999.dp)) {
        Text(if (saving) "保存中…" else "加密保存", fontWeight = FontWeight.ExtraBold)
    }
}

@Composable private fun InfoBlock(text: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { Text(text, Modifier.padding(16.dp), color = Muted, style = MaterialTheme.typography.bodyMedium) }
}

@Composable private fun ConfigStatus(name: String, configured: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.SemiBold)
        Text(if (configured) "已配置" else "未配置", color = if (configured) Color(0xFF2E7D32) else Muted, fontWeight = FontWeight.Bold)
    }
}

private fun dictionarySummary(state: SettingsUiState): String = when {
    state.offlineDictionary.downloading -> "完整词典下载中 ${state.offlineDictionary.progress}%"
    state.offlineDictionary.installed -> "ECDICT · ${state.offlineDictionary.entryCount} 词条 · ${formatSize(state.offlineDictionary.sizeBytes)}"
    else -> "内置 45,000 词 · 可下载完整包"
}

@Composable
private fun DictionaryPackStatus(state: SettingsUiState) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.offlineDictionary.status, color = Ink, fontWeight = FontWeight.Bold)
            if (state.offlineDictionary.downloading) {
                LinearProgressIndicator(
                    progress = { state.offlineDictionary.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold,
                    trackColor = Lavender,
                )
            }
            if (state.offlineDictionary.installed) {
                Text("${state.offlineDictionary.entryCount} 个词条 · 占用 ${formatSize(state.offlineDictionary.sizeBytes)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("数据源：ECDICT · MIT License · 不包含真人发音", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("当前已内置 45,000 词基础包（约 3 MB APK / 6.3 MB 安装后）。完整包下载时需要网络，预计需要约 50–150 MB 存储空间。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
