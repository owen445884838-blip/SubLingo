package com.sublingo.app.domain.model

data class ProviderProfile(
    val id: String,
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
