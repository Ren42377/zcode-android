package com.zcode.android.core.tools

import com.zcode.android.core.terminal.ExecService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import java.io.File

// Outcome of a tool execution. outputForModel is what the model sees as the tool
// result; isError marks failures so the model can react instead of crashing.
data class ToolOutcome(
    val outputForModel: String,
    val isError: Boolean = false,
)

// Per-turn environment handed to every tool execution.
class ToolContext(
    val workspaceRoot: File,
    val exec: ExecService,
    val shellEnvironment: Array<String>,
    val httpClient: OkHttpClient,
    // Web search uses the provider endpoint; null disables the tool.
    val webSearchBaseUrl: String?,
    val webSearchApiKey: String?,
    // Active LLM settings so orchestration tools can run their own requests.
    val endpoint: com.zcode.android.core.engine.LlmEndpoint,
    val model: String,
    val thinkingEffort: com.zcode.android.core.engine.ThinkingEffort?,
)

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject

    suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome
}

// Resolves a user supplied path against the workspace root and rejects escapes.
internal fun resolveWorkspacePath(
    root: File,
    raw: String,
): File? {
    val supplied = File(raw)
    val resolved = if (supplied.isAbsolute) supplied else File(root, raw)
    val canonicalRoot = root.canonicalFile
    val canonical = resolved.canonicalFile
    val inside =
        canonical.path == canonicalRoot.path || canonical.path.startsWith(canonicalRoot.path + File.separator)
    return if (inside) canonical else null
}

internal fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.intField(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

internal fun toolError(message: String): ToolOutcome = ToolOutcome(outputForModel = message, isError = true)

internal fun requiredFields(vararg names: String): JsonArray =
    buildJsonArray {
        names.forEach { add(JsonPrimitive(it)) }
    }
