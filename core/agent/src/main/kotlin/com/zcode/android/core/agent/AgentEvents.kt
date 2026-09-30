package com.zcode.android.core.agent

import com.zcode.android.core.tools.TodoItem
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

// Events the agent loop emits while a turn runs; the chat UI renders them live.
sealed interface AgentEvent {
    data class TextDelta(
        val text: String,
    ) : AgentEvent

    data class ThinkingDelta(
        val text: String,
    ) : AgentEvent

    data class ToolStarted(
        val callId: String,
        val name: String,
        val summary: String,
    ) : AgentEvent

    data class ToolFinished(
        val callId: String,
        val name: String,
        val outputForModel: String,
        val isError: Boolean,
    ) : AgentEvent

    data class ApprovalRequested(
        val callId: String,
        val name: String,
        val summary: String,
    ) : AgentEvent

    data class TodoListUpdated(
        val todos: List<TodoItem>,
    ) : AgentEvent

    data class QuestionAsked(
        val questionId: String,
        val question: String,
        val options: List<String>,
    ) : AgentEvent
}

enum class ApprovalAnswer {
    ALLOW_ONCE,
    ALLOW_ALWAYS,
    REJECT_ONCE,
    REJECT_ALWAYS,
}

// Bridges approval requests from the agent loop to the UI: the loop suspends on
// a deferred registered here, and the screen completes it with the user answer.
@Singleton
class ApprovalCoordinator
    @Inject
    constructor() {
        private val pending = mutableMapOf<String, CompletableDeferred<ApprovalAnswer>>()

        @Synchronized
        fun register(callId: String): CompletableDeferred<ApprovalAnswer> =
            CompletableDeferred<ApprovalAnswer>().also { pending[callId] = it }

        @Synchronized
        fun answer(
            callId: String,
            answer: ApprovalAnswer,
        ) {
            pending.remove(callId)?.complete(answer)
        }

        @Synchronized
        fun cancelAll() {
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }
