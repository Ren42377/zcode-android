package com.zcode.android.core.engine

// Protocol agnostic models shared by the OpenAI compatible and Anthropic
// compatible clients.

enum class LlmProtocol {
    OPENAI,
    ANTHROPIC,
}

enum class LlmRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

enum class ThinkingEffort {
    LOW,
    HIGH,
    MAX,
}

// One tool invocation requested by the model. argumentsJson holds the raw JSON
// object text produced by the model.
data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

// Static definition of a tool exposed to the model. parameters carries the JSON
// schema object for the tool input.
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
)

data class LlmMessage(
    val role: LlmRole,
    val content: String,
    // Assistant turns keep their thinking content so context rebuilds faithfully.
    val thinking: String? = null,
    // Assistant turns carry the requested tool calls; TOOL turns carry the id of
    // the call this message answers.
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null,
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val maxOutputTokens: Int? = null,
    val thinkingEffort: ThinkingEffort? = null,
    val tools: List<ToolSpec> = emptyList(),
)

sealed interface LlmEvent {
    data class TextDelta(
        val text: String,
    ) : LlmEvent

    data class ThinkingDelta(
        val text: String,
    ) : LlmEvent

    data class ToolCallStart(
        val index: Int,
        val id: String,
        val name: String,
    ) : LlmEvent

    data class ToolCallArguments(
        val index: Int,
        val fragment: String,
    ) : LlmEvent
}

data class LlmUsage(
    val inputTokens: Int,
    val outputTokens: Int,
)

// Final state of one streamed completion.
data class StreamOutcome(
    val usage: LlmUsage?,
    val toolCalls: List<ToolCallRequest>,
)

data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val protocol: LlmProtocol,
)

data class ModelSpec(
    val id: String,
    val label: String,
    val contextTokens: Long,
    val maxOutputTokens: Int,
    val supportsThinking: Boolean = false,
    val multimodal: Boolean = false,
)

object ProviderPresets {
    val all: List<ProviderPreset> =
        listOf(
            ProviderPreset("zai-open-platform", "Z.ai Open Platform", "https://api.z.ai/api/paas/v4", LlmProtocol.OPENAI),
            ProviderPreset("zai-coding-plan", "Z.ai Coding Plan", "https://api.z.ai/api/coding/paas/v4", LlmProtocol.OPENAI),
            ProviderPreset(
                "zai-coding-plan-anthropic",
                "Z.ai Coding Plan (Anthropic)",
                "https://api.z.ai/api/anthropic",
                LlmProtocol.ANTHROPIC,
            ),
            ProviderPreset("bigmodel", "BigModel", "https://open.bigmodel.cn/api/paas/v4", LlmProtocol.OPENAI),
            ProviderPreset(
                "bigmodel-coding-plan",
                "BigModel Coding Plan",
                "https://open.bigmodel.cn/api/coding/paas/v4",
                LlmProtocol.OPENAI,
            ),
        )

    fun byId(id: String): ProviderPreset? = all.firstOrNull { it.id == id }
}

object BuiltinModels {
    val glm53 =
        ModelSpec(
            id = "glm-5.3",
            label = "GLM-5.3",
            contextTokens = 1_000_000,
            maxOutputTokens = 131_072,
            supportsThinking = true,
        )

    val glm53Flash =
        ModelSpec(
            id = "glm-5.3-flash",
            label = "GLM-5.3 Flash",
            contextTokens = 1_000_000,
            maxOutputTokens = 131_072,
            supportsThinking = true,
            multimodal = true,
        )

    val all: List<ModelSpec> = listOf(glm53, glm53Flash)

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
