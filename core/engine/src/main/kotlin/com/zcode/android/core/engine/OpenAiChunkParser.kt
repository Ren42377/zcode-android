package com.zcode.android.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Protocol neutral result of parsing one SSE payload: display events, usage when
// present, and tool call fragments the client merges across chunks.
data class ParsedChunk(
    val events: List<LlmEvent>,
    val usage: LlmUsage?,
    val toolCallFragments: List<ToolCallFragment>,
)

// One incremental piece of a tool call. Providers stream the call id and name
// first, then the arguments as JSON text fragments.
data class ToolCallFragment(
    val index: Int,
    val id: String?,
    val name: String?,
    val argumentsFragment: String?,
)

// Parses OpenAI chat completion chunks. GLM models expose their thinking as
// reasoning_content on the delta, and the final chunk (with
// stream_options.include_usage) carries the usage totals.

object OpenAiChunkParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): ParsedChunk {
        val root =
            json.parseToJsonElement(payload) as? JsonObject
                ?: return ParsedChunk(events = emptyList(), usage = null, toolCallFragments = emptyList())
        val events = mutableListOf<LlmEvent>()
        val fragments = mutableListOf<ToolCallFragment>()
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
            val toolCalls = delta["tool_calls"] as? JsonArray ?: continue
            for (call in toolCalls) {
                val callObject = call as? JsonObject ?: continue
                val index = (callObject["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val id = (callObject["id"] as? JsonPrimitive)?.contentOrNull
                val function = callObject["function"] as? JsonObject
                val name = (function?.get("name") as? JsonPrimitive)?.contentOrNull
                val arguments = (function?.get("arguments") as? JsonPrimitive)?.contentOrNull
                fragments.add(
                    ToolCallFragment(
                        index = index,
                        id = id,
                        name = name,
                        argumentsFragment = arguments,
                    ),
                )
                if (!name.isNullOrEmpty()) {
                    events.add(
                        LlmEvent.ToolCallStart(
                            index = index,
                            id = id.orEmpty(),
                            name = name,
                        ),
                    )
                }
            }
        }
        val usage =
            (root["usage"] as? JsonObject)?.let { usage ->
                val prompt = (usage["prompt_tokens"] as? JsonPrimitive)?.content?.toIntOrNull()
                val completion = (usage["completion_tokens"] as? JsonPrimitive)?.content?.toIntOrNull()
                if (prompt == null && completion == null) {
                    null
                } else {
                    LlmUsage(inputTokens = prompt ?: 0, outputTokens = completion ?: 0)
                }
            }
        return ParsedChunk(events = events, usage = usage, toolCallFragments = fragments)
    }
}
