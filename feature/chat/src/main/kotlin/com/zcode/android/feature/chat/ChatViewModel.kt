package com.zcode.android.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.agent.ConversationEngine
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.LlmMessage
import com.zcode.android.core.engine.LlmRole
import com.zcode.android.core.engine.LlmUsage
import com.zcode.android.core.engine.ProviderPresets
import com.zcode.android.core.engine.ThinkingEffort
import com.zcode.android.core.storage.ApiKeyVault
import com.zcode.android.core.storage.MessageDao
import com.zcode.android.core.storage.MessageEntity
import com.zcode.android.core.storage.SessionDao
import com.zcode.android.core.storage.SessionEntity
import com.zcode.android.core.storage.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Call
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class StreamingState(
    val thinking: String,
    val content: String,
)

// One chat screen bound to one session. The session is created on the first
// prompt of a new conversation, and every message is persisted as it arrives.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val sessionDao: SessionDao,
        private val messageDao: MessageDao,
        private val preferences: UserPreferences,
        private val apiKeyVault: ApiKeyVault,
        private val engine: ConversationEngine,
    ) : ViewModel() {
        private val sessionKey = MutableStateFlow(savedStateHandle.get<String>(SESSION_ARG) ?: NEW_SESSION)

        val messages =
            sessionKey
                .flatMapLatest { messageDao.observe(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val model: StateFlow<String?> =
            preferences.model.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val thinkingEffort: StateFlow<String?> =
            preferences.thinkingEffort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _streaming = MutableStateFlow<StreamingState?>(null)
        val streaming: StateFlow<StreamingState?> = _streaming.asStateFlow()

        private val _busy = MutableStateFlow(false)
        val busy: StateFlow<Boolean> = _busy.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private var turnJob: Job? = null

        private var activeCall: Call? = null

        @Volatile
        private var stopped = false

        fun send(prompt: String) {
            val trimmed = prompt.trim()
            if (trimmed.isEmpty() || turnJob?.isActive == true) {
                return
            }
            stopped = false
            turnJob =
                viewModelScope.launch {
                    if (sessionKey.value == NEW_SESSION) {
                        createSession(trimmed)
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
                            content = trimmed,
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
                            engine.runTurn(
                                history = messageDao.list(sessionKey.value).map { it.toLlmMessage() },
                                endpoint = endpoint,
                                model = currentModel(),
                                maxOutputTokens = BuiltinModels.byId(currentModel())?.maxOutputTokens,
                                thinkingEffort = currentEffort(),
                                onTextDelta = { delta ->
                                    text.append(delta)
                                    publishStreaming(text, thinking)
                                },
                                onThinkingDelta = { delta ->
                                    thinking.append(delta)
                                    publishStreaming(text, thinking)
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
                        activeCall = null
                    }
                }
        }

        fun stop() {
            stopped = true
            activeCall?.cancel()
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
                    workspacePath = "",
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

        private suspend fun currentModel(): String = preferences.model.first() ?: BuiltinModels.glm53.id

        private suspend fun currentEffort(): ThinkingEffort? =
            preferences.thinkingEffort.first()?.let { effort ->
                runCatching { ThinkingEffort.valueOf(effort) }.getOrNull()
            }

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
        }
    }
