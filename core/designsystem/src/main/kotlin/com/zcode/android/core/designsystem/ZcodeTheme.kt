package com.zcode.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Maps the ZCode tokens onto Material 3 roles. Roles without a ZCode equivalent keep
// the Material dark defaults. A light scheme lands together with the theme setting.
private val ZcodeColorScheme = darkColorScheme(
    primary = ZcodeColors.primary,
    onPrimary = ZcodeColors.bg,
    secondary = ZcodeColors.secondary,
    onSecondary = ZcodeColors.bg,
    tertiary = ZcodeColors.info,
    onTertiary = ZcodeColors.bg,
    background = ZcodeColors.bg,
    onBackground = ZcodeColors.text,
    surface = ZcodeColors.panel,
    onSurface = ZcodeColors.text,
    surfaceVariant = ZcodeColors.element,
    onSurfaceVariant = ZcodeColors.muted,
    error = ZcodeColors.error,
    onError = ZcodeColors.bg,
    outline = ZcodeColors.border,
    outlineVariant = ZcodeColors.borderSubtle,
)

@Composable
fun ZcodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZcodeColorScheme,
        content = content,
    )
}
