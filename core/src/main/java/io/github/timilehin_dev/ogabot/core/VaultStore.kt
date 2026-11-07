package io.github.timilehin_dev.ogabot.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the provider API key encrypted with an Android Keystore AES-GCM key. */
object VaultStore {
    private const val KEY_ALIAS = "ogabot_master"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FILE = "vault_provider.bin"
    private val json = Json { ignoreUnknownKeys = true }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun saveProvider(context: Context, config: ProviderConfig) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val data = cipher.doFinal(json.encodeToString(config).toByteArray(Charsets.UTF_8))
        File(context.filesDir, FILE).writeBytes(iv + data)
    }

    fun loadProvider(context: Context): ProviderConfig? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return try {
            val bytes = f.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
            json.decodeFromString<ProviderConfig>(plain.decodeToString())
        } catch (e: Exception) {
            null
        }
    }
}
