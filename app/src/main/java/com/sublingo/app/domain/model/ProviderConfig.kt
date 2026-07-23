package com.sublingo.app.domain.model

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val apiKeyAlias: String,
    val model: String? = null,
    val resourceId: String? = null,
)
