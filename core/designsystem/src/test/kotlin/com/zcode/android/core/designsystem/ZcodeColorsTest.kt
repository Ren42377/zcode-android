package com.zcode.android.core.designsystem

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

// Pins the color tokens of the ZCode terminal theme so accidental edits fail the build.
class ZcodeColorsTest {
    @Test
    fun colorTokensMatchTheZCodeTheme() {
        assertEquals(0xFF0F1419.toInt(), ZcodeColors.bg.toArgb())
        assertEquals(0xFF161B22.toInt(), ZcodeColors.panel.toArgb())
        assertEquals(0xFF1F2937.toInt(), ZcodeColors.element.toArgb())
        assertEquals(0xFF30363D.toInt(), ZcodeColors.userMessage.toArgb())
        assertEquals(0xFFE5E7EB.toInt(), ZcodeColors.text.toArgb())
        assertEquals(0xFF94A3B8.toInt(), ZcodeColors.muted.toArgb())
        assertEquals(0xFF7DD3FC.toInt(), ZcodeColors.primary.toArgb())
        assertEquals(0xFFC4B5FD.toInt(), ZcodeColors.secondary.toArgb())
        assertEquals(0xFFFCA5A5.toInt(), ZcodeColors.error.toArgb())
        assertEquals(0xFFFBBF24.toInt(), ZcodeColors.warning.toArgb())
        assertEquals(0xFF86EFAC.toInt(), ZcodeColors.success.toArgb())
        assertEquals(0xFF93C5FD.toInt(), ZcodeColors.info.toArgb())
        assertEquals(0xFF3B4450.toInt(), ZcodeColors.border.toArgb())
        assertEquals(0xFF26313D.toInt(), ZcodeColors.borderSubtle.toArgb())
        assertEquals(0xFF12351E.toInt(), ZcodeColors.diffAddedBg.toArgb())
        assertEquals(0xFF14532D.toInt(), ZcodeColors.diffAddedLineBg.toArgb())
        assertEquals(0xFF3B1717.toInt(), ZcodeColors.diffRemovedBg.toArgb())
        assertEquals(0xFF7F1D1D.toInt(), ZcodeColors.diffRemovedLineBg.toArgb())
    }
}
