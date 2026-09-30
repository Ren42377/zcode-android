package com.zcode.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.agent.HookRunner
import com.zcode.android.core.agent.PermissionMode
import com.zcode.android.core.agent.PluginLoader
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.ProviderPresets
import com.zcode.android.core.mcp.McpRegistry
import com.zcode.android.core.storage.ApiKeyVault
import com.zcode.android.core.storage.MessageDao
import com.zcode.android.core.storage.SessionDao
import com.zcode.android.core.storage.SessionEntity
import com.zcode.android.core.storage.UserPreferences
import com.zcode.android.core.terminal.TermuxBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateStatus(
    val checking: Boolean = false,
    val latestTag: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val preferences: UserPreferences,
        private val sessionDao: SessionDao,
        private val messageDao: MessageDao,
        private val apiKeyVault: ApiKeyVault,
        private val mcpRegistry: McpRegistry,
        private val hookRunner: HookRunner,
        private val updateChecker: UpdateChecker,
        private val pluginLoader: PluginLoader,
    ) : ViewModel() {
        val providerId: StateFlow<String?> =
            preferences.providerId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val model: StateFlow<String?> =
            preferences.model.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val thinkingEffort: StateFlow<String?> =
            preferences.thinkingEffort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val mode: StateFlow<String> =
            preferences.permissionMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "build")

        val termuxMode: StateFlow<Boolean> =
            preferences.termuxMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val alwaysAllowed: StateFlow<Set<String>> =
            preferences.alwaysAllowedTools.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        val sessions: StateFlow<List<SessionEntity>> =
            sessionDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _mcpStatuses = MutableStateFlow<List<String>>(emptyList())
        val mcpStatuses: StateFlow<List<String>> = _mcpStatuses.asStateFlow()

        private val _update = MutableStateFlow(UpdateStatus())
        val update: StateFlow<UpdateStatus> = _update.asStateFlow()

        private val _plugins = MutableStateFlow<List<String>>(emptyList())
        val plugins: StateFlow<List<String>> = _plugins.asStateFlow()

        val termuxInstalled: Boolean = TermuxBridge(appContext).isInstalled()

        val termuxPermissionGranted: Boolean = TermuxBridge(appContext).isPermissionGranted()

        init {
            refreshMcp()
            refreshPlugins()
        }

        fun refreshPlugins() {
            _plugins.value =
                pluginLoader.installed().map { manifest ->
                    "${manifest.name} ${manifest.version}: ${manifest.description}"
                }
        }

        fun installPlugin(
            repo: String,
            ref: String,
        ) {
            viewModelScope.launch {
                try {
                    val manifest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pluginLoader.installFromGitHub(repo, ref) }
                    _update.value = UpdateStatus(message = "Installed ${manifest.name} ${manifest.version}")
                    refreshPlugins()
                } catch (e: java.io.IOException) {
                    _update.value = UpdateStatus(message = "Install failed: ${e.message}")
                }
            }
        }

        fun uninstallPlugin(name: String) {
            viewModelScope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pluginLoader.uninstall(name) }
                refreshPlugins()
            }
        }

        fun setProvider(id: String) {
            viewModelScope.launch { preferences.setProviderId(id) }
        }

        fun setModel(id: String) {
            viewModelScope.launch { preferences.setModel(id) }
        }

        fun setEffort(value: String?) {
            viewModelScope.launch { preferences.setThinkingEffort(value) }
        }

        fun setMode(mode: PermissionMode) {
            viewModelScope.launch { preferences.setPermissionMode(mode.name.lowercase()) }
        }

        fun setTermuxMode(enabled: Boolean) {
            viewModelScope.launch { preferences.setTermuxMode(enabled) }
        }

        fun resetAlwaysAllowed() {
            viewModelScope.launch {
                alwaysAllowed.value.forEach { tool -> preferences.removeAlwaysAllowedTool(tool) }
            }
        }

        fun refreshMcp() {
            viewModelScope.launch {
                mcpRegistry.loadConfig(null)
                mcpRegistry.connectAll()
                _mcpStatuses.value =
                    mcpRegistry.statuses.map { status ->
                        val state = if (status.connected) "connected" else "offline"
                        val error = status.error?.let { " ($it)" }.orEmpty()
                        "${status.name}: ${status.transport} $state, ${status.toolCount} tools$error"
                    }
            }
        }

        fun hookSummary(): String {
            val config = hookRunner.loadConfig(null)
            val events = config.definitions.map { it.event }.distinct()
            return if (events.isEmpty()) {
                "No hooks configured"
            } else {
                "Enabled: ${config.enabled}, timeout ${config.timeoutMs}ms, events: ${events.joinToString()}"
            }
        }

        fun renameSession(
            id: String,
            title: String,
        ) {
            viewModelScope.launch { sessionDao.rename(id, title) }
        }

        fun deleteSession(id: String) {
            viewModelScope.launch { sessionDao.delete(id) }
        }

        fun forkSession(id: String) {
            viewModelScope.launch {
                val source = sessionDao.get(id) ?: return@launch
                val newId =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                val now = System.currentTimeMillis()
                sessionDao.upsert(
                    SessionEntity(
                        id = newId,
                        title = source.title + " (fork)",
                        workspacePath = source.workspacePath,
                        providerId = source.providerId,
                        model = source.model,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                messageDao.list(id).forEach { message ->
                    messageDao.insert(
                        message.copy(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            sessionId = newId,
                        ),
                    )
                }
            }
        }

        fun exportSession(id: String) {
            viewModelScope.launch {
                val session = sessionDao.get(id) ?: return@launch
                val messages = messageDao.list(id)
                val markdown =
                    buildString {
                        append("# ").append(session.title).append("\n\n")
                        messages.forEach { message ->
                            append("## ").append(message.role).append("\n\n")
                            message.content?.let { content -> append(content).append("\n\n") }
                        }
                    }
                val file = java.io.File(appContext.cacheDir, "session-${id.take(8)}.md")
                file.writeText(markdown, Charsets.UTF_8)
                _update.value = _update.value.copy(message = "Exported to ${file.path}")
            }
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                _update.value = UpdateStatus(checking = true)
                val result = updateChecker.latestRelease()
                _update.value =
                    UpdateStatus(
                        checking = false,
                        latestTag = result.tag,
                        message = result.message,
                    )
            }
        }

        fun clearMessage() {
            _update.value = _update.value.copy(message = null)
        }

        fun providerLabels(): List<Pair<String, String>> = ProviderPresets.all.map { it.id to it.label }

        fun modelLabels(): List<Pair<String, String>> = BuiltinModels.all.map { it.id to it.label }

        fun clearApiKey() {
            viewModelScope.launch {
                providerId.value?.let { apiKeyVault.clear(it) }
            }
        }
    }
