package com.zcode.android.core.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// Endpoint configuration resolved from the selected provider preset.
data class LlmEndpoint(
    val baseUrl: String,
    val protocol: LlmProtocol,
    val apiKey: String,
)

// Streams completions from an OpenAI compatible or Anthropic compatible endpoint
// over Server Sent Events. The caller may cancel the returned Call handle to
// stop an in-flight stream without corrupting the conversation history.
class LlmClient(
    httpClient: OkHttpClient = OkHttpClient(),
) {
    private val client = httpClient
    private val json = Json

    suspend fun stream(
        request: LlmRequest,
        endpoint: LlmEndpoint,
        onEvent: (LlmEvent) -> Unit,
        onCallStarted: (Call) -> Unit = {},
    ): StreamOutcome =
        withContext(Dispatchers.IO) {
            val httpCall =
                client.newCall(
                    Request
                        .Builder()
                        .url(endpointUrl(endpoint))
                        .headers(endpoint.headers())
                        .post(endpoint.requestBody(request).toRequestBody(JSON_MEDIA_TYPE))
                        .build(),
                )
            onCallStarted(httpCall)
            httpCall.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody =
                        response.body
                            ?.string()
                            .orEmpty()
                            .take(ERROR_SNIPPET_CHARS)
                    throw IOException("HTTP ${response.code}: $errorBody")
                }
                val source = response.body.source()
                var usage: LlmUsage? = null
                val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (Sse.isDone(line)) {
                        break
                    }
                    val payload = Sse.dataPayload(line) ?: continue
                    val chunk =
                        when (endpoint.protocol) {
                            LlmProtocol.OPENAI -> OpenAiChunkParser.parse(payload)
                            LlmProtocol.ANTHROPIC -> AnthropicEventParser.parse(payload)
                        }
                    chunk.events.forEach(onEvent)
                    for (fragment in chunk.toolCallFragments) {
                        toolCallBuilders.getOrPut(fragment.index) { ToolCallBuilder() }.apply(fragment)
                    }
                    usage = combineUsage(usage, chunk.usage, endpoint.protocol)
                }
                StreamOutcome(
                    usage = usage,
                    toolCalls =
                        toolCallBuilders.toSortedMap().values.map { it.build() },
                )
            }
        }

    // Performs one lightweight request to confirm the endpoint and key work.
    suspend fun validate(endpoint: LlmEndpoint) {
        withContext(Dispatchers.IO) {
            val url =
                endpoint.baseUrl.trimEnd('/') +
                    when (endpoint.protocol) {
                        LlmProtocol.OPENAI -> {
                            "/models"
                        }

                        LlmProtocol.ANTHROPIC -> {
                            "/v1/models"
                        }
                    }
            val request =
                Request
                    .Builder()
                    .url(url)
                    .headers(endpoint.headers())
                    .get()
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("validation failed with HTTP ${response.code}")
                }
            }
        }
    }

    private class ToolCallBuilder {
        private var id: String = ""
        private var name: String = ""
        private val arguments = StringBuilder()

        fun apply(fragment: ToolCallFragment) {
            fragment.id?.let { id = it }
            fragment.name?.let { name = it }
            fragment.argumentsFragment?.let { arguments.append(it) }
        }

        fun build(): ToolCallRequest =
            ToolCallRequest(
                id = id,
                name = name,
                argumentsJson = arguments.toString(),
            )
    }

    private fun endpointUrl(endpoint: LlmEndpoint): String =
        endpoint.baseUrl.trimEnd('/') +
            when (endpoint.protocol) {
                LlmProtocol.OPENAI -> {
                    "/chat/completions"
                }

                LlmProtocol.ANTHROPIC -> {
                    "/v1/messages"
                }
            }

    private fun LlmEndpoint.headers() =
        okhttp3.Headers
            .Builder()
            .apply {
                when (protocol) {
                    LlmProtocol.OPENAI -> {
                        add("Authorization", "Bearer $apiKey")
                    }

                    LlmProtocol.ANTHROPIC -> {
                        add("x-api-key", apiKey)
                        add("anthropic-version", ANTHROPIC_VERSION)
                    }
                }
            }.build()

    private fun LlmEndpoint.requestBody(request: LlmRequest): String {
        val body =
            when (protocol) {
                LlmProtocol.OPENAI -> openAiBody(request)
                LlmProtocol.ANTHROPIC -> anthropicBody(request)
            }
        return json.encodeToString(JsonElement.serializer(), body)
    }

    private fun openAiBody(request: LlmRequest) =
        buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            put(
                "messages",
                buildJsonArray {
                    request.messages.forEach { message ->
                        add(message.openAiMessage())
                    }
                },
            )
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        request.tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", tool.name)
                                            put("description", tool.description)
                                            put("parameters", tool.parameters)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
            request.maxOutputTokens?.let { put("max_tokens", it) }
            // Reasoning effort is passed through verbatim; provider support for the
            // max level varies, and a rejected value surfaces as a stream error.
            request.thinkingEffort?.let { put("reasoning_effort", it.name.lowercase()) }
        }

    private fun LlmMessage.openAiMessage(): JsonObject =
        buildJsonObject {
            put("role", role.name.lowercase())
            if (content.isNotEmpty()) {
                put("content", content)
            }
            if (toolCalls.isNotEmpty()) {
                put(
                    "tool_calls",
                    buildJsonArray {
                        toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", call.name)
                                            put("arguments", call.argumentsJson)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
            toolCallId?.let { put("tool_call_id", it) }
        }

    private fun anthropicBody(request: LlmRequest) =
        buildJsonObject {
            put("model", request.model)
            put("stream", true)
            val budget = request.thinkingEffort?.budgetTokens()
            put("max_tokens", maxOf(request.maxOutputTokens ?: DEFAULT_ANTHROPIC_MAX_TOKENS, (budget ?: 0) + 1024))
            val system =
                request.messages
                    .filter { it.role == LlmRole.SYSTEM }
                    .joinToString(separator = "\n\n") { it.content }
            if (system.isNotEmpty()) {
                put("system", system)
            }
            put(
                "messages",
                buildJsonArray {
                    request.messages
                        .filter { it.role != LlmRole.SYSTEM }
                        .forEach { message ->
                            add(message.anthropicMessage())
                        }
                },
            )
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        request.tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("input_schema", tool.parameters)
                                },
                            )
                        }
                    },
                )
            }
            if (budget != null) {
                put(
                    "thinking",
                    buildJsonObject {
                        put("type", "enabled")
                        put("budget_tokens", budget)
                    },
                )
            }
        }

    private fun LlmMessage.anthropicMessage(): JsonObject =
        buildJsonObject {
            put("role", role.wireRole())
            when {
                toolCalls.isNotEmpty() -> {
                    put(
                        "content",
                        buildJsonArray {
                            if (content.isNotEmpty()) {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", content)
                                    },
                                )
                            }
                            toolCalls.forEach { call ->
                                add(
                                    buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", call.id)
                                        put("name", call.name)
                                        put("input", json.parseToJsonElement(call.argumentsJson.ifEmpty { "{}" }))
                                    },
                                )
                            }
                        },
                    )
                }

                role == LlmRole.TOOL -> {
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", toolCallId.orEmpty())
                                    put("content", content)
                                },
                            )
                        },
                    )
                }

                else -> {
                    put("content", content)
                }
            }
        }

    private fun LlmRole.wireRole(): String =
        when (this) {
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "assistant"
            LlmRole.SYSTEM -> "system"
            LlmRole.TOOL -> "user"
        }

    // Anthropic reports input usage in message_start and output usage in
    // message_delta, so values are merged; OpenAI reports final totals once.
    private fun combineUsage(
        current: LlmUsage?,
        chunk: LlmUsage?,
        protocol: LlmProtocol,
    ): LlmUsage? =
        when {
            chunk == null -> {
                current
            }

            protocol == LlmProtocol.OPENAI -> {
                chunk
            }

            current == null -> {
                chunk
            }

            else -> {
                LlmUsage(
                    inputTokens = if (chunk.inputTokens > 0) chunk.inputTokens else current.inputTokens,
                    outputTokens = if (chunk.outputTokens > 0) chunk.outputTokens else current.outputTokens,
                )
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_ANTHROPIC_MAX_TOKENS = 8192
        const val ERROR_SNIPPET_CHARS = 400

        fun ThinkingEffort.budgetTokens(): Int =
            when (this) {
                ThinkingEffort.LOW -> 2048
                ThinkingEffort.HIGH -> 16384
                ThinkingEffort.MAX -> 32768
            }
    }
}
