package com.zcode.android.core.mcp

import android.content.Context
import com.zcode.android.core.engine.ToolSpec
import com.zcode.android.core.tools.ExternalToolProvider
import com.zcode.android.core.tools.Tool
import com.zcode.android.core.tools.ToolContext
import com.zcode.android.core.tools.ToolOutcome
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Adapts one MCP tool to the agent tool interface. Arguments arrive as a JSON
// object from the model and are forwarded to the server unchanged.
class McpToolAdapter(
    private val mcpTool: McpTool,
    private val registry: McpRegistry,
) : Tool {
    override val name: String = "mcp__" + mcpTool.serverName + "__" + mcpTool.name
    override val description: String = mcpTool.description ?: "MCP tool " + mcpTool.name
    override val parameters: JsonObject =
        runCatching { Json.parseToJsonElement(mcpTool.inputSchemaJson) }.getOrNull() as? JsonObject
            ?: buildJsonObject { put("type", "object") }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val result = registry.call(mcpTool.serverName, mcpTool.name, input.toString())
        return if (result.isError) {
            ToolOutcome(outputForModel = result.text, isError = true)
        } else {
            ToolOutcome(outputForModel = result.text)
        }
    }
}

// Owns the configured MCP servers: loads user and workspace configs, connects,
// merges tools with the ZCode precedence (workspace over user, user servers win
// among themselves by first definition), and executes calls.
@Singleton
class McpRegistry
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ExternalToolProvider {
        private val lock = Any()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val httpClient = OkHttpClient()

        private val clients = mutableMapOf<String, McpTransport>()
        private val toolCache = mutableMapOf<String, List<McpTool>>()
        private val errors = mutableMapOf<String, String>()

        val statuses: List<McpServerStatus>
            get() =
                synchronized(lock) {
                    configs.map { (name, config) ->
                        val connected = clients.containsKey(name)
                        McpServerStatus(
                            name = name,
                            transport = config.transport,
                            connected = connected,
                            toolCount = toolCache[name]?.size ?: 0,
                            error = errors[name],
                        )
                    }
                }

        private var configs: Map<String, McpServerConfig> = emptyMap()

        // Reads user scope plus workspace scope configuration. Workspace servers
        // take precedence over user servers with the same name.
        @Synchronized
        fun loadConfig(workspaceRoot: File?) {
            val userFile = File(context.filesDir, "config/cli/config.json")
            val workspaceFile = workspaceRoot?.let { File(it, ".zcode/config.json") }
            val merged = LinkedHashMap<String, McpServerConfig>()
            merged.putAll(parseServers(userFile))
            if (workspaceFile != null) {
                merged.putAll(parseServers(workspaceFile))
            }
            configs = merged
        }

        // Connects every enabled server off the main thread and refreshes tools.
        fun connectAll() {
            scope.launch {
                synchronized(lock) {
                    configs.values
                        .filter { it.enabled && !clients.containsKey(it.name) }
                        .forEach { config ->
                            launch { connect(config) }
                        }
                }
            }
        }

        @Synchronized
        private suspend fun connect(config: McpServerConfig) {
            try {
                val transport =
                    when (config.transport) {
                        "stdio" -> {
                            StdioMcpTransport(
                                command = listOfNotNull(config.command, *config.args.toTypedArray()).joinToString(separator = " "),
                                workingDirectory = null,
                            )
                        }

                        "http" -> {
                            HttpMcpTransport(url = config.url.orEmpty(), headers = config.headers, httpClient = httpClient)
                        }

                        "sse" -> {
                            SseMcpTransport(url = config.url.orEmpty(), headers = config.headers, httpClient = httpClient)
                        }

                        else -> {
                            throw IOException("unsupported transport: " + config.transport)
                        }
                    }
                transport.connect()
                transport.request("notifications/initialized", null)
                clients[config.name] = transport
                errors.remove(config.name)
                refreshTools(config.name, transport)
            } catch (t: Throwable) {
                errors[config.name] = t.message ?: "connection failed"
            }
        }

        private suspend fun refreshTools(
            name: String,
            transport: McpTransport,
        ) {
            val outcome = transport.request("tools/list", null)
            val result =
                outcome.result ?: run {
                    errors[name] = outcome.error ?: "tools/list failed"
                    return
                }
            val tools =
                (result["tools"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { element ->
                        val tool = element as? JsonObject ?: return@mapNotNull null
                        val toolName = (tool["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        McpTool(
                            serverName = name,
                            name = toolName,
                            description = (tool["description"] as? JsonPrimitive)?.contentOrNull,
                            inputSchemaJson = (tool["inputSchema"] as? JsonObject)?.toString() ?: "{}",
                        )
                    }.orEmpty()
            toolCache[name] = tools
        }

        fun tools(): List<McpTool> = synchronized(toolCache) { toolCache.values.flatten() }

        override fun specs(): List<ToolSpec> =
            tools().map { tool ->
                ToolSpec(
                    name = "mcp__" + tool.serverName + "__" + tool.name,
                    description = tool.description ?: "MCP tool " + tool.name,
                    parameters =
                        runCatching { Json.parseToJsonElement(tool.inputSchemaJson) }.getOrNull() as? JsonObject
                            ?: buildJsonObject { put("type", "object") },
                )
            }

        fun adapters(): List<Tool> = tools().map { McpToolAdapter(it, this) }

        override fun byName(name: String): Tool? = adapters().firstOrNull { it.name == name }

        suspend fun call(
            serverName: String,
            toolName: String,
            argumentsJson: String,
        ): McpCallResult {
            val transport = clients[serverName] ?: return McpCallResult(text = "MCP server $serverName is not connected", isError = true)
            val arguments =
                runCatching { Json.parseToJsonElement(argumentsJson) }.getOrNull() as? JsonObject
                    ?: JsonObject(emptyMap())
            val params =
                buildJsonObject {
                    put("name", toolName)
                    put("arguments", arguments)
                }
            val outcome = transport.request("tools/call", params)
            val result = outcome.result
            if (result == null) {
                return McpCallResult(text = outcome.error ?: "MCP call failed", isError = true)
            }
            val content = result["content"] as? kotlinx.serialization.json.JsonArray
            val text =
                content
                    ?.mapNotNull { entry ->
                        val block = entry as? JsonObject ?: return@mapNotNull null
                        (block["text"] as? JsonPrimitive)?.contentOrNull
                    }?.joinToString(separator = "\n")
                    .orEmpty()
            val isError = (result["isError"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            return McpCallResult(text = text.ifEmpty { "(empty result)" }, isError = isError)
        }

        private fun parseServers(file: File): Map<String, McpServerConfig> {
            if (!file.isFile) {
                return emptyMap()
            }
            val root =
                runCatching { Json.parseToJsonElement(file.readText(Charsets.UTF_8)) }.getOrNull() as? JsonObject
                    ?: return emptyMap()
            val section = (root["mcp.servers"] as? JsonObject) ?: (root["mcpServers"] as? JsonObject) ?: (root["servers"] as? JsonObject)
            section ?: return emptyMap()
            return section.entries
                .mapNotNull { (name, value) ->
                    val server = value as? JsonObject ?: return@mapNotNull null
                    McpServerConfig(
                        name = name,
                        transport =
                            (server["transport"] as? JsonPrimitive)?.contentOrNull
                                ?: if (server["command"] != null) "stdio" else "http",
                        command = (server["command"] as? JsonPrimitive)?.contentOrNull,
                        args =
                            (server["args"] as? kotlinx.serialization.json.JsonArray)
                                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                                .orEmpty(),
                        url = (server["url"] as? JsonPrimitive)?.contentOrNull,
                        headers =
                            (server["headers"] as? JsonObject)
                                ?.entries
                                ?.associate { (key, header) ->
                                    key to ((header as? JsonPrimitive)?.contentOrNull ?: "")
                                }.orEmpty(),
                        enabled = (server["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true,
                    )
                }.associateBy { it.name }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object McpModule {
    @Provides
    @Singleton
    @IntoSet
    fun mcpProvider(registry: McpRegistry): ExternalToolProvider = registry
}
