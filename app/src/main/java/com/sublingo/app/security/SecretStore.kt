package com.sublingo.app.security

interface SecretStore {
    suspend fun save(alias: String, value: String)
    suspend fun read(alias: String): String?
    suspend fun delete(alias: String)
}
