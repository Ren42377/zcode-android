package com.zcode.android.feature.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.agent.AgentEvent
import com.zcode.android.core.agent.AgentLoop
import com.zcode.android.core.agent.ApprovalAnswer
import com.zcode.android.core.agent.ApprovalCoordinator
import com.zcode.android.core.agent.HookConfig
import com.zcode.android.core.agent.HookRunner
import com.zcode.android.core.agent.MemoryStore
import com.zcode.android.core.agent.PermissionMode
import com.zcode.android.core.agent.SkillLoader
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.LlmClient
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.LlmEvent
import com.zcode.android.core.engine.LlmMessage
import com.zcode.android.core.engine.LlmProtocol
import com.zcode.android.core.engine.LlmRequest
import com.zcode.android.core.engine.LlmRole
import com.zcode.android.core.engine.LlmUsage
import com.zcode.android.core.engine.ProviderPresets
import com.zcode.android.core.engine.ThinkingEffort
import com.zcode.android.core.mcp.McpRegistry
import com.zcode.android.core.storage.ApiKeyVault
import com.zcode.android.core.storage.MessageDao
import com.zcode.android.core.storage.MessageEntity
import com.zcode.android.core.storage.SessionDao
import com.zcode.android.core.storage.SessionEntity
import com.zcode.android.core.storage.ToolEventDao
import com.zcode.android.core.storage.ToolEventEntity
import com.zcode.android.core.storage.UserPreferences
import com.zcode.android.core.terminal.ExecService
import com.zcode.android.core.tools.BashTool
import com.zcode.android.core.tools.TodoItem
import com.zcode.android.core.tools.ToolContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class StreamingState(
    val thinking: String,
    val content: String,
)

// A tool call rendered as a live card while it runs. Finished calls are persisted
// in Room and survive resume.
data class RunningTool(
    val callId: String,
    val name: String,
    val summary: String,
)

data class PendingApproval(
    val callId: String,
    val name: String,
    val summary: String,
)

// Chronological transcript rows: persisted messages and executed tool events.
sealed interface ChatRow {
    data class MessageRow(
        val entity: MessageEntity,
    ) : ChatRow

    data class ToolRow(
        val entity: ToolEventEntity,
    ) : ChatRow

    val timestamp: Long
        get() =
            when (this) {
                is MessageRow -> entity.createdAt
                is ToolRow -> entity.createdAt
            }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        @ApplicationContext private val appContext: Context,
        private val sessionDao: SessionDao,
        private val messageDao: MessageDao,
        private val toolEventDao: ToolEventDao,
        private val preferences: UserPreferences,
        private val apiKeyVault: ApiKeyVault,
        private val agentLoop: AgentLoop,
        private val approvals: ApprovalCoordinator,
        private val client: LlmClient,
        private val memoryStore: MemoryStore,
        private val skillLoader: SkillLoader,
        private val hookRunner: HookRunner,
        private val mcpRegistry: McpRegistry,
    ) : ViewModel() {
        private val sessionKey = MutableStateFlow(savedStateHandle.get<String>(SESSION_ARG) ?: NEW_SESSION)

        val rows: StateFlow<List<ChatRow>> =
            combine(
                sessionKey.flatMapLatest { messageDao.observe(it) },
                sessionKey.flatMapLatest { toolEventDao.observe(it) },
            ) { messages, events ->
                (messages.map { ChatRow.MessageRow(it) } + events.map { ChatRow.ToolRow(it) })
                    .sortedBy { it.timestamp }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val model: StateFlow<String?> =
            preferences.model.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val thinkingEffort: StateFlow<String?> =
            preferences.thinkingEffort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val mode: StateFlow<String> =
            preferences.permissionMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MODE_BUILD)

        private val _streaming = MutableStateFlow<StreamingState?>(null)
        val streaming: StateFlow<StreamingState?> = _streaming.asStateFlow()

        private val _runningTools = MutableStateFlow<List<RunningTool>>(emptyList())
        val runningTools: StateFlow<List<RunningTool>> = _runningTools.asStateFlow()

        private val _pendingApproval = MutableStateFlow<PendingApproval?>(null)
        val pendingApproval: StateFlow<PendingApproval?> = _pendingApproval.asStateFlow()

        private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
        val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

        private val _queued = MutableStateFlow<List<String>>(emptyList())
        val queued: StateFlow<List<String>> = _queued.asStateFlow()

        private val _busy = MutableStateFlow(false)
        val busy: StateFlow<Boolean> = _busy.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _suggestions = MutableStateFlow<List<SlashCommand>>(emptyList())
        val suggestions: StateFlow<List<SlashCommand>> = _suggestions.asStateFlow()

        private val _pickerOpen = MutableStateFlow(false)
        val pickerOpen: StateFlow<Boolean> = _pickerOpen.asStateFlow()

        private var turnJob: Job? = null
        private var activeCall: Call? = null

        @Volatile
        private var sessionStarted = false

        @Volatile
        private var stopped = false

        private val exec = ExecService()
        private val httpClient = OkHttpClient()

        init {
            viewModelScope.launch {
                sessionKey.collect {
                    sessionStarted = false
                }
            }
        }

        fun send(prompt: String) {
            val trimmed = prompt.trim()
            if (trimmed.isEmpty()) {
                return
            }
            if (turnJob?.isActive == true) {
                _queued.value = _queued.value + trimmed
                return
            }
            stopped = false
            launchTurn(trimmed)
        }

        fun removeFromQueue(index: Int) {
            _queued.value = _queued.value.filterIndexed { position, _ -> position != index }
        }

        fun stop() {
            stopped = true
            activeCall?.cancel()
            approvals.cancelAll()
        }

        fun approve(answer: ApprovalAnswer) {
            val pending = _pendingApproval.value ?: return
            approvals.answer(pending.callId, answer)
        }

        fun dismissError() {
            _error.value = null
        }

        fun onInputChanged(value: String) {
            _suggestions.value =
                if (value.startsWith("/")) {
                    SlashCommands.matching(value.removePrefix("/").trim())
                } else {
                    emptyList()
                }
        }

        fun clearSuggestions() {
            _suggestions.value = emptyList()
        }

        fun tryHandleSlash(raw: String) {
            val body = raw.removePrefix("/").trim()
            val name = body.substringBefore(' ')
            val args = body.substringAfter(' ', missingDelimiterValue = "").trim()
            when (name) {
                "help" -> {
                    insertLocalMessage(helpText())
                }

                "model" -> {
                    _pickerOpen.value = true
                }

                "mode" -> {
                    cycleMode()
                }

                "effort" -> {
                    cycleEffort()
                }

                "clear" -> {
                    sessionKey.value = NEW_SESSION
                }

                "compact" -> {
                    compact()
                }

                "goal" -> {
                    if (args.isNotEmpty()) {
                        viewModelScope.launch {
                            ensureSession("Goal")
                            insertRoleMessage(ROLE_GOAL, "Session goal: $args")
                        }
                    }
                }

                "init" -> {
                    initAgentsMd()
                }

                "memory" -> {
                    showMemory()
                }
            }
        }

        fun pickerShown() {
            _pickerOpen.value = false
        }

        private fun cycleEffort() {
            viewModelScope.launch {
                val order = listOf<String?>(null, "LOW", "HIGH", "MAX")
                val current = preferences.thinkingEffort.first()
                val next = order[(order.indexOf(current) + 1) % order.size]
                preferences.setThinkingEffort(next)
            }
        }

        private fun compact() {
            if (turnJob?.isActive == true) {
                return
            }
            turnJob =
                viewModelScope.launch {
                    val endpoint = resolveEndpoint()
                    if (endpoint == null) {
                        _error.value = "No provider configured"
                        return@launch
                    }
                    val history = messageDao.list(sessionKey.value)
                    if (history.size < 3) {
                        insertLocalMessage("Nothing to compact yet")
                        return@launch
                    }
                    _busy.value = true
                    try {
                        val transcript =
                            history
                                .joinToString(separator = "\n\n") { message ->
                                    "${message.role}: ${message.content}".take(2_000)
                                }.take(MAX_COMPACT_CHARS)
                        val text = StringBuilder()
                        client.stream(
                            request =
                                LlmRequest(
                                    model = currentModel(),
                                    messages =
                                        listOf(
                                            LlmMessage(role = LlmRole.SYSTEM, content = COMPACTION_PROMPT),
                                            LlmMessage(role = LlmRole.USER, content = transcript),
                                        ),
                                ),
                            endpoint = endpoint,
                            onEvent = { event ->
                                if (event is LlmEvent.TextDelta) {
                                    text.append(event.text)
                                }
                            },
                        )
                        insertRoleMessage(ROLE_SUMMARY, text.toString())
                    } catch (_: IOException) {
                        _error.value = "Compaction failed"
                    } finally {
                        _busy.value = false
                    }
                }
        }

        private fun initAgentsMd() {
            viewModelScope.launch {
                ensureSession("AGENTS.md")
                val file = File(workspace(), "AGENTS.md")
                if (file.isFile) {
                    insertLocalMessage("AGENTS.md already exists in the workspace.")
                } else {
                    file.writeText(AGENTS_TEMPLATE, Charsets.UTF_8)
                    insertLocalMessage("Created AGENTS.md in the workspace.")
                }
            }
        }

        private fun showMemory() {
            viewModelScope.launch {
                ensureSession("Memory")
                val content = memoryStore.read(workspace())
                insertLocalMessage(content ?: "No memory stored for this workspace yet.")
            }
        }

        private suspend fun insertLocalMessage(content: String) {
            ensureSession(
                content
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .ifEmpty { "Note" }
                    .take(TITLE_CHARS),
            )
            insertRoleMessage(ROLE_ASSISTANT, content)
        }

        private fun helpText(): String = SlashCommands.all.joinToString(separator = "\n") { "/${it.name} - ${it.description}" }

        fun selectModel(modelId: String) {
            viewModelScope.launch {
                preferences.setModel(modelId)
            }
        }

        fun selectEffort(effort: String?) {
            viewModelScope.launch {
                preferences.setThinkingEffort(effort)
            }
        }

        fun cycleMode() {
            viewModelScope.launch {
                val current = runCatching { PermissionMode.valueOf(mode.value.uppercase()) }.getOrDefault(PermissionMode.BUILD)
                val next = PermissionMode.entries[(current.ordinal + 1) % PermissionMode.entries.size]
                preferences.setPermissionMode(next.name.lowercase())
            }
        }

        private fun launchTurn(prompt: String) {
            turnJob =
                viewModelScope.launch {
                    if (sessionKey.value == NEW_SESSION) {
                        createSession(prompt)
                    }
                    val endpoint = resolveEndpoint()
                    if (endpoint == null) {
                        _error.value = "No provider configured"
                        return@launch
                    }
                    val hookConfig = hookRunner.loadConfig(workspace())
                    mcpRegistry.loadConfig(workspace())
                    mcpRegistry.connectAll()
                    val toolContext = buildToolContext(endpoint)
                    if (!sessionStarted) {
                        sessionStarted = true
                        hookRunner.run(
                            event = "SessionStart",
                            subject = "session",
                            payload = JsonObject(emptyMap()),
                            config = hookConfig,
                            shellEnvironment = toolContext.shellEnvironment,
                            workingDirectory = toolContext.workspaceRoot.path,
                        )
                    }
                    val submit =
                        hookRunner.run(
                            event = "UserPromptSubmit",
                            subject = "user",
                            payload = JsonObject(emptyMap()),
                            config = hookConfig,
                            shellEnvironment = toolContext.shellEnvironment,
                            workingDirectory = toolContext.workspaceRoot.path,
                        )
                    if (submit.blocked) {
                        _error.value = submit.reason ?: "Prompt blocked by hook"
                        _busy.value = false
                        return@launch
                    }
                    val now = System.currentTimeMillis()
                    messageDao.insert(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionKey.value,
                            role = ROLE_USER,
                            content = prompt,
                            createdAt = now,
                        ),
                    )
                    sessionDao.touch(sessionKey.value, now)

                    val text = StringBuilder()
                    val thinking = StringBuilder()
                    _streaming.value = StreamingState(thinking = "", content = "")
                    _busy.value = true
                    try {
                        val usage =
                            agentLoop.run(
                                history = buildLlmHistory(messageDao.list(sessionKey.value)),
                                instructions = buildInstructions(),
                                endpoint = endpoint,
                                model = currentModel(),
                                maxOutputTokens = BuiltinModels.byId(currentModel())?.maxOutputTokens,
                                thinkingEffort = currentEffort(),
                                mode = currentMode(),
                                context = toolContext,
                                hooks = hookConfig,
                                onEvent = { event ->
                                    handleEvent(event, text, thinking)
                                },
                                onCallStarted = { activeCall = it },
                            )
                        insertAssistant(text.toString(), thinking.toString().ifEmpty { null }, usage)
                    } catch (_: IOException) {
                        // A cancelled call surfaces as IOException; keep partial output.
                        if (text.isNotEmpty()) {
                            insertAssistant(text.toString(), thinking.toString().ifEmpty { null }, null)
                        }
                        if (!stopped) {
                            _error.value = "Request failed"
                        }
                    } finally {
                        _streaming.value = null
                        _busy.value = false
                        _pendingApproval.value = null
                        _runningTools.value = emptyList()
                        activeCall = null
                        val next = _queued.value.firstOrNull()
                        if (next != null) {
                            _queued.value = _queued.value.drop(1)
                            if (!stopped) {
                                launchTurn(next)
                            }
                        }
                    }
                }
        }

        private fun handleEvent(
            event: AgentEvent,
            text: StringBuilder,
            thinking: StringBuilder,
        ) {
            when (event) {
                is AgentEvent.TextDelta -> {
                    text.append(event.text)
                    publishStreaming(text, thinking)
                }

                is AgentEvent.ThinkingDelta -> {
                    thinking.append(event.text)
                    publishStreaming(text, thinking)
                }

                is AgentEvent.ToolStarted -> {
                    _runningTools.value =
                        _runningTools.value +
                        RunningTool(callId = event.callId, name = event.name, summary = event.summary)
                }

                is AgentEvent.ToolFinished -> {
                    _runningTools.value = _runningTools.value.filter { it.callId != event.callId }
                    viewModelScope.launch {
                        toolEventDao.insert(
                            ToolEventEntity(
                                id = event.callId,
                                sessionId = sessionKey.value,
                                name = event.name,
                                summary = "",
                                output = event.outputForModel,
                                isError = event.isError,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }

                is AgentEvent.ApprovalRequested -> {
                    _pendingApproval.value =
                        PendingApproval(callId = event.callId, name = event.name, summary = event.summary)
                }

                is AgentEvent.TodoListUpdated -> {
                    _todos.value = event.todos
                }
            }
        }

        private suspend fun createSession(seed: String) {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            sessionDao.upsert(
                SessionEntity(
                    id = id,
                    title =
                        seed
                            .lineSequence()
                            .firstOrNull()
                            .orEmpty()
                            .take(TITLE_CHARS),
                    workspacePath = workspace().path,
                    providerId = preferences.providerId.first().orEmpty(),
                    model = currentModel(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            sessionKey.value = id
        }

        private suspend fun resolveEndpoint(): LlmEndpoint? {
            val providerId = preferences.providerId.first() ?: return null
            val preset = ProviderPresets.byId(providerId) ?: return null
            val apiKey = apiKeyVault.load(providerId) ?: return null
            return LlmEndpoint(baseUrl = preset.baseUrl, protocol = preset.protocol, apiKey = apiKey)
        }

        private suspend fun buildToolContext(endpoint: LlmEndpoint): ToolContext {
            val providerId = preferences.providerId.first().orEmpty()
            val preset = ProviderPresets.byId(providerId)
            val webSearchBase =
                if (preset?.protocol == LlmProtocol.OPENAI) {
                    preset.baseUrl
                } else {
                    null
                }
            return ToolContext(
                workspaceRoot = workspace(),
                exec = exec,
                shellEnvironment =
                    BashTool.buildEnvironment(
                        homeDirectory = appContext.filesDir.path,
                        tmpDirectory = appContext.cacheDir.path,
                        nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
                    ),
                httpClient = httpClient,
                webSearchBaseUrl = webSearchBase,
                webSearchApiKey = endpoint.apiKey,
                endpoint = endpoint,
                model = currentModel(),
                thinkingEffort = currentEffort(),
            )
        }

        private fun workspace(): File = File(appContext.filesDir, "workspace").apply { mkdirs() }

        private suspend fun currentModel(): String = preferences.model.first() ?: BuiltinModels.glm53.id

        private suspend fun currentEffort(): ThinkingEffort? =
            preferences.thinkingEffort.first()?.let { effort ->
                runCatching { ThinkingEffort.valueOf(effort) }.getOrNull()
            }

        private suspend fun currentMode(): PermissionMode =
            runCatching { PermissionMode.valueOf(mode.value.uppercase()) }.getOrDefault(PermissionMode.BUILD)

        private suspend fun insertAssistant(
            content: String,
            thinking: String?,
            usage: LlmUsage?,
        ) {
            val now = System.currentTimeMillis()
            messageDao.insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionKey.value,
                    role = ROLE_ASSISTANT,
                    content = content,
                    thinking = thinking,
                    inputTokens = usage?.inputTokens,
                    outputTokens = usage?.outputTokens,
                    createdAt = now,
                ),
            )
            sessionDao.touch(sessionKey.value, now)
        }

        private suspend fun ensureSession(title: String) {
            if (sessionKey.value == NEW_SESSION) {
                createSession(title)
            }
        }

        private suspend fun insertRoleMessage(
            role: String,
            content: String,
        ) {
            val now = System.currentTimeMillis()
            messageDao.insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionKey.value,
                    role = role,
                    content = content,
                    createdAt = now,
                ),
            )
            sessionDao.touch(sessionKey.value, now)
        }

        // History for the model starts at the last summary or goal marker so
        // compaction actually reclaims context.
        private fun buildLlmHistory(all: List<MessageEntity>): List<LlmMessage> {
            val lastAnchor = all.indexOfLast { it.role == ROLE_SUMMARY || it.role == ROLE_GOAL }
            val slice = if (lastAnchor >= 0) all.subList(lastAnchor, all.size) else all
            return slice.map { message ->
                when (message.role) {
                    ROLE_SUMMARY -> LlmMessage(role = LlmRole.SYSTEM, content = "Conversation summary:\n${message.content}")
                    ROLE_GOAL -> LlmMessage(role = LlmRole.SYSTEM, content = message.content)
                    ROLE_USER -> LlmMessage(role = LlmRole.USER, content = message.content)
                    else -> LlmMessage(role = LlmRole.ASSISTANT, content = message.content)
                }
            }
        }

        // Workspace instructions: AGENTS.md, project memory, and available skills
        // are injected into the system prompt, matching the ZCode behavior.
        private fun buildInstructions(): String? {
            val parts = mutableListOf<String>()
            val agentsFile = File(workspace(), "AGENTS.md")
            if (agentsFile.isFile) {
                parts.add("Workspace instructions (AGENTS.md):\n" + agentsFile.readText(Charsets.UTF_8).take(MAX_INSTRUCTION_CHARS))
            }
            memoryStore.read(workspace())?.let { memory ->
                parts.add("Project memory (MEMORY.md):\n" + memory.take(MAX_INSTRUCTION_CHARS))
            }
            val skills = skillLoader.load(workspace())
            if (skills.isNotEmpty()) {
                val list =
                    skills.joinToString(separator = "\n") { skill ->
                        "- ${skill.name}: ${skill.description} (read ${skill.path.path} for the full skill)"
                    }
                parts.add("Available skills:\n$list")
            }
            return if (parts.isEmpty()) null else parts.joinToString(separator = "\n\n")
        }

        private fun publishStreaming(
            text: StringBuilder,
            thinking: StringBuilder,
        ) {
            _streaming.value = StreamingState(thinking = thinking.toString(), content = text.toString())
        }

        companion object {
            const val SESSION_ARG = "sessionId"
            private const val NEW_SESSION = "new"
            private const val ROLE_USER = "user"
            private const val ROLE_ASSISTANT = "assistant"
            private const val TITLE_CHARS = 48
            private const val MODE_BUILD = "build"
            private const val ROLE_SUMMARY = "summary"
            private const val ROLE_GOAL = "goal"
            private const val MAX_COMPACT_CHARS = 48_000
            private const val MAX_INSTRUCTION_CHARS = 12_000
            private const val COMPACTION_PROMPT =
                "Summarize this conversation. Capture every decision made, every file changed, " +
                    "commands run, and test results. Be complete but concise."
            private const val AGENTS_TEMPLATE =
                "# AGENTS.md\n\n" +
                    "Instructions for the ZCode agent working in this workspace.\n" +
                    "Describe build commands, conventions, and constraints here.\n"
        }
    }
