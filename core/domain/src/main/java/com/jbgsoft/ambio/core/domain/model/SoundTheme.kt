package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color

enum class SoundTheme(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color
) {
    RAIN(
        primary = Color(0xFF5C7AEA),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8B9DC3),
        background = Color(0xFF1A1F3C),
        surface = Color(0xFF252B4A),
        surfaceVariant = Color(0xFF2E3555)
    ),
    FIREPLACE(
        primary = Color(0xFFE85D04),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFFAA307),
        background = Color(0xFF2D1810),
        surface = Color(0xFF3D2216),
        surfaceVariant = Color(0xFF4D2E1E)
    ),
    FOREST(
        primary = Color(0xFF2D6A4F),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF52B788),
        background = Color(0xFF1B2E1F),
        surface = Color(0xFF243C2A),
        surfaceVariant = Color(0xFF2D4A35)
    ),
    OCEAN(
        primary = Color(0xFF0077B6),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF48CAE4),
        background = Color(0xFF0A1929),
        surface = Color(0xFF132F4C),
        surfaceVariant = Color(0xFF1A3D5C)
    ),
    WIND(
        primary = Color(0xFF7B8794),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFB8C5D0),
        background = Color(0xFF1E2328),
        surface = Color(0xFF2A3038),
        surfaceVariant = Color(0xFF353D47)
    )
}
