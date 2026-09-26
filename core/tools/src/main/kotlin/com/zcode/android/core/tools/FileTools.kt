package com.zcode.android.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

private const val READ_LINE_LIMIT = 2_000

class ReadTool : Tool {
    override val name = "Read"
    override val description =
        "Reads a text file from the workspace and returns it with line numbers. Use offset and limit to read large files in slices."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "path",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "File path relative to the workspace root")
                        },
                    )
                    put(
                        "offset",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "1-based line number to start from")
                        },
                    )
                    put(
                        "limit",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Maximum number of lines to return")
                        },
                    )
                },
            )
            put("required", requiredFields("path"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val raw = input.stringField("path")
        if (raw.isNullOrEmpty()) {
            return toolError("path is required")
        }
        val file = resolveWorkspacePath(context.workspaceRoot, raw)
        if (file == null) {
            return toolError("path is outside the workspace: $raw")
        }
        if (!file.exists() || !file.isFile) {
            return toolError("file not found: $raw")
        }
        val offset = (input.intField("offset") ?: 1).coerceAtLeast(1)
        val limit = (input.intField("limit") ?: READ_LINE_LIMIT).coerceIn(1, READ_LINE_LIMIT)
        val lines = file.readLines()
        val end = minOf(offset - 1 + limit, lines.size)
        if (offset - 1 >= lines.size) {
            return ToolOutcome(outputForModel = "Line $offset is beyond the end of the file (${lines.size} lines)")
        }
        val body =
            lines
                .subList(offset - 1, end)
                .mapIndexed { index, line -> "%6d\t%s".format(offset + index, line) }
                .joinToString(separator = "\n")
        val suffix =
            if (end < lines.size) {
                "\n[lines ${end + 1}..${lines.size} omitted; read again with a higher offset]"
            } else {
                ""
            }
        return ToolOutcome(outputForModel = body + suffix)
    }
}

class WriteTool : Tool {
    override val name = "Write"
    override val description =
        "Creates a new file in the workspace or fully overwrites an existing one with the given content."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "path",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "File path relative to the workspace root")
                        },
                    )
                    put(
                        "content",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Full file content to write")
                        },
                    )
                },
            )
            put("required", requiredFields("path", "content"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val raw = input.stringField("path")
        val content = input.stringField("content") ?: ""
        if (raw.isNullOrEmpty()) {
            return toolError("path is required")
        }
        val file = resolveWorkspacePath(context.workspaceRoot, raw)
        if (file == null) {
            return toolError("path is outside the workspace: $raw")
        }
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return ToolOutcome(outputForModel = "Wrote ${content.toByteArray(Charsets.UTF_8).size} bytes to $raw")
    }
}

class EditTool : Tool {
    override val name = "Edit"
    override val description =
        "Replaces one exact occurrence of old_string with new_string in a workspace file. The old_string must appear exactly once."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "path",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "File path relative to the workspace root")
                        },
                    )
                    put(
                        "old_string",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Exact text to replace; must be unique in the file")
                        },
                    )
                    put(
                        "new_string",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Replacement text")
                        },
                    )
                },
            )
            put("required", requiredFields("path", "old_string", "new_string"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val raw = input.stringField("path")
        val oldString = input.stringField("old_string")
        val newString = input.stringField("new_string")
        if (raw.isNullOrEmpty() || oldString.isNullOrEmpty() || newString == null) {
            return toolError("path, old_string and new_string are required")
        }
        val file = resolveWorkspacePath(context.workspaceRoot, raw)
        if (file == null) {
            return toolError("path is outside the workspace: $raw")
        }
        if (!file.exists() || !file.isFile) {
            return toolError("file not found: $raw")
        }
        val content = file.readText(Charsets.UTF_8)
        val first = content.indexOf(oldString)
        when {
            first < 0 -> {
                return toolError("old_string was not found in $raw")
            }

            content.indexOf(oldString, first + 1) >= 0 -> {
                return toolError("old_string appears more than once in $raw; provide a longer unique string")
            }

            else -> {}
        }
        file.writeText(content.replaceRange(first, first + oldString.length, newString), Charsets.UTF_8)
        return ToolOutcome(outputForModel = "Edited $raw")
    }
}

// Kept for tests that need to build File references without workspace context.
internal fun workspaceFile(
    root: File,
    relative: String,
): File = File(root, relative)
