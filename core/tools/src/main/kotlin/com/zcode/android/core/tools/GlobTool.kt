package com.zcode.android.core.tools

import java.io.File
import java.util.regex.PatternSyntaxException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Translates a glob pattern (with **, * and ?) into a path regex.
internal fun globToRegex(pattern: String): Regex {
    val normalized = pattern.replace('\\', '/')
    val builder = StringBuilder()
    var i = 0
    while (i < normalized.length) {
        when {
            normalized.startsWith("**/", i) -> {
                builder.append("(?:[^/]*/)*")
                i += 3
            }

            normalized.startsWith("**", i) -> {
                builder.append(".*")
                i += 2
            }

            normalized[i] == '*' -> {
                builder.append("[^/]*")
                i++
            }

            normalized[i] == '?' -> {
                builder.append("[^/]")
                i++
            }

            else -> {
                builder.append(Regex.escape(normalized[i].toString()))
                i++
            }
        }
    }
    return Regex(builder.toString())
}

private const val GLOB_WALK_LIMIT = 5_000
private const val GLOB_RESULT_LIMIT = 200

class GlobTool : Tool {
    override val name = "Glob"
    override val description =
        "Finds files in the workspace by glob pattern (for example **/*.kt) and returns their workspace relative paths."
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
                            put("description", "Glob pattern relative to the workspace root")
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
                globToRegex(pattern)
            } catch (_: PatternSyntaxException) {
                return toolError("invalid glob pattern: $pattern")
            }
        val results = mutableListOf<String>()
        val files = context.workspaceRoot.walkTopDown().take(GLOB_WALK_LIMIT)
        for (file in files) {
            if (!file.isFile) {
                continue
            }
            val relative = file.relativeTo(context.workspaceRoot).invariantSeparatorsPath
            if (regex.matches(relative)) {
                results.add(relative)
            }
        }
        if (results.isEmpty()) {
            return ToolOutcome(outputForModel = "No files match $pattern")
        }
        val sorted = results.sorted()
        val shown = sorted.take(GLOB_RESULT_LIMIT)
        val suffix =
            if (sorted.size > GLOB_RESULT_LIMIT) {
                "\n[${sorted.size - GLOB_RESULT_LIMIT} more matches omitted]"
            } else {
                ""
            }
        return ToolOutcome(outputForModel = shown.joinToString(separator = "\n") + suffix)
    }
}
