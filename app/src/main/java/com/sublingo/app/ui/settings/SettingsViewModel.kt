package com.sublingo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublingo.app.data.db.ProviderProfileDao
import com.sublingo.app.data.db.ProviderProfileEntity
import com.sublingo.app.data.remote.OfflineDictionaryPackManager
import com.sublingo.app.data.remote.OfflineDictionaryState
import com.sublingo.app.data.settings.LlmPresetId
import com.sublingo.app.data.settings.LlmProviderPresets
import com.sublingo.app.data.settings.normalizeLlmBaseUrl
import com.sublingo.app.data.settings.SttPresetId
import com.sublingo.app.data.settings.SttProtocol
import com.sublingo.app.data.settings.SttProviderPresets
import com.sublingo.app.data.settings.sttOptionsJson
import com.sublingo.app.data.settings.sttProtocol
import com.sublingo.app.data.settings.validateSttProvider
import com.sublingo.app.data.settings.validateLlmProvider
import com.sublingo.app.data.settings.upgradeKnownLlmPresetModel
import com.sublingo.app.security.SecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedLlmPreset: LlmPresetId = LlmPresetId.DEEPSEEK,
    val llmProviderName: String = LlmProviderPresets.deepSeek.displayName,
    val llmBaseUrl: String = LlmProviderPresets.deepSeek.baseUrl,
    val llmModel: String = LlmProviderPresets.deepSeek.model,
    val llmApiKey: String = "",
    val activeLlmProviderName: String = LlmProviderPresets.deepSeek.displayName,
    val activeLlmPreset: LlmPresetId? = null,
    val canReuseLlmApiKey: Boolean = false,
    val isEditingLlmApiKey: Boolean = false,
    val selectedSttPreset: SttPresetId = SttPresetId.DOUBAO,
    val sttProtocol: SttProtocol = SttProtocol.DOUBAO_BIGMODEL,
    val sttProviderName: String = SttProviderPresets.doubao.displayName,
    val sttBaseUrl: String = SttProviderPresets.doubao.baseUrl,
    val sttModel: String = "",
    val sttResourceId: String = SttProviderPresets.doubao.resourceId,
    val sttApiKey: String = "",
    val activeSttProviderName: String = SttProviderPresets.doubao.displayName,
    val canReuseSttApiKey: Boolean = false,
    val isEditingSttApiKey: Boolean = false,
    val cookie: String = "",
    val llmConfigured: Boolean = false,
    val sttConfigured: Boolean = false,
    val cookieConfigured: Boolean = false,
    val isSaving: Boolean = false,
    val saveStatus: String = "",
    val offlineDictionary: OfflineDictionaryState = OfflineDictionaryState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secretStore: SecretStore,
    private val providerDao: ProviderProfileDao,
    private val dictionaryPackManager: OfflineDictionaryPackManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var persistedLlmProfile: ProviderProfileEntity? = null
    private var persistedSttProfile: ProviderProfileEntity? = null

    init {
        viewModelScope.launch {
            val storedProfile = migrateActiveProfile("LLM", providerDao.getEnabled("LLM"))
            val profile = storedProfile?.let(::upgradeKnownLlmPresetModel)
            if (profile != null && profile != storedProfile) providerDao.upsert(profile)
            val hasLlmSecret = profile?.secretAlias?.let { secretStore.read(it) } != null
            persistedLlmProfile = profile
            val presetId = profile?.let { LlmPresetId.fromStorageId(it.presetId) }
                ?: LlmPresetId.DEEPSEEK
            val preset = LlmProviderPresets.byId(presetId)
            val sttProfile = migrateActiveProfile("STT", providerDao.getEnabled("STT"))
            val hasSttSecret = sttProfile?.secretAlias?.let { secretStore.read(it) } != null
            persistedSttProfile = sttProfile
            val sttPresetId = sttProfile?.let { SttPresetId.fromStorageId(it.presetId) } ?: SttPresetId.DOUBAO
            val sttPreset = SttProviderPresets.byId(sttPresetId)
            _uiState.value = _uiState.value.copy(
                selectedLlmPreset = presetId,
                llmProviderName = profile?.name ?: preset.displayName,
                llmBaseUrl = profile?.baseUrl ?: preset.baseUrl,
                llmModel = profile?.model ?: preset.model,
                activeLlmProviderName = profile?.name ?: preset.displayName,
                activeLlmPreset = profile?.let { presetId },
                canReuseLlmApiKey = profile != null && hasLlmSecret,
                llmConfigured = profile != null && hasLlmSecret,
                selectedSttPreset = sttPresetId,
                sttProtocol = sttProfile?.let(::sttProtocol) ?: sttPreset.protocol,
                sttProviderName = sttProfile?.name ?: sttPreset.displayName,
                sttBaseUrl = sttProfile?.baseUrl ?: sttPreset.baseUrl,
                sttModel = sttProfile?.model ?: sttPreset.model,
                sttResourceId = sttProfile?.resourceId ?: sttPreset.resourceId,
                activeSttProviderName = sttProfile?.name ?: sttPreset.displayName,
                canReuseSttApiKey = sttProfile != null && hasSttSecret,
                sttConfigured = sttProfile != null && hasSttSecret,
                cookieConfigured = secretStore.read(COOKIE_SECRET) != null,
            )
        }
        viewModelScope.launch {
            dictionaryPackManager.state.collect { dictionary ->
                _uiState.value = _uiState.value.copy(offlineDictionary = dictionary)
            }
        }
    }

    fun selectLlmPreset(id: LlmPresetId) {
        val preset = LlmProviderPresets.byId(id)
        _uiState.value = _uiState.value.copy(
            selectedLlmPreset = id,
            llmProviderName = preset.displayName.takeUnless { id == LlmPresetId.CUSTOM }.orEmpty(),
            llmBaseUrl = preset.baseUrl,
            llmModel = preset.model,
            llmApiKey = "",
            canReuseLlmApiKey = false,
            isEditingLlmApiKey = false,
            saveStatus = "",
        )
        viewModelScope.launch {
            val persisted = providerDao.getByPreset("LLM", id.storageId)
            val reusable = persisted?.secretAlias?.let { secretStore.read(it) } != null
            persistedLlmProfile = persisted
            if (_uiState.value.selectedLlmPreset == id) {
                _uiState.value = _uiState.value.copy(
                    llmProviderName = persisted?.name ?: preset.displayName.takeUnless { id == LlmPresetId.CUSTOM }.orEmpty(),
                    llmBaseUrl = persisted?.baseUrl ?: preset.baseUrl,
                    llmModel = persisted?.model ?: preset.model,
                    canReuseLlmApiKey = reusable,
                )
            }
        }
    }

    fun updateLlmProviderName(value: String) {
        _uiState.value = _uiState.value.copy(llmProviderName = value)
    }

    fun updateLlmBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(llmBaseUrl = value)
    }

    fun updateLlmModel(value: String) { _uiState.value = _uiState.value.copy(llmModel = value) }
    fun updateLlmApiKey(value: String) { _uiState.value = _uiState.value.copy(llmApiKey = value) }
    fun changeLlmApiKey() { _uiState.value = _uiState.value.copy(isEditingLlmApiKey = true, llmApiKey = "") }
    fun selectSttPreset(id: SttPresetId) {
        val preset = SttProviderPresets.byId(id)
        _uiState.value = _uiState.value.copy(
            selectedSttPreset = id,
            sttProtocol = preset.protocol,
            sttProviderName = preset.displayName.takeUnless { id == SttPresetId.CUSTOM }.orEmpty(),
            sttBaseUrl = preset.baseUrl,
            sttModel = preset.model,
            sttResourceId = preset.resourceId,
            sttApiKey = "",
            canReuseSttApiKey = false,
            isEditingSttApiKey = false,
            saveStatus = "",
        )
        viewModelScope.launch {
            val persisted = providerDao.getByPreset("STT", id.storageId)
            val reusable = persisted?.secretAlias?.let { secretStore.read(it) } != null
            persistedSttProfile = persisted
            if (_uiState.value.selectedSttPreset == id) {
                _uiState.value = _uiState.value.copy(
                    sttProtocol = persisted?.let(::sttProtocol) ?: preset.protocol,
                    sttProviderName = persisted?.name ?: preset.displayName.takeUnless { id == SttPresetId.CUSTOM }.orEmpty(),
                    sttBaseUrl = persisted?.baseUrl ?: preset.baseUrl,
                    sttModel = persisted?.model ?: preset.model,
                    sttResourceId = persisted?.resourceId ?: preset.resourceId,
                    canReuseSttApiKey = reusable,
                )
            }
        }
    }

    fun updateSttProtocol(value: SttProtocol) {
        _uiState.value = _uiState.value.copy(sttProtocol = value)
    }
    fun updateSttProviderName(value: String) {
        _uiState.value = _uiState.value.copy(sttProviderName = value)
    }
    fun updateSttBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(sttBaseUrl = value)
    }
    fun updateSttModel(value: String) { _uiState.value = _uiState.value.copy(sttModel = value) }
    fun updateSttResourceId(value: String) { _uiState.value = _uiState.value.copy(sttResourceId = value) }
    fun updateSttApiKey(value: String) { _uiState.value = _uiState.value.copy(sttApiKey = value) }
    fun changeSttApiKey() { _uiState.value = _uiState.value.copy(isEditingSttApiKey = true, sttApiKey = "") }
    fun updateCookie(value: String) { _uiState.value = _uiState.value.copy(cookie = value) }

    fun saveLlm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveStatus = "")
            runCatching {
                val state = _uiState.value
                validateLlmProvider(state.llmProviderName, state.llmBaseUrl, state.llmModel)
                val submittedKey = state.llmApiKey.trim()
                val secretAlias = providerSecretAlias("LLM", state.selectedLlmPreset.storageId)
                if (submittedKey.isNotEmpty()) secretStore.save(secretAlias, submittedKey)
                require(submittedKey.isNotEmpty() || (state.canReuseLlmApiKey && secretStore.read(secretAlias) != null)) {
                    "请输入 ${state.llmProviderName.trim()} API Key"
                }
                ProviderProfileEntity(
                    id = providerProfileId("LLM", state.selectedLlmPreset.storageId),
                    kind = "LLM",
                    name = state.llmProviderName.trim(),
                    presetId = state.selectedLlmPreset.storageId,
                    baseUrl = normalizeLlmBaseUrl(state.llmBaseUrl),
                    model = state.llmModel.trim(),
                    secretAlias = secretAlias,
                    enabled = true,
                ).also { profile ->
                    providerDao.disableKind("LLM")
                    providerDao.upsert(profile)
                    persistedLlmProfile = profile
                }
            }.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    llmProviderName = profile.name,
                    llmBaseUrl = profile.baseUrl.orEmpty(),
                    llmModel = profile.model.orEmpty(),
                    llmApiKey = "",
                    activeLlmProviderName = profile.name,
                    activeLlmPreset = LlmPresetId.fromStorageId(profile.presetId),
                    canReuseLlmApiKey = true,
                    isEditingLlmApiKey = false,
                    llmConfigured = true,
                    isSaving = false,
                    saveStatus = "${profile.name} 配置已加密保存",
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, saveStatus = it.message ?: "保存失败")
            }
        }
    }

    fun saveStt() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveStatus = "")
            runCatching {
                val state = _uiState.value
                validateSttProvider(state.sttProviderName, state.sttBaseUrl, state.sttProtocol, state.sttModel, state.sttResourceId)
                val submittedKey = state.sttApiKey.trim()
                val secretAlias = providerSecretAlias("STT", state.selectedSttPreset.storageId)
                if (submittedKey.isNotEmpty()) secretStore.save(secretAlias, submittedKey)
                require(submittedKey.isNotEmpty() || (state.canReuseSttApiKey && secretStore.read(secretAlias) != null)) {
                    "请输入 ${state.sttProviderName.trim()} API Key"
                }
                ProviderProfileEntity(
                    id = providerProfileId("STT", state.selectedSttPreset.storageId),
                    kind = "STT",
                    name = state.sttProviderName.trim(),
                    presetId = state.selectedSttPreset.storageId,
                    baseUrl = normalizeLlmBaseUrl(state.sttBaseUrl),
                    model = state.sttModel.trim().ifEmpty { null },
                    resourceId = state.sttResourceId.trim().ifEmpty { null },
                    optionsJson = sttOptionsJson(state.sttProtocol),
                    secretAlias = secretAlias,
                    enabled = true,
                ).also { profile -> providerDao.disableKind("STT"); providerDao.upsert(profile); persistedSttProfile = profile }
            }.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    sttProviderName = profile.name,
                    sttBaseUrl = profile.baseUrl.orEmpty(),
                    sttModel = profile.model.orEmpty(),
                    sttResourceId = profile.resourceId.orEmpty(),
                    sttApiKey = "",
                    activeSttProviderName = profile.name,
                    canReuseSttApiKey = true,
                    isEditingSttApiKey = false,
                    sttConfigured = true,
                    isSaving = false,
                    saveStatus = "${profile.name} 配置已加密保存",
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, saveStatus = it.message ?: "保存失败")
            }
        }
    }

    fun saveCookie() {
        viewModelScope.launch {
            val value = _uiState.value.cookie.trim()
            if (value.isBlank()) {
                _uiState.value = _uiState.value.copy(saveStatus = "请输入 Cookie")
                return@launch
            }
            secretStore.save(COOKIE_SECRET, value)
            _uiState.value = _uiState.value.copy(cookie = "", cookieConfigured = true, saveStatus = "Cookie 已加密保存")
        }
    }

    fun showStatus(message: String) { _uiState.value = _uiState.value.copy(saveStatus = message) }
    fun clearStatus() { _uiState.value = _uiState.value.copy(saveStatus = "") }
    fun downloadOfflineDictionary() { dictionaryPackManager.download() }
    fun deleteOfflineDictionary() { dictionaryPackManager.delete() }
    fun save() { saveLlm(); saveStt() }

    private suspend fun migrateActiveProfile(kind: String, profile: ProviderProfileEntity?): ProviderProfileEntity? {
        profile ?: return null
        val presetId = when (kind) {
            "LLM" -> LlmPresetId.fromStorageId(profile.presetId).storageId
            else -> SttPresetId.fromStorageId(profile.presetId).storageId
        }
        val targetId = providerProfileId(kind, presetId)
        val targetAlias = providerSecretAlias(kind, presetId)
        if (profile.id == targetId && profile.secretAlias == targetAlias) return profile
        profile.secretAlias?.let { oldAlias ->
            if (secretStore.read(targetAlias) == null) secretStore.read(oldAlias)?.let { secretStore.save(targetAlias, it) }
        }
        val migrated = profile.copy(id = targetId, presetId = presetId, secretAlias = targetAlias, enabled = true)
        providerDao.disableKind(kind)
        providerDao.upsert(migrated)
        return migrated
    }

    private companion object {
        const val COOKIE_SECRET = "download.cookie.default"
    }
}

internal fun providerProfileId(kind: String, presetId: String): String = "${kind.lowercase()}-$presetId"
internal fun providerSecretAlias(kind: String, presetId: String): String = "provider.${kind.lowercase()}.$presetId"
