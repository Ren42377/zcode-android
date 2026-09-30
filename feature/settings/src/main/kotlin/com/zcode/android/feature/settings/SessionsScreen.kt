package com.zcode.android.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zcode.android.core.designsystem.ZcodeColors

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val filtered = sessions.filter { it.title.contains(query, ignoreCase = true) }

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
                text = "Sessions",
                style = MaterialTheme.typography.titleLarge,
                color = ZcodeColors.text,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Search sessions", color = ZcodeColors.muted) },
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
        update.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ZcodeColors.muted,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { session ->
                Surface(
                    color = ZcodeColors.panel,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = session.title.ifEmpty { "Untitled" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZcodeColors.text,
                        )
                        Text(
                            text = session.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZcodeColors.muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.forkSession(session.id) }) {
                                Text(text = "Fork", color = ZcodeColors.secondary)
                            }
                            TextButton(onClick = { viewModel.exportSession(session.id) }) {
                                Text(text = "Export", color = ZcodeColors.info)
                            }
                            TextButton(onClick = { viewModel.deleteSession(session.id) }) {
                                Text(text = "Delete", color = ZcodeColors.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
