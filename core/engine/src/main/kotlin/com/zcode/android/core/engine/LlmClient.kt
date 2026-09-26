package com.zcode.android.core.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    ): LlmUsage? =
        withContext(Dispatchers.IO) {
            val httpCall =
                client.newCall(
                    Request
                        .Builder()
                        .url(endpointUrl(endpoint))
                        .headers(endpoint.headers(apiKey))
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
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (Sse.isDone(line)) {
                        break
                    }
                    val payload = Sse.dataPayload(line) ?: continue
                    val (events, chunkUsage) =
                        when (endpoint.protocol) {
                            LlmProtocol.OPENAI -> OpenAiChunkParser.parse(payload)
                            LlmProtocol.ANTHROPIC -> AnthropicEventParser.parse(payload)
                        }
                    events.forEach(onEvent)
                    usage = combineUsage(usage, chunkUsage, endpoint.protocol)
                }
                usage
            }
        }

    private fun endpointUrl(endpoint: LlmEndpoint): String =
        endpoint.baseUrl.trimEnd('/') +
            when (endpoint.protocol) {
                LlmProtocol.OPENAI -> "/chat/completions"
                LlmProtocol.ANTHROPIC -> "/v1/messages"
            }

    private fun LlmEndpoint.headers(apiKey: String) =
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
                        add(
                            buildJsonObject {
                                put("role", message.role.name.lowercase())
                                put("content", message.content)
                            },
                        )
                    }
                },
            )
            request.maxOutputTokens?.let { put("max_tokens", it) }
            // Reasoning effort is passed through verbatim; provider support for the
            // max level varies, and a rejected value surfaces as a stream error.
            request.thinkingEffort?.let { put("reasoning_effort", it.name.lowercase()) }
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
                            add(
                                buildJsonObject {
                                    put("role", message.role.wireRole())
                                    put("content", message.content)
                                },
                            )
                        }
                },
            )
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

    private fun LlmRole.wireRole(): String =
        when (this) {
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "assistant"
            LlmRole.SYSTEM -> "system"
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
