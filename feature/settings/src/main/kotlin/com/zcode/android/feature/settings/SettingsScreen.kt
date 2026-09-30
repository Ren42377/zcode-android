package com.zcode.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.zcode.android.core.agent.PermissionMode
import com.zcode.android.core.designsystem.ZcodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val provider by viewModel.providerId.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val effort by viewModel.thinkingEffort.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val termuxMode by viewModel.termuxMode.collectAsStateWithLifecycle()
    val alwaysAllowed by viewModel.alwaysAllowed.collectAsStateWithLifecycle()
    val mcpStatuses by viewModel.mcpStatuses.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    var pluginRepo by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ZcodeColors.bg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(text = "Back", color = ZcodeColors.primary)
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = ZcodeColors.text,
            )
        }

        Section(title = "Provider") {
            viewModel.providerLabels().forEach { (id, label) ->
                ChoiceRow(label = label, selected = id == provider) { viewModel.setProvider(id) }
            }
        }

        Section(title = "Model") {
            viewModel.modelLabels().forEach { (id, label) ->
                ChoiceRow(label = label, selected = id == model) { viewModel.setModel(id) }
            }
        }

        Section(title = "Thinking effort") {
            listOf("Default" to null, "Low" to "LOW", "High" to "HIGH", "Max" to "MAX").forEach { (label, value) ->
                ChoiceRow(label = label, selected = value == effort) { viewModel.setEffort(value) }
            }
        }

        Section(title = "Permission mode") {
            PermissionMode.entries.forEach { entry ->
                ChoiceRow(label = entry.label, selected = entry.name.lowercase() == mode) { viewModel.setMode(entry) }
            }
        }

        Section(title = "Terminal") {
            ToggleRow(
                label = "Use Termux userland for commands",
                checked = termuxMode,
                enabled = viewModel.termuxInstalled,
                onToggle = viewModel::setTermuxMode,
            )
            Text(
                text =
                    when {
                        !viewModel.termuxInstalled -> "Termux is not installed. The built-in shell (mksh) is used."
                        !viewModel.termuxPermissionGranted -> "Grant the RUN_COMMAND permission to Termux to enable this."
                        else -> "Termux is ready."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = ZcodeColors.muted,
            )
        }

        Section(title = "Approvals") {
            Text(
                text =
                    if (alwaysAllowed.isEmpty()) {
                        "No tools are always allowed."
                    } else {
                        "Always allowed: ${alwaysAllowed.joinToString()}"
                    },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = ZcodeColors.muted,
            )
            TextButton(onClick = viewModel::resetAlwaysAllowed) {
                Text(text = "Reset always allowed", color = ZcodeColors.warning)
            }
        }

        Section(title = "MCP servers") {
            if (mcpStatuses.isEmpty()) {
                Text(
                    text = "No MCP servers configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZcodeColors.muted,
                )
            } else {
                mcpStatuses.forEach { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = ZcodeColors.text,
                    )
                }
            }
            TextButton(onClick = viewModel::refreshMcp) {
                Text(text = "Refresh", color = ZcodeColors.primary)
            }
        }

        Section(title = "Hooks") {
            Text(
                text = viewModel.hookSummary(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = ZcodeColors.muted,
            )
        }

        Section(title = "Plugins") {
            if (plugins.isEmpty()) {
                Text(
                    text = "No plugins installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZcodeColors.muted,
                )
            } else {
                plugins.forEach { plugin ->
                    Text(
                        text = plugin,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = ZcodeColors.text,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = pluginRepo,
                    onValueChange = { pluginRepo = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(text = "owner/repo", color = ZcodeColors.muted) },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZcodeColors.primary,
                            unfocusedBorderColor = ZcodeColors.border,
                            cursorColor = ZcodeColors.primary,
                            focusedTextColor = ZcodeColors.text,
                            unfocusedTextColor = ZcodeColors.text,
                        ),
                )
                TextButton(
                    onClick = {
                        viewModel.installPlugin(pluginRepo.trim(), "main")
                        pluginRepo = ""
                    },
                ) {
                    Text(text = "Install", color = ZcodeColors.primary)
                }
            }
        }

        Section(title = "Updates") {
            TextButton(onClick = viewModel::checkForUpdates) {
                Text(text = if (update.checking) "Checking" else "Check for updates", color = ZcodeColors.primary)
            }
            update.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZcodeColors.muted,
                )
            }
        }

        Section(title = "API key") {
            TextButton(onClick = viewModel::clearApiKey) {
                Text(text = "Remove stored key", color = ZcodeColors.error)
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = ZcodeColors.panel,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ZcodeColors.text,
            )
            HorizontalDivider(color = ZcodeColors.borderSubtle)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "[x]" else "[ ]",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (selected) ZcodeColors.primary else ZcodeColors.muted,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = ZcodeColors.text,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) ZcodeColors.text else ZcodeColors.muted,
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
    }
}
