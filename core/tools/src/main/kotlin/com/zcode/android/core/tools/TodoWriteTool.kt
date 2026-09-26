package com.zcode.android.core.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class TodoItem(
    val content: String,
    val status: String,
)

class TodoWriteTool : Tool {
    override val name = "TodoWrite"
    override val description =
        "Replaces the current todo list for the session. Status must be pending, in_progress, or completed."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "todos",
                        buildJsonObject {
                            put("type", "array")
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("content", buildJsonObject { put("type", "string") })
                                            put(
                                                "status",
                                                buildJsonObject {
                                                    put("type", "string")
                                                    put(
                                                        "enum",
                                                        kotlinx.serialization.json.buildJsonArray {
                                                            add(kotlinx.serialization.json.JsonPrimitive("pending"))
                                                            add(kotlinx.serialization.json.JsonPrimitive("in_progress"))
                                                            add(kotlinx.serialization.json.JsonPrimitive("completed"))
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                    put("required", requiredFields("content", "status"))
                                },
                            )
                        },
                    )
                },
            )
            put("required", requiredFields("todos"))
        }

    // Parses the todos input; used by the agent to update the UI widget.
    fun parseTodos(input: JsonObject): List<TodoItem> =
        (input["todos"] as? JsonArray)
            ?.mapNotNull { entry ->
                val item = entry as? JsonObject ?: return@mapNotNull null
                val content = (item["content"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val status = (item["status"] as? JsonPrimitive)?.contentOrNull ?: "pending"
                TodoItem(content = content, status = status)
            }.orEmpty()

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val todos = parseTodos(input)
        if (todos.isEmpty()) {
            return ToolOutcome(outputForModel = "Todo list cleared")
        }
        val body =
            todos.joinToString(separator = "\n") { item ->
                val marker =
                    when (item.status) {
                        "completed" -> "[x]"
                        "in_progress" -> "[~]"
                        else -> "[ ]"
                    }
                "$marker ${item.content}"
            }
        return ToolOutcome(outputForModel = "Todo list updated:\n$body")
    }
}
