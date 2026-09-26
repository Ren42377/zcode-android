package com.zcode.android.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChunkParserTest {
    @Test
    fun parsesThinkingAndTextDeltas() {
        val payload = "{\"choices\":[{\"delta\":{\"reasoning_content\":\"plan\",\"content\":\"hello\"}}]}"
        val (events, usage) = OpenAiChunkParser.parse(payload)
        assertEquals(
            listOf<LlmEvent>(
                LlmEvent.ThinkingDelta("plan"),
                LlmEvent.TextDelta("hello"),
            ),
            events,
        )
        assertNull(usage)
    }

    @Test
    fun parsesFinalUsage() {
        val payload = "{\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"
        val (events, usage) = OpenAiChunkParser.parse(payload)
        assertTrue(events.isEmpty())
        assertEquals(LlmUsage(inputTokens = 10, outputTokens = 5), usage)
    }

    @Test
    fun ignoresUnexpectedPayloadShapes() {
        val (events, usage) = OpenAiChunkParser.parse("{\"error\":\"nope\"}")
        assertTrue(events.isEmpty())
        assertNull(usage)
    }
}
