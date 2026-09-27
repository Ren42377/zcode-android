package com.zcode.android.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zcode.android.core.designsystem.ZcodeColors
import com.zcode.android.core.engine.ProviderPresets

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()

    LaunchedEffect(done) {
        if (done) {
            onDone()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ZcodeColors.bg)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "ZCode",
            style = MaterialTheme.typography.headlineMedium,
            color = ZcodeColors.text,
        )
        Text(
            text =
                "An AI coding agent for Android. Paste the API key for your Z.ai or BigModel account " +
                    "to start. The key is validated once and stored in the Android Keystore.",
            style = MaterialTheme.typography.bodyMedium,
            color = ZcodeColors.muted,
        )
        ProviderPicker(
            selectedId = form.providerId,
            onSelect = { id -> viewModel.update { it.copy(providerId = id) } },
        )
        OutlinedTextField(
            value = form.apiKey,
            onValueChange = { value -> viewModel.update { it.copy(apiKey = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "API key") },
            singleLine = true,
            visualTransformation =
                if (form.showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZcodeColors.primary,
                    cursorColor = ZcodeColors.primary,
                    focusedTextColor = ZcodeColors.text,
                    unfocusedTextColor = ZcodeColors.text,
                ),
            trailingIcon = {
                OutlinedButton(
                    onClick = { viewModel.update { it.copy(showKey = !it.showKey) } },
                ) {
                    Text(
                        text = if (form.showKey) "Hide" else "Show",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        )
        if (error != null) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = ZcodeColors.error,
            )
        }
        Button(
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !form.validating && form.apiKey.isNotBlank(),
        ) {
            Text(text = if (form.validating) "Validating" else "Validate and start")
        }
        if (form.validating) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ZcodeColors.primary,
                )
            }
        }
    }
}

@Composable
private fun ProviderPicker(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = ProviderPresets.byId(selectedId)
    Column {
        Text(
            text = "Provider",
            style = MaterialTheme.typography.labelMedium,
            color = ZcodeColors.muted,
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.label.orEmpty(),
                color = ZcodeColors.text,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = ZcodeColors.panel,
        ) {
            ProviderPresets.all.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(text = preset.label, color = ZcodeColors.text) },
                    onClick = {
                        expanded = false
                        onSelect(preset.id)
                    },
                )
            }
        }
    }
}
