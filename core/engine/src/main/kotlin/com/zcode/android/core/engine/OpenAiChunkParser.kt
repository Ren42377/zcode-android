package com.zcode.android.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

// Parses OpenAI chat completion chunks into protocol agnostic events. GLM models
// expose their thinking as reasoning_content on the delta, and the final chunk
// (with stream_options.include_usage) carries the usage totals.

object OpenAiChunkParser {
    private val json = Json { ignoreUnknownKeys = true }

    // Returns the events carried by one SSE payload plus the usage when present.
    fun parse(payload: String): Pair<List<LlmEvent>, LlmUsage?> {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: return Pair(emptyList(), null)
        val events = mutableListOf<LlmEvent>()
        val choices = root["choices"] as? JsonArray ?: JsonArray(emptyList())
        for (choice in choices) {
            val delta = (choice as? JsonObject)?.get("delta") as? JsonObject ?: continue
            val thinking = (delta["reasoning_content"] as? JsonPrimitive)?.contentOrNull
            if (!thinking.isNullOrEmpty()) {
                events.add(LlmEvent.ThinkingDelta(thinking))
            }
            val text = (delta["content"] as? JsonPrimitive)?.contentOrNull
            if (!text.isNullOrEmpty()) {
                events.add(LlmEvent.TextDelta(text))
            }
        }
        val usage = (root["usage"] as? JsonObject)?.let { usage ->
            val prompt = (usage["prompt_tokens"] as? JsonPrimitive)?.content?.toIntOrNull()
            val completion = (usage["completion_tokens"] as? JsonPrimitive)?.content?.toIntOrNull()
            if (prompt == null && completion == null) {
                null
            } else {
                LlmUsage(inputTokens = prompt ?: 0, outputTokens = completion ?: 0)
            }
        }
        return Pair(events, usage)
    }
}
