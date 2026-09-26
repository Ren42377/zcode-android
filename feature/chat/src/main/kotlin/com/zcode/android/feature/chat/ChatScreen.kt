package com.zcode.android.feature.chat

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import com.zcode.android.core.designsystem.ZcodeColors
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.ThinkingEffort
import com.zcode.android.core.storage.MessageEntity

data class ChatEntry(
    val id: String,
    val isUser: Boolean,
    val content: String,
    val thinking: String?,
)

private fun MessageEntity.toEntry(): ChatEntry =
    ChatEntry(
        id = id,
        isUser = role == "user",
        content = content,
        thinking = thinking,
    )

@Composable
fun ChatScreen(
    onOpenTerminal: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val effort by viewModel.thinkingEffort.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var pickerVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val entryCount = messages.size + if (streaming == null) 0 else 1
    LaunchedEffect(entryCount, streaming?.content?.length, streaming?.thinking?.length) {
        if (entryCount > 0) {
            listState.animateScrollToItem(entryCount - 1)
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
            TextButton(onClick = { pickerVisible = true }) {
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
            items(messages, key = { it.id }) { entity ->
                MessageBubble(entity = entity.toEntry())
            }
            streaming?.let { state ->
                if (state.content.isNotEmpty() || state.thinking.isNotEmpty()) {
                    item(key = "streaming") {
                        MessageBubble(
                            entry =
                                ChatEntry(
                                    id = "streaming",
                                    isUser = false,
                                    content = state.content,
                                    thinking = state.thinking.ifEmpty { null },
                                ),
                        )
                    }
                }
            }
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

        InputRow(
            value = input,
            onValueChange = { input = it },
            busy = busy,
            onSend = {
                viewModel.send(input)
                input = ""
            },
            onStop = viewModel::stop,
        )
    }

    if (pickerVisible) {
        ModelPickerSheet(
            selectedModel = model,
            selectedEffort = effort,
            onSelectModel = viewModel::selectModel,
            onSelectEffort = viewModel::selectEffort,
            onDismiss = { pickerVisible = false },
        )
    }
}

@Composable
private fun MessageBubble(entity: ChatEntry) {
    if (entity.isUser) {
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
        Column(modifier = Modifier.fillMaxWidth()) {
            entity.thinking?.let { thinking ->
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZcodeColors.muted,
                )
                Text(
                    text = thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZcodeColors.thinking,
                )
            }
            Markdown(
                content = entity.content,
                modifier = Modifier.fillMaxWidth(),
            )
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
