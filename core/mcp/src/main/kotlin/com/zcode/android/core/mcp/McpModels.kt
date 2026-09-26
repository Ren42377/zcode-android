package com.zcode.android.core.mcp

// Server entry parsed from the mcp.servers section of a config file. The shape
// matches the ZCode configuration so existing server definitions import as is.
data class McpServerConfig(
    val name: String,
    val transport: String,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

data class McpTool(
    val serverName: String,
    val name: String,
    val description: String?,
    val inputSchemaJson: String,
)

data class McpCallResult(
    val text: String,
    val isError: Boolean,
)

// Status of one configured server, surfaced in the UI.
data class McpServerStatus(
    val name: String,
    val transport: String,
    val connected: Boolean,
    val toolCount: Int,
    val error: String? = null,
)
