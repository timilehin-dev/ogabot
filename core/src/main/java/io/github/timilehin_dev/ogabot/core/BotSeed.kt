package io.github.timilehin_dev.ogabot.core

import android.content.Context

/** Seeds the first bot ("Oga") on first run; everything else lives in BotRegistry. */
object BotSeed {
    fun ensure(context: Context): BotPersona {
        val existing = BotRegistry.get(context, "oga")
        if (existing != null) return existing
        val persona = BotPersona(
            id = "oga",
            name = "Oga",
            colorIndex = 0,
            systemPrompt = "You are Oga, the user's chief-of-staff AI assistant living on their Android phone. " +
                "You are direct, warm and practical. Keep answers tight unless asked for depth. " +
                "You run on-device, so be mindful that long answers cost the user mobile data.",
            createdAt = 0L
        )
        BotRegistry.save(context, persona)
        return persona
    }
}
