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
    val emoji: String,
    val systemPrompt: String
)

/** Seeds the first bot ("Oga") on first run; later phases grow this into the BotRegistry. */
object BotSeed {
    private const val DEFAULT = "oga"
    private val json = Json { ignoreUnknownKeys = true }

    fun ensure(context: Context): BotPersona {
        val dir = File(context.filesDir, "bots/$DEFAULT")
        val f = File(dir, "bot.json")
        if (!f.exists()) {
            dir.mkdirs()
            val persona = BotPersona(
                id = DEFAULT,
                name = "Oga",
                emoji = "\uD83E\uDD11",
                systemPrompt = "You are Oga, the user's chief-of-staff AI assistant living on their Android phone. " +
                    "You are direct, warm and practical. Keep answers tight unless asked for depth. " +
                    "You run on-device, so be mindful that long answers cost the user mobile data."
            )
            f.writeText(json.encodeToString(persona))
        }
        return json.decodeFromString<BotPersona>(f.readText())
    }
}
