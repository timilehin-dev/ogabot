package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BotPersona(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val emoji: String = "",
    val systemPrompt: String,
    val createdAt: Long = 0L
)

/** File-backed bot registry: bots/<id>/bot.json (the multi-agent layer, Ogabot's core). */
object BotRegistry {
    private val json = Json { ignoreUnknownKeys = true }

    fun botsDir(context: Context): File = File(context.filesDir, "bots")

    fun list(context: Context): List<BotPersona> {
        val dir = botsDir(context)
        val bots = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { d ->
                val f = File(d, "bot.json")
                if (f.exists()) try { json.decodeFromString<BotPersona>(f.readText()) } catch (e: Exception) { null } else null
            }
            ?.sortedBy { it.createdAt }
            ?: emptyList()
        return bots
    }

    fun get(context: Context, id: String): BotPersona? {
        val f = File(File(botsDir(context), id), "bot.json")
        if (!f.exists()) return null
        return try { json.decodeFromString<BotPersona>(f.readText()) } catch (e: Exception) { null }
    }

    fun save(context: Context, persona: BotPersona) {
        val dir = File(botsDir(context), persona.id)
        dir.mkdirs()
        File(dir, "bot.json").writeText(json.encodeToString(persona))
    }

    fun create(context: Context, name: String, personaText: String, colorIndex: Int): BotPersona {
        val cleanName = name.trim()
        val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifEmpty { "bot" } + "-" + (System.currentTimeMillis() / 1000).toString(36)
        val persona = BotPersona(
            id = id,
            name = cleanName,
            colorIndex = colorIndex % 6,
            systemPrompt = personaText.trim(),
            createdAt = System.currentTimeMillis()
        )
        save(context, persona)
        return persona
    }
}
