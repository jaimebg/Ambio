package com.jbgsoft.ambio.core.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.ui.graphics.vector.ImageVector

data class Sound(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    @RawRes val audioRes: Int,
    @DrawableRes val illustrationRes: Int,
    val theme: SoundTheme
)
