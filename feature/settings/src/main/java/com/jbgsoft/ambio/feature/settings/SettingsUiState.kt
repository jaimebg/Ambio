package com.jbgsoft.ambio.feature.settings

data class SettingsUiState(
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true
)
