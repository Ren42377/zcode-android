package com.zcode.android.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicEventParserTest {
    @Test
    fun parsesMessageStartInputUsage() {
        val payload = "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}"
        val chunk = AnthropicEventParser.parse(payload)
        assertTrue(chunk.events.isEmpty())
        assertEquals(LlmUsage(inputTokens = 7, outputTokens = 0), chunk.usage)
        assertTrue(chunk.toolCallFragments.isEmpty())
    }

    @Test
    fun parsesTextAndThinkingDeltas() {
        val textChunk =
            AnthropicEventParser.parse("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}")
        assertEquals(listOf<LlmEvent>(LlmEvent.TextDelta("hi")), textChunk.events)
        assertNull(textChunk.usage)

        val thinkingChunk =
            AnthropicEventParser.parse("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"hmm\"}}")
        assertEquals(listOf<LlmEvent>(LlmEvent.ThinkingDelta("hmm")), thinkingChunk.events)
    }

    @Test
    fun parsesToolUseBlocks() {
        val start =
            AnthropicEventParser.parse(
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\"," +
                    "\"id\":\"toolu1\",\"name\":\"bash\"}}",
            )
        assertEquals(
            listOf(LlmEvent.ToolCallStart(index = 1, id = "toolu1", name = "bash")),
            start.events,
        )
        assertEquals(1, start.toolCallFragments.size)
        assertEquals("toolu1", start.toolCallFragments.first().id)

        val arguments =
            AnthropicEventParser.parse(
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"cmd\\\"\"}}",
            )
        assertEquals(
            listOf(LlmEvent.ToolCallArguments(index = 1, fragment = "{\"cmd\"")),
            arguments.events,
        )
        assertEquals("{\"cmd\"", arguments.toolCallFragments.first().argumentsFragment)
    }

    @Test
    fun parsesMessageDeltaOutputUsage() {
        val payload = "{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":9}}"
        val chunk = AnthropicEventParser.parse(payload)
        assertEquals(LlmUsage(inputTokens = 0, outputTokens = 9), chunk.usage)
    }
}
