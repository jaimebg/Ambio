package com.jbgsoft.ambio.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbgsoft.ambio.ui.layout.CappedWidthContainer

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onHapticsChanged = viewModel::onHapticsChanged,
        onChimeChanged = viewModel::onChimeChanged,
        onEffectsChanged = viewModel::onEffectsChanged,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onHapticsChanged: (Boolean) -> Unit,
    onChimeChanged: (Boolean) -> Unit,
    onEffectsChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CappedWidthContainer { columnModifier ->
            Column(
                modifier = columnModifier
                    .fillMaxHeight()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settingsHeader")
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                SettingRow(
                    title = stringResource(R.string.settings_haptics),
                    summary = stringResource(R.string.settings_haptics_summary),
                    checked = uiState.hapticsEnabled,
                    onCheckedChange = onHapticsChanged
                )
                SettingRow(
                    title = stringResource(R.string.settings_chime),
                    summary = stringResource(R.string.settings_chime_summary),
                    checked = uiState.chimeEnabled,
                    onCheckedChange = onChimeChanged
                )
                SettingRow(
                    title = stringResource(R.string.settings_effects),
                    summary = stringResource(R.string.settings_effects_summary),
                    checked = uiState.effectsEnabled,
                    onCheckedChange = onEffectsChanged
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
