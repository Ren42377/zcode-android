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
}

enum class ThinkingEffort {
    LOW,
    HIGH,
    MAX,
}

data class LlmMessage(
    val role: LlmRole,
    val content: String,
    // Assistant turns keep their thinking content so context rebuilds faithfully.
    val thinking: String? = null,
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val maxOutputTokens: Int? = null,
    val thinkingEffort: ThinkingEffort? = null,
)

sealed interface LlmEvent {
    data class TextDelta(
        val text: String,
    ) : LlmEvent

    data class ThinkingDelta(
        val text: String,
    ) : LlmEvent
}

data class LlmUsage(
    val inputTokens: Int,
    val outputTokens: Int,
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
            ProviderPreset("zai-coding-plan-anthropic", "Z.ai Coding Plan (Anthropic)", "https://api.z.ai/api/anthropic", LlmProtocol.ANTHROPIC),
            ProviderPreset("bigmodel", "BigModel", "https://open.bigmodel.cn/api/paas/v4", LlmProtocol.OPENAI),
            ProviderPreset("bigmodel-coding-plan", "BigModel Coding Plan", "https://open.bigmodel.cn/api/coding/paas/v4", LlmProtocol.OPENAI),
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
