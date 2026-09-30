package com.zcode.android.core.agent

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Emits AskUserQuestion requests as agent events and suspends until the UI
// answers. Kept separate from the tool so the tool stays free of UI concerns.
@Singleton
class QuestionCoordinator
    @Inject
    constructor() {
        private val pending = mutableMapOf<String, kotlinx.coroutines.CompletableDeferred<String>>()

        @Synchronized
        fun register(questionId: String): kotlinx.coroutines.CompletableDeferred<String> =
            kotlinx.coroutines.CompletableDeferred<String>().also { pending[questionId] = it }

        @Synchronized
        fun answer(
            questionId: String,
            answer: String,
        ) {
            pending.remove(questionId)?.complete(answer)
        }

        @Synchronized
        fun cancelAll() {
            pending.values.forEach { it.cancel() }
            pending.clear()
        }

        // Registers the request, hands the id to the caller so it can emit the
        // event, and suspends until an answer arrives.
        suspend fun await(
            question: String,
            options: List<String>,
            onAsked: (String) -> Unit,
        ): String {
            val id = UUID.randomUUID().toString()
            val deferred = register(id)
            onAsked(id)
            return deferred.await()
        }
    }
