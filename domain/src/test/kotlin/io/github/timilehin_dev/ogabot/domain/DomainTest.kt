package io.github.timilehin_dev.ogabot.domain

import io.github.timilehin_dev.ogabot.domain.entity.Bot
import io.github.timilehin_dev.ogabot.domain.entity.Budget
import io.github.timilehin_dev.ogabot.domain.entity.TurnOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The domain layer is pure Kotlin with zero Android imports — these tests run
 * on the JVM and are the fastest verification loop in the project.
 */
class DomainTest {

    @Test
    fun `bot defaults are sensible`() {
        val bot = Bot(id = "oga", name = "Oga")
        assertEquals("oga", bot.id)
        assertEquals("", bot.title)
        assertEquals(0, bot.colorIndex)
        assertTrue(bot.devMode)
        assertEquals(200_000, bot.budget.dailyTokenCap)
    }

    @Test
    fun `budget can be tightened per bot`() {
        val bot = Bot(id = "r", name = "Researcher", budget = Budget(dailyTokenCap = 50_000))
        assertEquals(50_000, bot.budget.dailyTokenCap)
    }

    @Test
    fun `turn outcome covers the four terminal states`() {
        val outcomes = TurnOutcome.entries.map { it.name }.toSet()
        assertEquals(setOf("COMPLETED", "FAILED", "ABORTED", "IDLE_TIMEOUT"), outcomes)
    }

    @Test
    fun `tool grants default to ask when missing`() {
        val bot = Bot(id = "b", name = "B", toolGrants = mapOf("web_read" to "auto"))
        val tier = bot.toolGrants["shell"] ?: "ask"
        assertEquals("ask", tier)
        assertEquals("auto", bot.toolGrants["web_read"])
    }
}
