package com.zcode.android.core.agent

import com.zcode.android.core.tools.Tool
import com.zcode.android.core.tools.ToolContext
import com.zcode.android.core.tools.ToolOutcome
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

// Launches a subagent for a focused task so the main context stays clean.
// Subagents run with a restricted read oriented tool set and never spawn
// further subagents.
class TaskTool
    @Inject
    constructor(
        private val runner: SubagentRunner,
    ) : Tool {
        override val name = "Task"
        override val description =
            "Launches a subagent for a focused task. subagent_type is general-purpose, Explore, or a custom " +
                "agent name. The prompt must be a complete, self contained task description."
        override val parameters =
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "subagent_type",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "general-purpose, Explore, or a custom agent name")
                            },
                        )
                        put(
                            "description",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Short task summary shown in the transcript")
                            },
                        )
                        put(
                            "prompt",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Complete task description for the subagent")
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("description"))
                        add(JsonPrimitive("prompt"))
                    },
                )
            }

        override suspend fun execute(
            input: JsonObject,
            context: ToolContext,
        ): ToolOutcome {
            val prompt = (input["prompt"] as? JsonPrimitive)?.contentOrNull
            if (prompt.isNullOrEmpty()) {
                return ToolOutcome(outputForModel = "prompt is required", isError = true)
            }
            val typeName = (input["subagent_type"] as? JsonPrimitive)?.contentOrNull ?: "general-purpose"
            val spec =
                when (typeName) {
                    "Explore" -> {
                        SubagentSpec.EXPLORE
                    }

                    "general-purpose" -> {
                        SubagentSpec.GENERAL_PURPOSE
                    }

                    else -> {
                        runner.loadCustomAgents(context.workspaceRoot)[typeName]
                            ?: return ToolOutcome(outputForModel = "Unknown subagent: $typeName", isError = true)
                    }
                }
            val answer = runner.run(spec, prompt, context.endpoint, context.model, context.thinkingEffort, context)
            return ToolOutcome(outputForModel = answer)
        }
    }

// Contributes the Task tool to the registry through Dagger multibindings.
@Module
@InstallIn(dagger.hilt.components.SingletonComponent::class)
object SubagentToolModule {
    @Provides
    @IntoSet
    fun taskTool(tool: TaskTool): Tool = tool
}
