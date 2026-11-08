package io.github.timilehin_dev.ogabot.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The accumulator is the heart of tool-call parsing — the exact thing that
 * broke on-device in the old build. These tests pin the three wire shapes.
 */
class ToolCallAccumulatorTest {

    @Test
    fun `fragments across many chunks assemble into one call`() {
        val acc = ToolCallAccumulator()
        acc.accept(ToolCallDelta(index = 0, id = "call_1", function = FunctionDelta(name = "web_read")))
        acc.accept(ToolCallDelta(index = 0, function = FunctionDelta(arguments = "{\"url\"")))
        acc.accept(ToolCallDelta(index = 0, function = FunctionDelta(arguments = ":\"https://x.com\"}")))

        val calls = acc.flush()
        assertEquals(1, calls.size)
        assertEquals("web_read", calls[0].name)
        assertEquals("call_1", calls[0].id)
        assertEquals("{\"url\":\"https://x.com\"}", calls[0].arguments)
    }

    @Test
    fun `a complete call in one chunk is passed through`() {
        val acc = ToolCallAccumulator()
        acc.accept(
            ToolCallDelta(
                index = 0,
                id = "call_9",
                function = FunctionDelta(name = "notify", arguments = "{\"title\":\"Hi\",\"body\":\"There\"}")
            )
        )

        val calls = acc.flush()
        assertEquals(1, calls.size)
        assertEquals("notify", calls[0].name)
        assertTrue(calls[0].arguments.contains("\"title\":\"Hi\""))
    }

    @Test
    fun `multiple parallel calls keep their indices`() {
        val acc = ToolCallAccumulator()
        acc.accept(ToolCallDelta(index = 0, id = "a", function = FunctionDelta(name = "web_read")))
        acc.accept(ToolCallDelta(index = 1, id = "b", function = FunctionDelta(name = "http_fetch")))
        acc.accept(ToolCallDelta(index = 0, function = FunctionDelta(arguments = "{\"url\":\"a\"}")))
        acc.accept(ToolCallDelta(index = 1, function = FunctionDelta(arguments = "{\"url\":\"b\"}")))

        val calls = acc.flush()
        assertEquals(2, calls.size)
        assertEquals("web_read", calls[0].name)
        assertEquals("http_fetch", calls[1].name)
    }

    @Test
    fun `blank arguments default to empty json object`() {
        val acc = ToolCallAccumulator()
        acc.accept(ToolCallDelta(index = 0, id = "x", function = FunctionDelta(name = "list_bots")))
        val calls = acc.flush()
        assertEquals("{}", calls[0].arguments)
    }

    @Test
    fun `flush resets state so the next turn starts clean`() {
        val acc = ToolCallAccumulator()
        acc.accept(ToolCallDelta(index = 0, id = "a", function = FunctionDelta(name = "x")))
        assertEquals(1, acc.flush().size)
        assertTrue(acc.isEmpty())
        assertEquals(0, acc.flush().size)
    }
}
