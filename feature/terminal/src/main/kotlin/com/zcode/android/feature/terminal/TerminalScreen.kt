package com.zcode.android.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zcode.android.core.designsystem.ZcodeColors
import org.connectbot.terminal.Terminal

private val helperKeys =
    listOf(
        "Tab" to "\t",
        "Esc" to "\u001B",
        "Ctrl+C" to "\u0003",
        "Ctrl+D" to "\u0004",
        "Left" to "\u001B[D",
        "Up" to "\u001B[A",
        "Down" to "\u001B[B",
        "Right" to "\u001B[C",
    )

@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ZcodeColors.bg),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(ZcodeColors.panel)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ZcodeColors.text,
                )
            }
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleMedium,
                color = ZcodeColors.text,
            )
        }
        val active = terminal
        if (active == null) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ZcodeColors.primary)
            }
        } else {
            Terminal(
                terminalEmulator = active.emulator,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                backgroundColor = ZcodeColors.bg,
                foregroundColor = ZcodeColors.text,
                keyboardEnabled = true,
            )
            HelperKeyRow(
                onSend = { sequence -> active.session.write(sequence.toByteArray()) },
            )
        }
    }
}

@Composable
private fun HelperKeyRow(onSend: (String) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZcodeColors.panel)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        helperKeys.forEach { (label, sequence) ->
            TextButton(onClick = { onSend(sequence) }) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = ZcodeColors.text,
                )
            }
        }
    }
}
