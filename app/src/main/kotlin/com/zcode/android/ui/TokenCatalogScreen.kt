package com.zcode.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.zcode.android.core.designsystem.ZcodeColors

private data class Token(
    val name: String,
    val color: Color,
)

// Every color token of the ZCode theme, shown on one screen so the design system
// values can be verified visually on a device.
private val tokens =
    listOf(
        Token("bg", ZcodeColors.bg),
        Token("panel", ZcodeColors.panel),
        Token("element", ZcodeColors.element),
        Token("userMessage", ZcodeColors.userMessage),
        Token("text", ZcodeColors.text),
        Token("muted", ZcodeColors.muted),
        Token("primary", ZcodeColors.primary),
        Token("secondary", ZcodeColors.secondary),
        Token("error", ZcodeColors.error),
        Token("warning", ZcodeColors.warning),
        Token("success", ZcodeColors.success),
        Token("info", ZcodeColors.info),
        Token("border", ZcodeColors.border),
        Token("borderSubtle", ZcodeColors.borderSubtle),
        Token("diffAddedBg", ZcodeColors.diffAddedBg),
        Token("diffAddedLineBg", ZcodeColors.diffAddedLineBg),
        Token("diffRemovedBg", ZcodeColors.diffRemovedBg),
        Token("diffRemovedLineBg", ZcodeColors.diffRemovedLineBg),
    )

@Composable
fun TokenCatalogScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ZcodeColors.bg)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ZCode for Android",
            style = MaterialTheme.typography.headlineSmall,
            color = ZcodeColors.text,
        )
        Text(
            text = "Design token preview. This placeholder screen validates the color system and will be replaced by the onboarding flow.",
            style = MaterialTheme.typography.bodyMedium,
            color = ZcodeColors.muted,
        )
        tokens.forEach { token ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokenSwatch(color = token.color)
                Text(
                    text = token.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZcodeColors.text,
                )
                Text(
                    text = token.color.toHexString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZcodeColors.muted,
                )
            }
        }
    }
}

@Composable
private fun TokenSwatch(color: Color) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .background(color = color, shape = RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = ZcodeColors.border, shape = RoundedCornerShape(8.dp)),
    )
}

private fun Color.toHexString(): String = "#%06X".format(toArgb() and 0xFFFFFF)
