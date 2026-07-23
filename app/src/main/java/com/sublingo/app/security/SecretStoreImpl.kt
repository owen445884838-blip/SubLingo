package com.sublingo.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val preferences = context.getSharedPreferences("encrypted-secrets", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override suspend fun save(alias: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("$alias.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("$alias.data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun read(alias: String): String? {
        val iv = preferences.getString("$alias.iv", null) ?: return null
        val data = preferences.getString("$alias.data", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    override suspend fun delete(alias: String) {
        preferences.edit().remove("$alias.iv").remove("$alias.data").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    MASTER_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val MASTER_ALIAS = "sublingo-master-key-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
