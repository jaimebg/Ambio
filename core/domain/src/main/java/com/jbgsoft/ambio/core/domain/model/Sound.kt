package com.jbgsoft.ambio.core.domain.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class Sound(
    val id: String,
    @param:StringRes val nameRes: Int,
    val icon: ImageVector,
    @param:RawRes val audioRes: Int,
    val theme: SoundTheme,
    val glow: SoundGlow
)
