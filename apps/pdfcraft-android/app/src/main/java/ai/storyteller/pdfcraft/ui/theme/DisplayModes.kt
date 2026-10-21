package ai.storyteller.pdfcraft.ui.theme

import ai.storyteller.pdfcraft.model.DisplayMode
import androidx.compose.ui.graphics.Color

data class DisplayModeColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val toolbarBackground: Color,
    val toolbarContent: Color
)

fun getColorsForDisplayMode(mode: DisplayMode): DisplayModeColors {
    return when (mode) {
        DisplayMode.DAY -> DisplayModeColors(
            background = Color(0xFFE0E0E0),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF1E1E1E),
            toolbarBackground = Color(0xFFFFFFFF),
            toolbarContent = Color(0xFF1E1E1E)
        )
        DisplayMode.NIGHT -> DisplayModeColors(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            text = Color(0xFFE0E0E0),
            toolbarBackground = Color(0xFF1E1E1E),
            toolbarContent = Color(0xFFE0E0E0)
        )
        DisplayMode.SEPIA -> DisplayModeColors(
            background = Color(0xFFE8DFC8),
            surface = Color(0xFFFBF0D9),
            text = Color(0xFF382E1E),
            toolbarBackground = Color(0xFFF3E7CA),
            toolbarContent = Color(0xFF382E1E)
        )
        DisplayMode.EINK -> DisplayModeColors(
            background = Color(0xFFF5F5F5),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF000000),
            toolbarBackground = Color(0xFFFFFFFF),
            toolbarContent = Color(0xFF000000)
        )
    }
}
