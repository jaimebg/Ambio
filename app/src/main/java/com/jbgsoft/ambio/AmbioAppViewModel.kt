package com.jbgsoft.ambio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.model.ActiveSound
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

    /**
     * The mix itself, from the same use case rather than derived from [palette]: two
     * different mixes can share a palette but have different titles, and the widget's
     * title has to follow the mix, not the colour.
     */
    val activeMix: StateFlow<List<ActiveSound>> = getActiveMix()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
