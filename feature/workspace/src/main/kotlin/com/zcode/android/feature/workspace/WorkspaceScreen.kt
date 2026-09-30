package com.zcode.android.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.zcode.android.core.designsystem.ZcodeColors

@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val openFile by viewModel.openFile.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var newDirectoryName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ZcodeColors.bg)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(text = "Back", color = ZcodeColors.primary)
            }
            Text(
                text = "Files",
                style = MaterialTheme.typography.titleLarge,
                color = ZcodeColors.text,
            )
            if (currentPath.isNotEmpty()) {
                TextButton(onClick = viewModel::up) {
                    Text(text = "Up", color = ZcodeColors.secondary)
                }
            }
        }

        val file = openFile
        if (file != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.first,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = ZcodeColors.text,
                )
                TextButton(onClick = viewModel::closeFile) {
                    Text(text = "Close", color = ZcodeColors.muted)
                }
            }
            Surface(
                color = ZcodeColors.panel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                ) {
                    Text(
                        text = file.second.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = ZcodeColors.text,
                    )
                    if (file.second.truncated) {
                        Text(
                            text = "[preview truncated]",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZcodeColors.warning,
                        )
                    }
                }
            }
        } else {
            error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZcodeColors.error,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.path }) { node ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (node.isDirectory) viewModel.enter(node.path) else viewModel.open(node.path)
                                }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (node.isDirectory) "[dir]" else "     ",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = ZcodeColors.muted,
                        )
                        Text(
                            text = node.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = ZcodeColors.text,
                        )
                        if (node.changed) {
                            Text(
                                text = "M",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZcodeColors.warning,
                            )
                        }
                        TextButton(onClick = { viewModel.delete(node.path) }) {
                            Text(text = "x", color = ZcodeColors.error)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newDirectoryName,
                    onValueChange = { newDirectoryName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(text = "New folder name", color = ZcodeColors.muted) },
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
                        viewModel.createDirectory(newDirectoryName)
                        newDirectoryName = ""
                    },
                ) {
                    Text(text = "Create", color = ZcodeColors.primary)
                }
            }
        }
    }
}
