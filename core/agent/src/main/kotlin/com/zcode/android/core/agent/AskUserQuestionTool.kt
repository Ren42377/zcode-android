package com.zcode.android.core.agent

import com.zcode.android.core.tools.Tool
import com.zcode.android.core.tools.ToolContext
import com.zcode.android.core.tools.ToolOutcome
import com.zcode.android.core.tools.ToolUiEvent
import com.zcode.android.core.tools.requiredFields
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject

// Asks the user a question and waits for the answer. The question renders as a
// card in the chat with the supplied options, matching the ZCode behavior of
// pausing the turn until the user decides.
class AskUserQuestionTool
    @Inject
    constructor(
        private val questions: QuestionCoordinator,
    ) : Tool {
        override val name = "AskUserQuestion"
        override val description =
            "Asks the user a question with a short set of options and waits for the answer. " +
                "Use this when a decision is genuinely the user's to make."
        override val parameters =
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "question",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "The question to ask the user")
                            },
                        )
                        put(
                            "options",
                            buildJsonObject {
                                put("type", "array")
                                put("description", "Two to four answer options")
                                put(
                                    "items",
                                    buildJsonObject {
                                        put("type", "object")
                                        put(
                                            "properties",
                                            buildJsonObject {
                                                put("label", buildJsonObject { put("type", "string") })
                                                put("description", buildJsonObject { put("type", "string") })
                                            },
                                        )
                                        put("required", requiredFields("label"))
                                    },
                                )
                            },
                        )
                    },
                )
                put("required", requiredFields("question", "options"))
            }

        override suspend fun execute(
            input: JsonObject,
            context: ToolContext,
        ): ToolOutcome {
            val question = (input["question"] as? JsonPrimitive)?.contentOrNull
            if (question.isNullOrEmpty()) {
                return ToolOutcome(outputForModel = "question is required", isError = true)
            }
            val options = parseOptions(input)
            if (options.isEmpty()) {
                return ToolOutcome(outputForModel = "at least one option is required", isError = true)
            }
            val answer =
                questions.await(question, options) { id ->
                    context.onUiEvent(ToolUiEvent.Question(questionId = id, question = question, options = options))
                }
            return ToolOutcome(outputForModel = "The user answered: $answer")
        }

        private fun parseOptions(input: JsonObject): List<String> =
            (input["options"] as? JsonArray)
                ?.mapNotNull { entry ->
                    when (entry) {
                        is JsonObject -> (entry["label"] as? JsonPrimitive)?.contentOrNull
                        is JsonPrimitive -> entry.contentOrNull
                        else -> null
                    }
                }.orEmpty()
    }

@Module
@InstallIn(SingletonComponent::class)
object QuestionToolModule {
    @Provides
    @IntoSet
    fun askUserQuestionTool(tool: AskUserQuestionTool): Tool = tool
}
