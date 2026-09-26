package com.zcode.android.core.tools

import com.zcode.android.core.engine.ToolSpec
import javax.inject.Inject

// Registry of the v1 tool set. Tool names and semantics follow ZCode so prompts
// and behavior stay compatible with the original agent.
class ToolRegistry
    @Inject
    constructor() {
        private val tools: List<Tool> =
            listOf(
                ReadTool(),
                WriteTool(),
                EditTool(),
                GlobTool(),
                GrepTool(),
                BashTool(),
                TodoWriteTool(),
                WebFetchTool(),
                WebSearchTool(),
            )

        private val byName: Map<String, Tool> = tools.associateBy { it.name }

        fun specs(): List<ToolSpec> =
            tools.map { tool ->
                ToolSpec(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters,
                )
            }

        fun byName(name: String): Tool? = byName[name]
    }
