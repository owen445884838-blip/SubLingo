package com.sublingo.app.ui.review

fun formatPhonetic(phonetic: String?): String? {
    val value = phonetic?.trim()?.ifBlank { null } ?: return null
    val core = value.trim().trim('/', '[', ']').trim()
    return core.takeIf(String::isNotBlank)?.let { "/$it/" }
}
