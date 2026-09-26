package com.zcode.android.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Parses Anthropic Messages SSE events. Tool calls arrive as content_block_start
// with a tool_use block followed by input_json_delta fragments.

object AnthropicEventParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): ParsedChunk {
        val root =
            json.parseToJsonElement(payload) as? JsonObject
                ?: return ParsedChunk(events = emptyList(), usage = null, toolCallFragments = emptyList())
        val type = (root["type"] as? JsonPrimitive)?.contentOrNull
        val events = mutableListOf<LlmEvent>()
        val fragments = mutableListOf<ToolCallFragment>()
        var usage: LlmUsage? = null
        when (type) {
            "message_start" -> {
                usage = parseMessageStart(root)
            }

            "content_block_start" -> {
                val index = (root["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val block = root["content_block"] as? JsonObject
                if ((block?.get("type") as? JsonPrimitive)?.contentOrNull == "tool_use") {
                    val id = (block["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val name = (block["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    fragments.add(ToolCallFragment(index = index, id = id, name = name, argumentsFragment = null))
                    events.add(LlmEvent.ToolCallStart(index = index, id = id, name = name))
                }
            }

            "content_block_delta" -> {
                val index = (root["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
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

                    "input_json_delta" -> {
                        val fragment = (delta?.get("partial_json") as? JsonPrimitive)?.contentOrNull
                        if (!fragment.isNullOrEmpty()) {
                            fragments.add(ToolCallFragment(index = index, id = null, name = null, argumentsFragment = fragment))
                            events.add(LlmEvent.ToolCallArguments(index = index, fragment = fragment))
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
        return ParsedChunk(events = events, usage = usage, toolCallFragments = fragments)
    }

    private fun parseMessageStart(root: JsonObject): LlmUsage? {
        val message = root["message"] as? JsonObject ?: return null
        val input = ((message["usage"] as? JsonObject)?.get("input_tokens") as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        return LlmUsage(inputTokens = input, outputTokens = 0)
    }
}
