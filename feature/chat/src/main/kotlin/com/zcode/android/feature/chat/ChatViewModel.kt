package com.zcode.android.feature.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.agent.AgentEvent
import com.zcode.android.core.agent.AgentLoop
import com.zcode.android.core.agent.ApprovalAnswer
import com.zcode.android.core.agent.ApprovalCoordinator
import com.zcode.android.core.agent.PermissionMode
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.LlmMessage
import com.zcode.android.core.engine.LlmRole
import com.zcode.android.core.engine.LlmProtocol
import com.zcode.android.core.engine.LlmUsage
import com.zcode.android.core.engine.ProviderPresets
import com.zcode.android.core.engine.ThinkingEffort
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
import com.zcode.android.core.tools.ExecServiceHolder
import com.zcode.android.core.tools.TodoItem
import com.zcode.android.core.tools.ToolContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
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

        private var turnJob: Job? = null
        private var activeCall: Call? = null

        @Volatile
        private var stopped = false

        private val exec = ExecServiceHolder.exec
        private val httpClient = OkHttpClient()

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
                                history = messageDao.list(sessionKey.value).map { it.toLlmMessage() },
                                endpoint = endpoint,
                                model = currentModel(),
                                maxOutputTokens = BuiltinModels.byId(currentModel())?.maxOutputTokens,
                                thinkingEffort = currentEffort(),
                                mode = currentMode(),
                                context = buildToolContext(endpoint),
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
                    title = seed.lineSequence().firstOrNull().orEmpty().take(TITLE_CHARS),
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

        private fun publishStreaming(
            text: StringBuilder,
            thinking: StringBuilder,
        ) {
            _streaming.value = StreamingState(thinking = thinking.toString(), content = text.toString())
        }

        private fun MessageEntity.toLlmMessage(): LlmMessage =
            LlmMessage(
                role = if (role == ROLE_USER) LlmRole.USER else LlmRole.ASSISTANT,
                content = content,
            )

        companion object {
            const val SESSION_ARG = "sessionId"
            private const val NEW_SESSION = "new"
            private const val ROLE_USER = "user"
            private const val ROLE_ASSISTANT = "assistant"
            private const val TITLE_CHARS = 48
            private const val MODE_BUILD = "build"
        }
    }
