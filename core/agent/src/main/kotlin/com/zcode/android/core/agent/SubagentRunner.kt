package com.zcode.android.core.agent

import android.content.Context
import com.zcode.android.core.engine.LlmClient
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.LlmEvent
import com.zcode.android.core.engine.LlmMessage
import com.zcode.android.core.engine.LlmRequest
import com.zcode.android.core.engine.LlmRole
import com.zcode.android.core.engine.ThinkingEffort
import com.zcode.android.core.tools.ToolContext
import com.zcode.android.core.tools.ToolOutcome
import com.zcode.android.core.tools.ToolRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class SubagentSpec(
    val name: String,
    val description: String,
    // null means the built-in read-only default; otherwise the exact tool names.
    val allowedTools: Set<String>? = null,
    val maxTurns: Int = DEFAULT_MAX_TURNS,
) {
    companion object {
        const val DEFAULT_MAX_TURNS = 8

        val GENERAL_PURPOSE: SubagentSpec =
            SubagentSpec(
                name = "general-purpose",
                description = "Researches complex questions and executes multi step tasks.",
                allowedTools = setOf("Read", "Glob", "Grep", "WebFetch", "WebSearch", "TodoWrite"),
            )

        val EXPLORE: SubagentSpec =
            SubagentSpec(
                name = "Explore",
                description = "Fast read only exploration of the workspace and the web.",
                allowedTools = setOf("Read", "Glob", "Grep", "WebFetch", "WebSearch"),
                maxTurns = 6,
            )
    }
}

// Runs focused subagent conversations. Subagents never spawn other subagents and
// share no memory with the main loop; their final answer returns to the caller.
@Singleton
class SubagentRunner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: LlmClient,
        private val registry: ToolRegistry,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        fun loadCustomAgents(workspaceRoot: File?): Map<String, SubagentSpec> {
            val roots =
                buildList {
                    add(File(context.filesDir, "config/agents"))
                    if (workspaceRoot != null) {
                        add(File(workspaceRoot, ".zcode/agents"))
                        add(File(workspaceRoot, "agents"))
                    }
                }
            return roots.flatMap { root -> scan(root) }.associateBy { it.name }
        }

        suspend fun run(
            spec: SubagentSpec,
            prompt: String,
            endpoint: LlmEndpoint,
            model: String,
            thinkingEffort: ThinkingEffort?,
            context: ToolContext,
        ): String {
            val tools = registry.specs().filter { spec.allowedTools == null || it.name in spec.allowedTools }
            val messages =
                mutableListOf(
                    LlmMessage(
                        role = LlmRole.SYSTEM,
                        content =
                            "You are the " + spec.name + " subagent. " + spec.description +
                                " Work only inside this task and return a complete, self contained answer.",
                    ),
                    LlmMessage(role = LlmRole.USER, content = prompt),
                )
            var lastText = ""
            repeat(spec.maxTurns) {
                val text = StringBuilder()
                val outcome =
                    client.stream(
                        request =
                            LlmRequest(
                                model = model,
                                messages = messages,
                                thinkingEffort = thinkingEffort,
                                tools = tools,
                            ),
                        endpoint = endpoint,
                        onEvent = { event ->
                            if (event is LlmEvent.TextDelta) {
                                text.append(event.text)
                            }
                        },
                    )
                lastText = text.toString()
                if (outcome.toolCalls.isEmpty()) {
                    return lastText
                }
                messages += LlmMessage(role = LlmRole.ASSISTANT, content = lastText, toolCalls = outcome.toolCalls)
                for (call in outcome.toolCalls) {
                    val tool = registry.byName(call.name)
                    val result: String =
                        when {
                            tool == null || spec.allowedTools?.contains(call.name) == false ->
                                "Tool " + call.name + " is not available to this subagent."

                            else -> {
                                val args =
                                    runCatching { json.parseToJsonElement(call.argumentsJson) as? JsonObject }.getOrNull()
                                        ?: JsonObject(emptyMap())
                                val executed: ToolOutcome =
                                    try {
                                        tool.execute(args, context)
                                    } catch (t: Throwable) {
                                        ToolOutcome(outputForModel = "Tool failed: ${t.message}", isError = true)
                                    }
                                executed.outputForModel
                            }
                        }
                    messages += LlmMessage(role = LlmRole.TOOL, content = result, toolCallId = call.id)
                }
            }
            return lastText
        }

        private fun scan(root: File): List<SubagentSpec> {
            if (!root.isDirectory) {
                return emptyList()
            }
            return root
                .listFiles { file -> file.isFile && file.name.endsWith(".md") }
                .orEmpty()
                .mapNotNull { file -> parse(file) }
        }

        private fun parse(file: File): SubagentSpec? {
            val content = file.readText(Charsets.UTF_8)
            if (!content.startsWith("---")) {
                return null
            }
            val end = content.indexOf("---", 3)
            if (end < 0) {
                return null
            }
            val frontMatter = content.substring(3, end)
            var name: String? = null
            var description: String? = null
            var tools: Set<String>? = null
            var maxTurns = SubagentSpec.DEFAULT_MAX_TURNS
            frontMatter.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("name:") -> name = trimmed.removePrefix("name:").trim()
                    trimmed.startsWith("description:") -> description = trimmed.removePrefix("description:").trim()
                    trimmed.startsWith("tools:") ->
                        tools =
                            trimmed.removePrefix("tools:")
                                .trim()
                                .trim('[', ']')
                                .split(',')
                                .map { it.trim().trim('"') }
                                .filter { it.isNotEmpty() }
                                .toSet()

                    trimmed.startsWith("maxTurns:") -> maxTurns = trimmed.removePrefix("maxTurns:").trim().toIntOrNull() ?: maxTurns
                }
            }
            name ?: return null
            return SubagentSpec(
                name = name.orEmpty(),
                description = description.orEmpty(),
                allowedTools = tools,
                maxTurns = maxTurns,
            )
        }
    }
