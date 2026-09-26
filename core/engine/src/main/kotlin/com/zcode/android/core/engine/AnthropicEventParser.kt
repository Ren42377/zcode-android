package com.zcode.android.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Parses Anthropic Messages SSE events into protocol agnostic events. Relevant
// event types: message_start (input usage), content_block_delta with text_delta
// or thinking_delta, message_delta (output usage), message_stop.

object AnthropicEventParser {
    private val json = Json { ignoreUnknownKeys = true }

    // Returns the events carried by one SSE payload plus the usage when present.
    fun parse(payload: String): Pair<List<LlmEvent>, LlmUsage?> {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: return Pair(emptyList(), null)
        val type = (root["type"] as? JsonPrimitive)?.contentOrNull
        val events = mutableListOf<LlmEvent>()
        var usage: LlmUsage? = null
        when (type) {
            "message_start" -> {
                usage = parseMessageStart(root)
            }
            "content_block_delta" -> {
                val delta = root["delta"] as? JsonObject
                when ((delta?.get("type") as? JsonPrimitive)?.contentOrNull) {
                    "text_delta" -> {
                        val text = (delta?.get("text") as? JsonPrimitive)?.contentOrNull
                        if (!text.isNullOrEmpty()) {
                            events.add(LlmEvent.TextDelta(text))
                        }
                    }
                    "thinking_delta" -> {
                        val thinking = (delta?.get("thinking") as? JsonPrimitive)?.contentOrNull
                        if (!thinking.isNullOrEmpty()) {
                            events.add(LlmEvent.ThinkingDelta(thinking))
                        }
                    }
                }
            }
            "message_delta" -> {
                val output = ((root["usage"] as? JsonObject)?.get("output_tokens") as? JsonPrimitive)?.content?.toIntOrNull()
                if (output != null) {
                    usage = LlmUsage(inputTokens = 0, outputTokens = output)
                }
            }
        }
        return Pair(events, usage)
    }

    private fun parseMessageStart(root: JsonObject): LlmUsage? {
        val message = root["message"] as? JsonObject ?: return null
        val input = ((message["usage"] as? JsonObject)?.get("input_tokens") as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        return LlmUsage(inputTokens = input, outputTokens = 0)
    }
}
