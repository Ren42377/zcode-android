package com.zcode.android.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChunkParserTest {
    @Test
    fun parsesThinkingAndTextDeltas() {
        val payload = "{\"choices\":[{\"delta\":{\"reasoning_content\":\"plan\",\"content\":\"hello\"}}]}"
        val chunk = OpenAiChunkParser.parse(payload)
        assertEquals(
            listOf<LlmEvent>(
                LlmEvent.ThinkingDelta("plan"),
                LlmEvent.TextDelta("hello"),
            ),
            chunk.events,
        )
        assertNull(chunk.usage)
        assertTrue(chunk.toolCallFragments.isEmpty())
    }

    @Test
    fun parsesFinalUsage() {
        val payload = "{\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"
        val chunk = OpenAiChunkParser.parse(payload)
        assertTrue(chunk.events.isEmpty())
        assertEquals(LlmUsage(inputTokens = 10, outputTokens = 5), chunk.usage)
    }

    @Test
    fun parsesToolCallFragments() {
        val payload =
            "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call1\",\"function\":" +
                "{\"name\":\"read\",\"arguments\":\"{\\\"path\\\"\"}}]}}]}"
        val chunk = OpenAiChunkParser.parse(payload)
        assertEquals(1, chunk.toolCallFragments.size)
        val fragment = chunk.toolCallFragments.first()
        assertEquals(0, fragment.index)
        assertEquals("call1", fragment.id)
        assertEquals("read", fragment.name)
        assertEquals("{\"path\"", fragment.argumentsFragment)
        assertEquals(
            listOf(LlmEvent.ToolCallStart(index = 0, id = "call1", name = "read")),
            chunk.events,
        )
    }

    @Test
    fun ignoresUnexpectedPayloadShapes() {
        val chunk = OpenAiChunkParser.parse("{\"error\":\"nope\"}")
        assertTrue(chunk.events.isEmpty())
        assertNull(chunk.usage)
        assertTrue(chunk.toolCallFragments.isEmpty())
    }
}
