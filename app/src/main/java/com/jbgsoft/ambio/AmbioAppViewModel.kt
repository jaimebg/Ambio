package com.jbgsoft.ambio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import com.jbgsoft.ambio.core.domain.model.toPalette
import com.jbgsoft.ambio.core.domain.usecase.GetActiveMixUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AmbioAppViewModel @Inject constructor(
    getActiveMix: GetActiveMixUseCase
) : ViewModel() {

    val palette: StateFlow<AmbioPalette> = getActiveMix()
        .map { mix -> mixPalettes(mix.map { it.sound.theme }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SoundTheme.RAIN.toPalette()
        )
}
