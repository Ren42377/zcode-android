package com.zcode.android.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicEventParserTest {
    @Test
    fun parsesMessageStartInputUsage() {
        val payload = "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}"
        val (events, usage) = AnthropicEventParser.parse(payload)
        assertTrue(events.isEmpty())
        assertEquals(LlmUsage(inputTokens = 7, outputTokens = 0), usage)
    }

    @Test
    fun parsesTextAndThinkingDeltas() {
        val (textEvents, textUsage) =
            AnthropicEventParser.parse("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}")
        assertEquals(listOf<LlmEvent>(LlmEvent.TextDelta("hi")), textEvents)
        assertNull(textUsage)

        val (thinkingEvents, _) =
            AnthropicEventParser.parse("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"hmm\"}}")
        assertEquals(listOf<LlmEvent>(LlmEvent.ThinkingDelta("hmm")), thinkingEvents)
    }

    @Test
    fun parsesMessageDeltaOutputUsage() {
        val payload = "{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":9}}"
        val (_, usage) = AnthropicEventParser.parse(payload)
        assertEquals(LlmUsage(inputTokens = 0, outputTokens = 9), usage)
    }
}
