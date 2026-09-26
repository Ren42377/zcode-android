package com.zcode.android.core.agent

import com.zcode.android.core.engine.LlmClient
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.LlmEvent
import com.zcode.android.core.engine.LlmMessage
import com.zcode.android.core.engine.LlmRequest
import com.zcode.android.core.engine.LlmRole
import com.zcode.android.core.engine.LlmUsage
import com.zcode.android.core.engine.ThinkingEffort
import okhttp3.Call
import javax.inject.Inject

data class TurnResult(
    val content: String,
    val thinking: String?,
    val usage: LlmUsage?,
)

// Runs one conversation turn: system prompt plus history in, streamed deltas out.
// Tool orchestration, compaction, and permission modes arrive in milestone M3.
class ConversationEngine
    @Inject
    constructor(
        private val client: LlmClient,
    ) {
        suspend fun runTurn(
            history: List<LlmMessage>,
            endpoint: LlmEndpoint,
            model: String,
            maxOutputTokens: Int?,
            thinkingEffort: ThinkingEffort?,
            onTextDelta: (String) -> Unit,
            onThinkingDelta: (String) -> Unit,
            onCallStarted: (Call) -> Unit = {},
        ): TurnResult {
            val text = StringBuilder()
            val thinking = StringBuilder()
            val usage =
                client.stream(
                    request =
                        LlmRequest(
                            model = model,
                            messages = listOf(LlmMessage(role = LlmRole.SYSTEM, content = SYSTEM_PROMPT)) + history,
                            maxOutputTokens = maxOutputTokens,
                            thinkingEffort = thinkingEffort,
                        ),
                    endpoint = endpoint,
                    onEvent = { event ->
                        when (event) {
                            is LlmEvent.TextDelta -> {
                                text.append(event.text)
                                onTextDelta(event.text)
                            }

                            is LlmEvent.ThinkingDelta -> {
                                thinking.append(event.text)
                                onThinkingDelta(event.text)
                            }
                        }
                    },
                    onCallStarted = onCallStarted,
                )
            return TurnResult(
                content = text.toString(),
                thinking = thinking.toString().ifEmpty { null },
                usage = usage,
            )
        }

        private companion object {
            // Minimal identity prompt for milestone M2. Workspace instructions,
            // memory, and skills are injected by later milestones (see roadmap).
            const val SYSTEM_PROMPT =
                "You are ZCode, an AI coding agent running on an Android device. " +
                    "Help the user with software engineering tasks and be precise and concise."
        }
    }
