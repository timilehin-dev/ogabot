package io.github.timilehin_dev.ogabot.platform

import io.github.timilehin_dev.ogabot.domain.port.BotRegistry
import io.github.timilehin_dev.ogabot.domain.port.MemoryStore
import io.github.timilehin_dev.ogabot.domain.port.ToolProvider
import io.github.timilehin_dev.ogabot.domain.port.TurnEngine
import io.github.timilehin_dev.ogabot.domain.port.Vault

/**
 * Process-wide dependency holder. Populated by the Application; read by the
 * WorkManager workers, which have no other way to reach the graph.
 */
object ServiceLocator {
    var turnEngine: TurnEngine? = null
    var bots: BotRegistry? = null
    var memory: MemoryStore? = null
    var vault: Vault? = null
    var tools: List<ToolProvider>? = null
}
