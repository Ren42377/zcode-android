package com.zcode.android.core.designsystem

import androidx.compose.ui.graphics.Color

// Color tokens from the default dark theme of the ZCode terminal UI
// (apps/zcode-cli/packages/tui/src/theme/defaults.ts in zai-org/ZCode).
// Values are pinned by ZcodeColorsTest. Do not adjust them freely.
object ZcodeColors {
    val bg = Color(0xFF0F1419)
    val panel = Color(0xFF161B22)
    val element = Color(0xFF1F2937)
    val userMessage = Color(0xFF30363D)
    val text = Color(0xFFE5E7EB)
    val muted = Color(0xFF94A3B8)
    val primary = Color(0xFF7DD3FC)
    val secondary = Color(0xFFC4B5FD)
    val error = Color(0xFFFCA5A5)
    val warning = Color(0xFFFBBF24)
    val success = Color(0xFF86EFAC)
    val info = Color(0xFF93C5FD)
    val border = Color(0xFF3B4450)
    val borderSubtle = Color(0xFF26313D)
    val diffAddedBg = Color(0xFF12351E)
    val diffAddedLineBg = Color(0xFF14532D)
    val diffRemovedBg = Color(0xFF3B1717)
    val diffRemovedLineBg = Color(0xFF7F1D1D)

    // Thinking text renders in the main text color at reduced opacity.
    val thinking = text.copy(alpha = 0.68f)
}
