package com.zcode.android.core.tools

import com.zcode.android.core.engine.ToolSpec
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject

// Contract for tool sources beyond the built-in registry, for example MCP
// servers. Implementations are contributed through Dagger multibindings.
interface ExternalToolProvider {
    fun specs(): List<ToolSpec>

    fun byName(name: String): Tool?
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalToolModule {
    @Multibinds
    abstract fun externalToolProviders(): Set<ExternalToolProvider>
}

// Registry of the v1 tool set plus any external providers. Tool names and
// semantics follow ZCode so prompts and behavior stay compatible.
class ToolRegistry
    @Inject
    constructor(
        private val externalProviders: Set<@JvmSuppressWildcards ExternalToolProvider>,
    ) {
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
            } + externalProviders.flatMap { provider -> provider.specs() }

        fun byName(name: String): Tool? = byName[name] ?: externalProviders.firstNotNullOfOrNull { provider -> provider.byName(name) }
    }
