package com.zcode.android.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import com.zcode.android.core.agent.ApprovalAnswer
import com.zcode.android.core.agent.PermissionMode
import com.zcode.android.core.designsystem.ZcodeColors
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.ThinkingEffort
import com.zcode.android.core.storage.MessageEntity
import com.zcode.android.core.tools.TodoItem

@Composable
fun ChatScreen(
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenFiles: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val runningTools by viewModel.runningTools.collectAsStateWithLifecycle()
    val pendingApproval by viewModel.pendingApproval.collectAsStateWithLifecycle()
    val pendingQuestion by viewModel.pendingQuestion.collectAsStateWithLifecycle()
    val openSessionsRequested by viewModel.openSessionsRequested.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val queued by viewModel.queued.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val pickerOpen by viewModel.pickerOpen.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val effort by viewModel.thinkingEffort.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(openSessionsRequested) {
        if (openSessionsRequested) {
            viewModel.sessionsOpened()
            onOpenSessions()
        }
    }

    val rowCount = rows.size + runningTools.size + if (streaming == null) 0 else 1
    LaunchedEffect(rowCount, streaming?.content?.length, streaming?.thinking?.length) {
        if (rowCount > 0) {
            listState.animateScrollToItem(rowCount - 1)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ZcodeColors.bg)
                .imePadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(ZcodeColors.panel)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ZCode",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = ZcodeColors.text,
            )
            TextButton(onClick = viewModel::cycleMode) {
                Text(
                    text = modeLabel(mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.warning,
                )
            }
            TextButton(onClick = viewModel::openPicker) {
                Text(
                    text = model ?: "Model",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.primary,
                )
            }
            TextButton(onClick = onOpenTerminal) {
                Text(
                    text = ">_",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.primary,
                )
            }
            Text(
                text = "${usage.inputTokens + usage.outputTokens} tok",
                style = MaterialTheme.typography.labelSmall,
                color = ZcodeColors.muted,
            )
            TextButton(onClick = onOpenFiles) {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.primary,
                )
            }
            TextButton(onClick = onOpenSessions) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.primary,
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.primary,
                )
            }
        }

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { rowKey(it) }) { row ->
                when (row) {
                    is ChatRow.MessageRow -> {
                        MessageBubble(row.entity)
                    }

                    is ChatRow.ToolRow -> {
                        ToolCard(
                            name = row.entity.name,
                            summary = row.entity.summary,
                            output = row.entity.output,
                            isError = row.entity.isError,
                            running = false,
                        )
                    }
                }
            }
            runningTools.forEach { tool ->
                item(key = tool.callId) {
                    ToolCard(
                        name = tool.name,
                        summary = tool.summary,
                        output = null,
                        isError = false,
                        running = true,
                    )
                }
            }
            streaming?.let { state ->
                if (state.content.isNotEmpty() || state.thinking.isNotEmpty()) {
                    item(key = "streaming") {
                        AssistantBlock(
                            thinking = state.thinking.ifEmpty { null },
                            content = state.content,
                        )
                    }
                }
            }
        }

        if (todos.isNotEmpty()) {
            TodoWidget(todos = todos)
        }

        error?.let { message ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(ZcodeColors.panel)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZcodeColors.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissError) {
                    Text(text = "Dismiss", color = ZcodeColors.muted)
                }
            }
        }

        if (queued.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(ZcodeColors.panel)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queued: ${queued.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.muted,
                )
                queued.take(3).forEachIndexed { index, prompt ->
                    TextButton(onClick = { viewModel.removeFromQueue(index) }) {
                        Text(
                            text = prompt.take(24),
                            style = MaterialTheme.typography.labelMedium,
                            color = ZcodeColors.text,
                        )
                    }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Surface(
                color = ZcodeColors.panel,
                shape = RoundedCornerShape(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    suggestions.forEach { command ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        input = "/" + command.name + " "
                                        viewModel.clearSuggestions()
                                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "/" + command.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                color = ZcodeColors.primary,
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZcodeColors.muted,
                            )
                        }
                    }
                }
            }
        }

        pendingQuestion?.let { question ->
            QuestionPanel(question = question, onAnswer = viewModel::answerQuestion)
        }

        pendingApproval?.let { approval ->
            ApprovalPanel(
                approval = approval,
                onAnswer = viewModel::approve,
            )
        }

        InputRow(
            value = input,
            onValueChange = { value ->
                input = value
                viewModel.onInputChanged(value)
            },
            busy = busy,
            onSend = {
                if (input.startsWith("/")) {
                    viewModel.tryHandleSlash(input)
                    viewModel.clearSuggestions()
                } else {
                    viewModel.send(input)
                }
                input = ""
            },
            onStop = viewModel::stop,
        )
    }

    if (pickerOpen) {
        ModelPickerSheet(
            selectedModel = model,
            selectedEffort = effort,
            onSelectModel = viewModel::selectModel,
            onSelectEffort = viewModel::selectEffort,
            onDismiss = viewModel::pickerShown,
        )
    }
}

private fun modeLabel(mode: String): String =
    runCatching { PermissionMode.valueOf(mode.uppercase()) }.getOrDefault(PermissionMode.BUILD).label

private fun rowKey(row: ChatRow): String =
    when (row) {
        is ChatRow.MessageRow -> row.entity.id
        is ChatRow.ToolRow -> row.entity.id
    }

@Composable
private fun MessageBubble(entity: MessageEntity) {
    if (entity.role == "user") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = ZcodeColors.userMessage,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = entity.content,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZcodeColors.text,
                )
            }
        }
    } else {
        AssistantBlock(
            thinking = entity.thinking,
            content = entity.content,
        )
    }
}

@Composable
private fun AssistantBlock(
    thinking: String?,
    content: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        thinking?.let { value ->
            Text(
                text = "Thinking",
                style = MaterialTheme.typography.labelSmall,
                color = ZcodeColors.muted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = ZcodeColors.thinking,
            )
        }
        if (content.isNotEmpty()) {
            Markdown(
                content = content,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToolCard(
    name: String,
    summary: String,
    output: String?,
    isError: Boolean,
    running: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = if (running) ZcodeColors.primary else ZcodeColors.border
    Surface(
        color = ZcodeColors.panel,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "-" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZcodeColors.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = ZcodeColors.text,
                    )
                    if (summary.isNotEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = ZcodeColors.muted,
                        )
                    }
                }
                Text(
                    text =
                        when {
                            running -> "running"
                            isError -> "failed"
                            else -> "done"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        when {
                            running -> ZcodeColors.primary
                            isError -> ZcodeColors.error
                            else -> ZcodeColors.success
                        },
                )
            }
            if (expanded && !output.isNullOrEmpty()) {
                HorizontalDivider(color = ZcodeColors.borderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = ZcodeColors.text,
                )
            }
        }
    }
}

@Composable
private fun TodoWidget(todos: List<TodoItem>) {
    Surface(
        color = ZcodeColors.panel,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Todos",
                style = MaterialTheme.typography.labelMedium,
                color = ZcodeColors.muted,
            )
            todos.forEach { todo ->
                Text(
                    text =
                        when (todo.status) {
                            "completed" -> "[x] ${todo.content}"
                            "in_progress" -> "[~] ${todo.content}"
                            else -> "[ ] ${todo.content}"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (todo.status == "completed") ZcodeColors.muted else ZcodeColors.text,
                )
            }
        }
    }
}

@Composable
private fun QuestionPanel(
    question: QuestionCard,
    onAnswer: (String) -> Unit,
) {
    Surface(
        color = ZcodeColors.panel,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(width = 1.dp, color = ZcodeColors.secondary, shape = RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleSmall,
                color = ZcodeColors.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    TextButton(onClick = { onAnswer(option) }) {
                        Text(text = option, color = ZcodeColors.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalPanel(
    approval: PendingApproval,
    onAnswer: (ApprovalAnswer) -> Unit,
) {
    Surface(
        color = ZcodeColors.panel,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(width = 1.dp, color = ZcodeColors.primary, shape = RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Allow ${approval.name}?",
                style = MaterialTheme.typography.titleSmall,
                color = ZcodeColors.text,
            )
            Text(
                text = approval.summary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = ZcodeColors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAnswer(ApprovalAnswer.ALLOW_ONCE) }) {
                    Text(text = "Allow", color = ZcodeColors.success)
                }
                TextButton(onClick = { onAnswer(ApprovalAnswer.ALLOW_ALWAYS) }) {
                    Text(text = "Always allow", color = ZcodeColors.success)
                }
                TextButton(onClick = { onAnswer(ApprovalAnswer.REJECT_ONCE) }) {
                    Text(text = "Reject", color = ZcodeColors.error)
                }
                TextButton(onClick = { onAnswer(ApprovalAnswer.REJECT_ALWAYS) }) {
                    Text(text = "Always reject", color = ZcodeColors.error)
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZcodeColors.panel)
                .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(text = "Message", color = ZcodeColors.muted) },
            maxLines = 5,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZcodeColors.primary,
                    unfocusedBorderColor = ZcodeColors.border,
                    cursorColor = ZcodeColors.primary,
                    focusedTextColor = ZcodeColors.text,
                    unfocusedTextColor = ZcodeColors.text,
                    focusedContainerColor = ZcodeColors.element,
                    unfocusedContainerColor = ZcodeColors.element,
                ),
        )
        IconButton(onClick = if (busy) onStop else onSend) {
            if (busy) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Stop",
                    tint = ZcodeColors.error,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = ZcodeColors.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    selectedModel: String?,
    selectedEffort: String?,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ZcodeColors.panel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleSmall,
                color = ZcodeColors.text,
            )
            BuiltinModels.all.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = model.id == selectedModel,
                        onClick = { onSelectModel(model.id) },
                    )
                    Column {
                        Text(
                            text = model.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZcodeColors.text,
                        )
                        Text(
                            text = "${model.contextTokens / 1000}K context",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZcodeColors.muted,
                        )
                    }
                }
            }
            HorizontalDivider(color = ZcodeColors.borderSubtle)
            Text(
                text = "Thinking effort",
                style = MaterialTheme.typography.titleSmall,
                color = ZcodeColors.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options =
                    listOf(
                        "Default" to null,
                        "Low" to ThinkingEffort.LOW,
                        "High" to ThinkingEffort.HIGH,
                        "Max" to ThinkingEffort.MAX,
                    )
                options.forEach { (label, effort) ->
                    FilterChip(
                        selected = effort?.name == selectedEffort || (effort == null && selectedEffort == null),
                        onClick = { onSelectEffort(effort?.name) },
                        label = {
                            Text(
                                text = label,
                                color = ZcodeColors.text,
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
