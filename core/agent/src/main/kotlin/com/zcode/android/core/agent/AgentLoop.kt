package com.zcode.android.core.agent

import com.zcode.android.core.engine.LlmClient
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.LlmEvent
import com.zcode.android.core.engine.LlmMessage
import com.zcode.android.core.engine.LlmRequest
import com.zcode.android.core.engine.LlmRole
import com.zcode.android.core.engine.LlmUsage
import com.zcode.android.core.engine.ThinkingEffort
import com.zcode.android.core.engine.ToolCallRequest
import com.zcode.android.core.tools.TodoWriteTool
import com.zcode.android.core.tools.ToolContext
import com.zcode.android.core.tools.ToolOutcome
import com.zcode.android.core.tools.ToolRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import javax.inject.Inject

data class TurnResult(
    val content: String,
    val thinking: String?,
    val usage: LlmUsage?,
)

// Runs the agent turn loop: stream a completion, execute requested tools under
// the permission rules, and repeat until the model answers without tool calls.
class AgentLoop
    @Inject
    constructor(
        private val client: LlmClient,
        private val registry: ToolRegistry,
        private val gate: PermissionGate,
        private val approvals: ApprovalCoordinator,
    ) {
        suspend fun run(
            history: List<LlmMessage>,
            endpoint: LlmEndpoint,
            model: String,
            maxOutputTokens: Int?,
            thinkingEffort: ThinkingEffort?,
            mode: PermissionMode,
            context: ToolContext,
            onEvent: (AgentEvent) -> Unit,
            onCallStarted: (Call) -> Unit = {},
            instructions: String? = null,
        ): TurnResult {
            val messages = mutableListOf(LlmMessage(role = LlmRole.SYSTEM, content = buildSystemPrompt(instructions)))
            messages.addAll(history)
            var totalUsage: LlmUsage? = null
            var lastText = ""
            val thinking = StringBuilder()
            while (true) {
                val text = StringBuilder()
                val outcome =
                    client.stream(
                        request =
                            LlmRequest(
                                model = model,
                                messages = messages,
                                maxOutputTokens = maxOutputTokens,
                                thinkingEffort = thinkingEffort,
                                tools = registry.specs(),
                            ),
                        endpoint = endpoint,
                        onEvent = { event ->
                            when (event) {
                                is LlmEvent.TextDelta -> {
                                    text.append(event.text)
                                    onEvent(AgentEvent.TextDelta(event.text))
                                }

                                is LlmEvent.ThinkingDelta -> {
                                    thinking.append(event.text)
                                    onEvent(AgentEvent.ThinkingDelta(event.text))
                                }

                                is LlmEvent.ToolCallStart -> {}

                                is LlmEvent.ToolCallArguments -> {}
                            }
                        },
                        onCallStarted = onCallStarted,
                    )
                totalUsage = mergeUsage(totalUsage, outcome.usage)
                lastText = text.toString()
                if (outcome.toolCalls.isEmpty()) {
                    break
                }
                messages += LlmMessage(role = LlmRole.ASSISTANT, content = lastText, toolCalls = outcome.toolCalls)
                for (call in outcome.toolCalls) {
                    executeToolCall(call, mode, context, messages, onEvent)
                }
            }
            return TurnResult(
                content = lastText,
                thinking = thinking.toString().ifEmpty { null },
                usage = totalUsage,
            )
        }

        private suspend fun executeToolCall(
            call: ToolCallRequest,
            mode: PermissionMode,
            context: ToolContext,
            messages: MutableList<LlmMessage>,
            onEvent: (AgentEvent) -> Unit,
        ) {
            val tool = registry.byName(call.name)
            if (tool == null) {
                finishTool(call, onEvent, messages, "Unknown tool: ${call.name}", isError = true)
                return
            }
            val summary = summarize(call.name, call.argumentsJson)
            when (gate.decide(tool.name, mode)) {
                PermissionDecision.Deny -> {
                    finishTool(
                        call,
                        onEvent,
                        messages,
                        "This action is not allowed in ${mode.label} mode.",
                        isError = true,
                    )
                    return
                }

                PermissionDecision.Ask -> {
                    onEvent(AgentEvent.ApprovalRequested(callId = call.id, name = call.name, summary = summary))
                    val answer = approvals.register(call.id).await()
                    when (answer) {
                        ApprovalAnswer.ALLOW_ALWAYS -> {
                            gate.allowAlways(call.name)
                        }

                        ApprovalAnswer.REJECT_ONCE -> {
                            finishTool(call, onEvent, messages, "The user rejected this action.", isError = true)
                            return
                        }

                        ApprovalAnswer.REJECT_ALWAYS -> {
                            gate.denyAlways(call.name)
                            finishTool(
                                call,
                                onEvent,
                                messages,
                                "The user rejected this action and similar ones.",
                                isError = true,
                            )
                            return
                        }

                        ApprovalAnswer.ALLOW_ONCE -> {}
                    }
                }

                PermissionDecision.Allow -> {}
            }
            onEvent(AgentEvent.ToolStarted(callId = call.id, name = call.name, summary = summary))
            val args = parseArguments(call.argumentsJson)
            val outcome =
                try {
                    tool.execute(args, context)
                } catch (t: Throwable) {
                    ToolOutcome(outputForModel = "Tool failed: ${t.message}", isError = true)
                }
            if (tool is TodoWriteTool) {
                onEvent(AgentEvent.TodoListUpdated(tool.parseTodos(args)))
            }
            messages += LlmMessage(role = LlmRole.TOOL, content = outcome.outputForModel, toolCallId = call.id)
            onEvent(
                AgentEvent.ToolFinished(
                    callId = call.id,
                    name = call.name,
                    outputForModel = outcome.outputForModel,
                    isError = outcome.isError,
                ),
            )
        }

        private fun finishTool(
            call: ToolCallRequest,
            onEvent: (AgentEvent) -> Unit,
            messages: MutableList<LlmMessage>,
            message: String,
            isError: Boolean,
        ) {
            messages += LlmMessage(role = LlmRole.TOOL, content = message, toolCallId = call.id)
            onEvent(AgentEvent.ToolFinished(callId = call.id, name = call.name, outputForModel = message, isError = isError))
        }

        private fun parseArguments(argumentsJson: String): JsonObject =
            if (argumentsJson.isBlank()) {
                JsonObject(emptyMap())
            } else {
                runCatching { json.parseToJsonElement(argumentsJson) as? JsonObject }
                    .getOrNull()
                    ?: JsonObject(emptyMap())
            }

        private fun summarize(
            toolName: String,
            argumentsJson: String,
        ): String {
            if (toolName == "TodoWrite") {
                return "todo list"
            }
            val args = parseArguments(argumentsJson)
            val key =
                when (toolName) {
                    "Bash" -> "command"
                    "WebFetch" -> "url"
                    "WebSearch" -> "query"
                    else -> "path"
                }
            val value = (args[key] as? JsonPrimitive)?.contentOrNull
            return (value ?: argumentsJson).take(SUMMARY_CHARS)
        }

        private fun mergeUsage(
            current: LlmUsage?,
            next: LlmUsage?,
        ): LlmUsage? =
            when {
                current == null -> {
                    next
                }

                next == null -> {
                    current
                }

                else -> {
                    LlmUsage(
                        inputTokens = current.inputTokens + next.inputTokens,
                        outputTokens = current.outputTokens + next.outputTokens,
                    )
                }
            }

        private companion object {
            const val SUMMARY_CHARS = 120
            val json = Json { ignoreUnknownKeys = true }

            fun buildSystemPrompt(instructions: String?): String {
                val base =
                    "You are ZCode, an AI coding agent running on an Android device. " +
                        "Help the user with software engineering tasks and be precise and concise."
                return if (instructions.isNullOrEmpty()) {
                    base
                } else {
                    "$base\n\n$instructions"
                }
            }
        }
    }
