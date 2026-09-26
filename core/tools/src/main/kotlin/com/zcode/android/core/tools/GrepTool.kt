package com.zcode.android.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.regex.PatternSyntaxException

private const val GREP_MATCH_LIMIT = 100
private const val GREP_FILE_LIMIT = 2_000
private const val BINARY_SAMPLE_BYTES = 8_000
private const val MAX_INSPECTED_FILE_BYTES = 1_000_000L

class GrepTool : Tool {
    override val name = "Grep"
    override val description =
        "Searches file contents in the workspace with a regular expression and returns matches as path:line: text."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "pattern",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Regular expression to search for")
                        },
                    )
                    put(
                        "path",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Optional file or directory to search, relative to the workspace")
                        },
                    )
                },
            )
            put("required", requiredFields("pattern"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val pattern = input.stringField("pattern")
        if (pattern.isNullOrEmpty()) {
            return toolError("pattern is required")
        }
        val regex =
            try {
                Regex(pattern)
            } catch (_: PatternSyntaxException) {
                return toolError("invalid regular expression: $pattern")
            }
        val rawScope = input.stringField("path")
        val scope: File =
            when {
                rawScope.isNullOrEmpty() -> {
                    context.workspaceRoot
                }

                else -> {
                    val resolved = resolveWorkspacePath(context.workspaceRoot, rawScope)
                    if (resolved == null || !resolved.exists()) {
                        return toolError("path is outside the workspace or missing: $rawScope")
                    }
                    resolved
                }
            }

        val matches = mutableListOf<String>()
        val candidates =
            sequence {
                if (scope.isFile) {
                    yield(scope)
                } else {
                    yieldAll(scope.walkTopDown().filter { it.isFile }.take(GREP_FILE_LIMIT))
                }
            }
        for (file in candidates) {
            if (matches.size >= GREP_MATCH_LIMIT) {
                break
            }
            if (file.length() > MAX_INSPECTED_FILE_BYTES || isBinary(file)) {
                continue
            }
            val relative = file.relativeTo(context.workspaceRoot).invariantSeparatorsPath
            var lineNumber = 0
            file.useLines { lines ->
                for (line in lines) {
                    lineNumber++
                    if (matches.size >= GREP_MATCH_LIMIT) {
                        return@useLines
                    }
                    if (regex.containsMatchIn(line)) {
                        matches.add("$relative:$lineNumber: ${line.take(400)}")
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return ToolOutcome(outputForModel = "No matches for $pattern")
        }
        val suffix =
            if (matches.size >= GREP_MATCH_LIMIT) {
                "\n[additional matches omitted]"
            } else {
                ""
            }
        return ToolOutcome(outputForModel = matches.joinToString(separator = "\n") + suffix)
    }

    private fun isBinary(file: File): Boolean {
        val bytes =
            file.inputStream().use { stream ->
                val buffer = ByteArray(BINARY_SAMPLE_BYTES)
                val read = stream.read(buffer)
                buffer.copyOf(if (read > 0) read else 0)
            }
        return bytes.contains(0.toByte())
    }
}
