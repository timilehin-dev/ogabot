package io.github.timilehin_dev.ogabot.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.timilehin_dev.ogabot.domain.entity.AuditEntry
import io.github.timilehin_dev.ogabot.domain.entity.Bot
import io.github.timilehin_dev.ogabot.domain.entity.MemoryDoc
import io.github.timilehin_dev.ogabot.domain.entity.ProviderConfig
import io.github.timilehin_dev.ogabot.domain.port.AuditSink
import io.github.timilehin_dev.ogabot.domain.port.BotRegistry
import io.github.timilehin_dev.ogabot.domain.port.MemoryStore
import io.github.timilehin_dev.ogabot.domain.port.Vault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * File-backed bot registry. A bot is a directory: bots/<id>/bot.json.
 * Files are the source of truth; the database is only an index.
 */
class BotFileStore(private val context: Context) : BotRegistry {

    private val dir = File(context.filesDir, "bots")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun botFile(id: String) = File(File(dir, id), "bot.json")

    override fun list(): List<Bot> = dir.listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { d ->
            val f = botFile(d.name)
            if (f.exists()) runCatching { json.decodeFromString<Bot>(f.readText()) }.getOrNull() else null
        }
        ?.sortedBy { it.createdAt }
        ?: emptyList()

    override fun get(id: String): Bot? {
        val f = botFile(id)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<Bot>(f.readText()) }.getOrNull()
    }

    override fun save(bot: Bot) {
        File(dir, bot.id).mkdirs()
        botFile(bot.id).writeText(json.encodeToString(bot))
    }

    override fun delete(id: String) {
        File(dir, id).deleteRecursively()
    }

    override fun create(name: String, persona: String, colorIndex: Int): Bot {
        val clean = name.trim()
        val id = clean.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "bot" } + "-" + (System.currentTimeMillis() / 1000).toString(36)
        val bot = Bot(
            id = id,
            name = clean,
            persona = persona.trim(),
            colorIndex = colorIndex % 6,
            createdAt = System.currentTimeMillis()
        )
        save(bot)
        return bot
    }
}

/** Bounded memory per bot: bots/<id>/memory/MEMORY.md. */
class MemoryFileStore(private val context: Context) : MemoryStore {

    private val root = File(context.filesDir, "bots")

    private fun file(botId: String) = File(File(File(root, botId), "memory"), "MEMORY.md")

    override fun load(botId: String): MemoryDoc? {
        val f = file(botId)
        if (!f.exists()) return null
        return MemoryDoc(botId, f.readText(), f.lastModified())
    }

    override fun save(doc: MemoryDoc) {
        file(doc.botId).apply { parentFile?.mkdirs() }.writeText(doc.content)
    }

    override fun inject(botId: String): String {
        val content = load(botId)?.content?.trim().orEmpty()
        if (content.isEmpty()) return ""
        return "\n\n[Long-term memory about the user — keep this in mind]\n$content\n"
    }
}

/** Append-only audit trail: audit/<yyyy-MM>.jsonl. */
class AuditFileStore(private val context: Context) : AuditSink {

    private val dir = File(context.filesDir, "audit")
    private val json = Json { ignoreUnknownKeys = true }

    override fun record(entry: AuditEntry) {
        runCatching {
            dir.mkdirs()
            val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(entry.timestamp))
            File(dir, "$month.jsonl").appendText(json.encodeToString(entry) + "\n")
        }
    }

    override fun recent(limit: Int): List<AuditEntry> {
        val files = dir.listFiles()?.filter { it.extension == "jsonl" }?.sortedBy { it.name }?.takeLast(2)
            ?: return emptyList()
        return files.flatMap { f ->
            f.readLines().filter { it.isNotBlank() }.mapNotNull {
                runCatching { json.decodeFromString<AuditEntry>(it) }.getOrNull()
            }
        }.takeLast(limit)
    }
}

/** Provider keys, encrypted with an Android Keystore AES-GCM key. */
class VaultStore(private val context: Context) : Vault {

    companion object {
        private const val KEY_ALIAS = "ogabot_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FILE = "vault_provider.bin"
    }
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

    override fun save(config: ProviderConfig) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val data = cipher.doFinal(json.encodeToString(config).toByteArray(Charsets.UTF_8))
            File(context.filesDir, FILE).writeBytes(iv + data)
        }
    }

    override fun load(): ProviderConfig? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return runCatching {
            val bytes = f.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
            json.decodeFromString<ProviderConfig>(plain.decodeToString())
        }.getOrNull()
    }
}
