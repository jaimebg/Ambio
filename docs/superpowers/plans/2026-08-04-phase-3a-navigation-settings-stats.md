# Fase 3a — Navegación, ajustes y estadísticas: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el usuario vea las sesiones que la app lleva guardando desde el primer commit, pueda apagar los hápticos, el chime y los efectos, y que la app tenga navegación sobre la que las fases siguientes puedan añadir pantallas.

**Architecture:** Dos módulos nuevos hermanos de `feature:home`, con el grafo de navegación en `app`. Los ajustes se guardan en el `UserPreferences` que ya existe y se aplican en el punto donde hoy se dispara cada comportamiento. Las estadísticas no tocan la capa de datos: `SessionRepository` ya expone las cuatro operaciones que hacen falta y sólo se usa una.

**Tech Stack:** Jetpack Compose, Navigation Compose, Hilt, DataStore, Room, JUnit + MockK + Turbine.

**Spec:** `docs/superpowers/specs/2026-08-04-navigation-settings-stats-design.md`

## Global Constraints

- **El número de tests nunca baja.** Parte de **164** y crece; cada tarea indica el número que debe ver.
- **Lint: 0 errores** tras cada tarea, medido sobre los ficheros `lint-results-*.xml`, nunca sobre el log de consola.
- **Warnings de compilador Kotlin: 2.** *(Corregido durante la Tarea 3: eran 3, y el tercero
  —la deprecación de `hiltViewModel()` en `HomeScreen.kt`— se cerró al descubrir que su
  reemplazo vive en `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0`, artefacto aparte
  de la misma versión. Era deuda diferida desde la Fase 1.)* Los dos que quedan están en
  `ui/theme/Theme.kt:79` y `:80` — `statusBarColor` y `navigationBarColor` deprecados, de otra
  naturaleza y fuera del alcance de esta fase. No se añade ninguno y no se arregla ninguno.
  Sólo se ven con `clean` + `--no-build-cache`.
- **Usa `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`**, no el de
  `androidx.hilt.navigation.compose`, que está deprecado. La dependencia del catálogo es
  `libs.hilt.lifecycle.viewmodel.compose`.
- **Ningún string hardcodeado.** Todo texto visible va a `strings.xml` del módulo que lo pinta, como estableció la Fase 2. Los nuevos módulos crean el suyo.
- **`navigation-compose` se declara en el catálogo en la versión `2.9.8`.** Hoy llega transitivamente en la 2.9.0 vía `hilt-navigation-compose`, y usar su API sin declararla deja su versión en manos de otra dependencia.
- No se toca `AudioService` ni la capa de audio: eso es la Fase 3b.
- Rama de trabajo: `chore/phase-3a-navigation-settings-stats` (ya creada, contiene el spec).

## File Structure

| Fichero | Responsabilidad | Tarea |
|---|---|---|
| `core/domain/.../model/UserPreferences.kt` | Tres booleanos nuevos | 1 |
| `core/domain/.../repository/PreferencesRepository.kt` | Tres setters nuevos | 1 |
| `core/data/.../datastore/PreferencesDataStore.kt` | Tres claves y sus setters | 1 |
| `core/data/.../repository/PreferencesRepositoryImpl.kt` | Delegación | 1 |
| `feature/home/.../HomeUiState.kt` | Tres booleanos en el estado | 2 |
| `feature/home/.../HomeViewModel.kt` | Puerta de hápticos y chime | 2 |
| `feature/home/.../HomeScreen.kt` | Puerta de efectos | 2 |
| `feature/settings/**` | Módulo nuevo: pantalla de ajustes | 3 |
| `core/domain/.../usecase/GetSessionHistoryUseCase.kt` | Historial | 4 |
| `feature/stats/**` | Módulo nuevo: pantalla de estadísticas | 4 |
| `app/.../AmbioApp.kt` | Grafo de navegación y tema | 5 |
| `app/.../MainActivity.kt` | Monta `AmbioApp` | 5 |

---

### Task 1: Los tres ajustes en la capa de datos

**Files:**
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/UserPreferences.kt`
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/repository/PreferencesRepository.kt`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/datastore/PreferencesDataStore.kt`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/repository/PreferencesRepositoryImpl.kt`
- Test: `core/data/src/test/java/com/jbgsoft/ambio/core/data/datastore/PreferencesDataStoreTest.kt` (nuevo)

**Interfaces:**
- Consumes: nada.
- Produces: `UserPreferences.hapticsEnabled`, `.chimeEnabled`, `.effectsEnabled`, los tres `Boolean` con defecto `true`; y en `PreferencesRepository` los métodos `suspend fun setHapticsEnabled(enabled: Boolean)`, `setChimeEnabled(enabled: Boolean)`, `setEffectsEnabled(enabled: Boolean)`. Las tareas 2 y 3 los consumen.

- [ ] **Step 1: Escribir el test primero**

Crear `core/data/src/test/java/com/jbgsoft/ambio/core/data/datastore/PreferencesDataStoreTest.kt`:

```kotlin
package com.jbgsoft.ambio.core.data.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferencesDataStore(context)

    @Test
    fun `the three toggles default to enabled`() = runTest {
        val prefs = dataStore.preferences.first()
        assertThat(prefs.hapticsEnabled).isTrue()
        assertThat(prefs.chimeEnabled).isTrue()
        assertThat(prefs.effectsEnabled).isTrue()
    }

    @Test
    fun `disabling haptics persists and leaves the others alone`() = runTest {
        dataStore.setHapticsEnabled(false)

        val prefs = dataStore.preferences.first()
        assertThat(prefs.hapticsEnabled).isFalse()
        assertThat(prefs.chimeEnabled).isTrue()
        assertThat(prefs.effectsEnabled).isTrue()
    }

    @Test
    fun `disabling chime does not disturb session state`() = runTest {
        dataStore.setVolume(0.42f)
        dataStore.setChimeEnabled(false)

        val prefs = dataStore.preferences.first()
        assertThat(prefs.chimeEnabled).isFalse()
        assertThat(prefs.volume).isEqualTo(0.42f)
    }
}
```

El tercer test es el que importa más de los tres: `UserPreferences` mezcla estado de sesión
con preferencias, y comprueba que escribir una no pisa la otra.

`@Config(sdk = [34])` es necesario: Robolectric 4.16.1 soporta hasta API 36 y el módulo
compila contra 37. Es el mismo pin que usa `StringProviderTest` en `core:common`.

- [ ] **Step 2: Ejecutar el test y verificar que falla**

```bash
./gradlew :core:data:testDebugUnitTest > /tmp/t1.log 2>&1; echo "EXIT=$?"
grep -E "Unresolved reference|FAILURE|BUILD" /tmp/t1.log | head -5
```

Esperado: FALLA con `Unresolved reference: hapticsEnabled`.

- [ ] **Step 3: Añadir los campos al modelo**

`core/domain/.../model/UserPreferences.kt` queda entero así:

```kotlin
package com.jbgsoft.ambio.core.domain.model

data class UserPreferences(
    // Session state — where the user left off
    val lastSoundId: String = "rain",
    val volume: Float = 0.7f,
    val lastTimerMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val lastMode: AppMode = AppMode.TIMER,
    // Preferences — how the user wants the app to behave
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true
)
```

Los dos comentarios no son decoración: marcan el corte que la clase va a necesitar cuando
alguien añada más campos, y que el spec dejó anotado explícitamente.

- [ ] **Step 4: Añadir las claves y setters al DataStore**

En `PreferencesDataStore.kt`, añadir el import `androidx.datastore.preferences.core.booleanPreferencesKey`, y dentro de `PreferencesKeys`:

```kotlin
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val CHIME_ENABLED = booleanPreferencesKey("chime_enabled")
        val EFFECTS_ENABLED = booleanPreferencesKey("effects_enabled")
```

En el `map` que construye `UserPreferences`, añadir tras `lastMode`:

```kotlin
            hapticsEnabled = prefs[PreferencesKeys.HAPTICS_ENABLED] ?: true,
            chimeEnabled = prefs[PreferencesKeys.CHIME_ENABLED] ?: true,
            effectsEnabled = prefs[PreferencesKeys.EFFECTS_ENABLED] ?: true
```

Y tres setters nuevos, siguiendo el patrón exacto de los que ya están:

```kotlin
    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setChimeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CHIME_ENABLED] = enabled
        }
    }

    suspend fun setEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.EFFECTS_ENABLED] = enabled
        }
    }
```

- [ ] **Step 5: Extender el repositorio**

En `core/domain/.../repository/PreferencesRepository.kt`, añadir al final de la interfaz:

```kotlin
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setChimeEnabled(enabled: Boolean)
    suspend fun setEffectsEnabled(enabled: Boolean)
```

Y en `core/data/.../repository/PreferencesRepositoryImpl.kt`, las tres delegaciones,
siguiendo el patrón de los métodos que ya tiene:

```kotlin
    override suspend fun setHapticsEnabled(enabled: Boolean) =
        preferencesDataStore.setHapticsEnabled(enabled)

    override suspend fun setChimeEnabled(enabled: Boolean) =
        preferencesDataStore.setChimeEnabled(enabled)

    override suspend fun setEffectsEnabled(enabled: Boolean) =
        preferencesDataStore.setEffectsEnabled(enabled)
```

- [ ] **Step 6: Ejecutar el test y verificar que pasa**

```bash
./gradlew :core:data:testDebugUnitTest > /tmp/t1.log 2>&1; echo "EXIT=$?"
grep -E "^BUILD" /tmp/t1.log
```

Esperado: `BUILD SUCCESSFUL`, 3 tests nuevos.

- [ ] **Step 7: Verificar el build completo**

```bash
./gradlew clean lint test assembleDebug > /tmp/t1full.log 2>&1; echo "EXIT=$?"
```

Esperado: verde. El total de tests sube de 164 a **170** (3 nuevos × 2 variantes).

- [ ] **Step 8: Commit**

```bash
git add core/domain core/data
git commit -m "feat: persist haptics, chime and effects preferences

UserPreferences now carries the three toggles, defaulting to enabled so
existing installs behave identically. The comments mark the seam between
session state and preferences, which have different lifecycles."
```

---

### Task 2: Aplicar los tres ajustes

Los ajustes ya se guardan; esta tarea hace que surtan efecto. Nada los puede cambiar todavía
—la pantalla es la Tarea 3— así que en la práctica siguen en `true`, pero el camino queda
cerrado y testeado.

**Files:**
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeUiState.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeViewModel.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeScreen.kt:70`
- Test: `feature/home/src/test/java/com/jbgsoft/ambio/feature/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `UserPreferences.hapticsEnabled`, `.chimeEnabled`, `.effectsEnabled` de la Tarea 1.
- Produces: `HomeUiState.hapticsEnabled`, `.chimeEnabled`, `.effectsEnabled`.

- [ ] **Step 1: Escribir los tests primero**

Añadir a `HomeViewModelTest.kt` estos dos tests. Usan el mismo estilo que los que ya hay en
el fichero:

```kotlin
    @Test
    fun `no haptic feedback fires when haptics are disabled`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(
            UserPreferences(hapticsEnabled = false)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        verify(exactly = 0) { hapticManager.heavyClick() }
        verify(exactly = 0) { hapticManager.click() }
    }

    @Test
    fun `haptic feedback fires when haptics are enabled`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(
            UserPreferences(hapticsEnabled = true)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        verify(atLeast = 1) { hapticManager.heavyClick() }
        verify(atLeast = 1) { hapticManager.click() }
    }
```

**Por qué dos eventos y no uno.** `playPause()` llama sólo a `heavyClick()` y `setMode()`
sólo a `click()`. Con un único evento, una de las dos aserciones de "no se dispara" sería
cierta de todos modos —el método nunca llama a ese háptico— y no probaría nada sobre la
puerta. Disparando ambos, las cuatro aserciones son significativas y la puerta queda probada
sobre dos tipos de háptico distintos.

**Y por qué el par de tests.** El primero solo comprueba que algo no ocurre, y pasaría
también si los eventos estuvieran rotos y no hicieran nada. El segundo demuestra que por ese
mismo camino, con el ajuste activo, sí ocurre — que es lo que convierte al primero en una
prueba real.

`HomeEvent.SetMode` toma un `AppMode`; si el nombre del evento o su parámetro no coincidiera
con el del fichero, usa el que exista y dilo en el informe.

El fichero ya tiene `private fun createViewModel(): HomeViewModel` en la línea 180, y los
mocks se llaman exactamente `hapticManager` y `preferencesRepository`. Los dos tests encajan
tal cual.

- [ ] **Step 2: Ejecutar los tests y verificar que fallan**

```bash
./gradlew :feature:home:testDebugUnitTest --tests "*HomeViewModelTest*" > /tmp/t2.log 2>&1; echo "EXIT=$?"
grep -E "Unresolved reference|FAILED|BUILD" /tmp/t2.log | head -5
```

Esperado: FALLA — `hapticsEnabled` no existe aún en `UserPreferences` desde la perspectiva
del test, o el primer test falla porque el háptico sí se dispara.

- [ ] **Step 3: Llevar los tres ajustes al estado de UI**

En `HomeUiState.kt`, añadir al final de la data class:

```kotlin
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true
```

En `HomeViewModel`, donde ya se observan las preferencias para copiar `volume`,
`breakMinutes` y `lastMode` al estado, copiar también los tres nuevos. Busca el bloque que
hace `_uiState.update { it.copy(volume = prefs.volume, ...) }` y añade:

```kotlin
                        hapticsEnabled = prefs.hapticsEnabled,
                        chimeEnabled = prefs.chimeEnabled,
                        effectsEnabled = prefs.effectsEnabled
```

- [ ] **Step 4: Poner la puerta a los hápticos**

Hay **17 llamadas** a `hapticManager` en `HomeViewModel` (8 `click`, 4 `heavyClick`,
3 `tick`, 2 `timerComplete`). Poner un `if` en cada una sería ruidoso y fácil de olvidar en
la siguiente. En su lugar, añadir un helper privado al final de la clase:

```kotlin
    private fun haptic(action: HapticManager.() -> Unit) {
        if (_uiState.value.hapticsEnabled) hapticManager.action()
    }
```

y sustituir cada llamada `hapticManager.click()` por `haptic { click() }`, `hapticManager.heavyClick()`
por `haptic { heavyClick() }`, y así con las cuatro formas. Verifica que no queda ninguna
directa:

```bash
grep -n "hapticManager\." feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeViewModel.kt
```

Esperado tras el cambio: **una sola línea**, la del propio helper.

- [ ] **Step 5: Poner la puerta al chime**

`HomeViewModel.kt:317` llama a `chimePlayer.playChime(...)`. Envolverlo:

```kotlin
        if (_uiState.value.chimeEnabled) {
            chimePlayer.playChime(chimeRepository.getTimerChimeResource())
        }
```

- [ ] **Step 6: Poner la puerta a los efectos**

`HomeScreen.kt:70` renderiza `AmbientEffectsOverlay(...)` incondicionalmente. Envolverlo:

```kotlin
                if (uiState.effectsEnabled) {
                    AmbientEffectsOverlay(
                        isPlaying = uiState.isPlaying,
                        soundTheme = uiState.selectedSound?.theme ?: SoundTheme.RAIN
                    )
                }
```

- [ ] **Step 7: Ejecutar los tests y verificar el build**

```bash
./gradlew clean lint test assembleDebug > /tmp/t2full.log 2>&1; echo "EXIT=$?"
```

Esperado: verde, **174 tests** (2 nuevos × 2 variantes sobre los 170), 0 errores de lint.

- [ ] **Step 8: Commit**

```bash
git add feature/home
git commit -m "feat: honour the haptics, chime and effects preferences

The haptics gate is a single private helper rather than seventeen ifs, so
the next call site cannot forget it."
```

---

### Task 3: Módulo y pantalla de ajustes

**Files:**
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/settings/src/main/res/values/strings.xml`
- Create: `feature/settings/src/main/java/com/jbgsoft/ambio/feature/settings/SettingsUiState.kt`
- Create: `feature/settings/src/main/java/com/jbgsoft/ambio/feature/settings/SettingsViewModel.kt`
- Create: `feature/settings/src/main/java/com/jbgsoft/ambio/feature/settings/SettingsScreen.kt`
- Create: `feature/settings/src/test/java/com/jbgsoft/ambio/feature/settings/SettingsViewModelTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `PreferencesRepository.preferences` y los tres setters de la Tarea 1.
- Produces: `@Composable fun SettingsScreen(onNavigateBack: () -> Unit)`, que la Tarea 5 registra en el grafo.

- [ ] **Step 1: Declarar el módulo**

En `settings.gradle.kts`, añadir junto a los `include` existentes:

```kotlin
include(":feature:settings")
```

Crear `feature/settings/build.gradle.kts`:

```kotlin
plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.feature.settings"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":ui"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Hilt
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)
}
```

Tres líneas de `plugins`, que era el objetivo declarado de la Fase 0. Ésta es la primera vez
que se comprueba.

- [ ] **Step 2: Escribir el test primero**

Crear `feature/settings/src/test/java/com/jbgsoft/ambio/feature/settings/SettingsViewModelTest.kt`:

```kotlin
package com.jbgsoft.ambio.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state reflects the stored preferences`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(
            UserPreferences(hapticsEnabled = false, chimeEnabled = true, effectsEnabled = false)
        )

        val viewModel = SettingsViewModel(preferencesRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.hapticsEnabled).isFalse()
            assertThat(state.chimeEnabled).isTrue()
            assertThat(state.effectsEnabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling haptics writes through to the repository`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(UserPreferences())

        val viewModel = SettingsViewModel(preferencesRepository)
        advanceUntilIdle()

        viewModel.onHapticsChanged(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferencesRepository.setHapticsEnabled(false) }
    }
}
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

```bash
./gradlew :feature:settings:testDebugUnitTest > /tmp/t3.log 2>&1; echo "EXIT=$?"
grep -E "Unresolved reference|FAILURE|BUILD" /tmp/t3.log | head -5
```

Esperado: FALLA con `Unresolved reference: SettingsViewModel`.

- [ ] **Step 4: Escribir el estado y el ViewModel**

Crear `SettingsUiState.kt`:

```kotlin
package com.jbgsoft.ambio.feature.settings

data class SettingsUiState(
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true
)
```

Crear `SettingsViewModel.kt`:

```kotlin
package com.jbgsoft.ambio.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.value = SettingsUiState(
                    hapticsEnabled = prefs.hapticsEnabled,
                    chimeEnabled = prefs.chimeEnabled,
                    effectsEnabled = prefs.effectsEnabled
                )
            }
        }
    }

    fun onHapticsChanged(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun onChimeChanged(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setChimeEnabled(enabled) }
    }

    fun onEffectsChanged(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setEffectsEnabled(enabled) }
    }
}
```

El estado no se actualiza de forma optimista: se escribe en el repositorio y el `collect`
devuelve el valor nuevo. Una sola fuente de verdad, y si la escritura falla la UI no miente.

- [ ] **Step 5: Escribir los strings**

Crear `feature/settings/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="settings_title">Settings</string>
    <string name="settings_back">Back</string>

    <!-- Toggles -->
    <string name="settings_haptics">Haptic feedback</string>
    <string name="settings_haptics_summary">Vibrate on taps and timer events</string>
    <string name="settings_chime">Completion chime</string>
    <string name="settings_chime_summary">Play a sound when a focus session ends</string>
    <string name="settings_effects">Ambient effects</string>
    <string name="settings_effects_summary">Animated particles behind the timer</string>
</resources>
```

- [ ] **Step 6: Escribir la pantalla**

Crear `SettingsScreen.kt`:

```kotlin
package com.jbgsoft.ambio.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                onCheckedChange = viewModel::onHapticsChanged
            )
            SettingRow(
                title = stringResource(R.string.settings_chime),
                summary = stringResource(R.string.settings_chime_summary),
                checked = uiState.chimeEnabled,
                onCheckedChange = viewModel::onChimeChanged
            )
            SettingRow(
                title = stringResource(R.string.settings_effects),
                summary = stringResource(R.string.settings_effects_summary),
                checked = uiState.effectsEnabled,
                onCheckedChange = viewModel::onEffectsChanged
            )
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
```

El `Switch` ya expone su estado a TalkBack por sí solo, y el texto de al lado le da el
nombre: no hace falta un `contentDescription` que duplicaría lo que ya se lee, que es la
regla que estableció la Fase 2.

- [ ] **Step 7: Ejecutar los tests y verificar el build**

```bash
./gradlew clean lint test assembleDebug > /tmp/t3full.log 2>&1; echo "EXIT=$?"
```

Esperado: verde, **178 tests** (2 nuevos × 2 variantes sobre los 174), 0 errores de lint.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts feature/settings
git commit -m "feat: add the settings screen

First new module since the convention plugins landed — three lines of
plugins, which is what Phase 0 set out to make possible."
```

---

### Task 4: Módulo y pantalla de estadísticas

**Files:**
- Create: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetSessionHistoryUseCase.kt`
- Create: `feature/stats/build.gradle.kts`
- Create: `feature/stats/src/main/res/values/strings.xml`
- Create: `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsUiState.kt`
- Create: `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsViewModel.kt`
- Create: `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsScreen.kt`
- Create: `feature/stats/src/test/java/com/jbgsoft/ambio/feature/stats/StatsViewModelTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `SessionRepository.getAllSessions()`, `.deleteSession(id)`, `GetSessionStatsUseCase`, y `SoundRepository.getSoundById(id): Sound?`.
- Produces: `@Composable fun StatsScreen(onNavigateBack: () -> Unit)`, que la Tarea 5 registra en el grafo.

- [ ] **Step 1: Declarar el módulo**

En `settings.gradle.kts`:

```kotlin
include(":feature:stats")
```

Crear `feature/stats/build.gradle.kts`, idéntico al de `feature:settings` salvo el namespace:

```kotlin
plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.feature.stats"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":ui"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Hilt
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)
}
```

- [ ] **Step 2: Escribir el test primero**

El caso que más importa es el del sonido desconocido. **No es hipotético:** en este mismo
repositorio el sonido "Wind" se renombró a "Cave", y cualquier sesión guardada antes de ese
cambio tiene un `soundId` que `getSoundById` ya no resuelve.

Crear `feature/stats/src/test/java/com/jbgsoft/ambio/feature/stats/StatsViewModelTest.kt`:

```kotlin
package com.jbgsoft.ambio.feature.stats

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.usecase.GetSessionHistoryUseCase
import com.jbgsoft.ambio.core.domain.usecase.GetSessionStatsUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val soundRepository: SoundRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val session = Session(
        id = 1L,
        soundId = "wind",
        durationMinutes = 25,
        completedAt = 1_700_000_000_000L,
        wasCompleted = true
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(): StatsViewModel {
        every { sessionRepository.getAllSessions() } returns flowOf(listOf(session))
        every { sessionRepository.getTotalFocusMinutes() } returns flowOf(25)
        every { sessionRepository.getCompletedSessionCount() } returns flowOf(1)
        return StatsViewModel(
            getSessionStats = GetSessionStatsUseCase(sessionRepository),
            getSessionHistory = GetSessionHistoryUseCase(sessionRepository),
            sessionRepository = sessionRepository,
            soundRepository = soundRepository
        )
    }

    @Test
    fun `a session whose sound no longer exists carries a null name resource`() = runTest {
        every { soundRepository.getSoundById("wind") } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.sessions).hasSize(1)
            assertThat(state.sessions.first().soundNameRes).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals come through from the stats use case`() = runTest {
        every { soundRepository.getSoundById(any()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.totalFocusMinutes).isEqualTo(25)
            assertThat(state.completedSessionCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a session reaches the repository`() = runTest {
        every { soundRepository.getSoundById(any()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDeleteSession(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { sessionRepository.deleteSession(1L) }
    }
}
```

`soundId = "wind"` no es un valor inventado para el test: es literalmente el id que existía
antes del renombrado.

- [ ] **Step 3: Ejecutar el test y verificar que falla**

```bash
./gradlew :feature:stats:testDebugUnitTest > /tmp/t4.log 2>&1; echo "EXIT=$?"
grep -E "Unresolved reference|FAILURE|BUILD" /tmp/t4.log | head -5
```

Esperado: FALLA con `Unresolved reference: StatsViewModel`.

- [ ] **Step 4: Escribir el caso de uso del historial**

Crear `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetSessionHistoryUseCase.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<List<Session>> = sessionRepository.getAllSessions()
}
```

Es un envoltorio fino a propósito: todos los ViewModels del proyecto hablan con casos de uso,
y uno que fuera directo al repositorio rompería el patrón que un contribuidor espera.

- [ ] **Step 5: Escribir el estado**

Crear `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsUiState.kt`:

```kotlin
package com.jbgsoft.ambio.feature.stats

import androidx.annotation.StringRes

data class SessionRow(
    val id: Long,
    @param:StringRes val soundNameRes: Int?,
    val durationMinutes: Int,
    val completedAt: Long,
    val wasCompleted: Boolean
)

data class StatsUiState(
    val totalFocusMinutes: Int = 0,
    val completedSessionCount: Int = 0,
    val sessions: List<SessionRow> = emptyList()
)
```

`soundNameRes` es **nullable a propósito**: `SoundRepository.getSoundById` devuelve
`Sound?`, y una sesión de un sonido retirado no tiene nombre que mostrar. La pantalla decide
qué poner en su lugar; el estado no inventa un id que no existe.

- [ ] **Step 6: Escribir el ViewModel**

Crear `StatsViewModel.kt`:

```kotlin
package com.jbgsoft.ambio.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.usecase.GetSessionHistoryUseCase
import com.jbgsoft.ambio.core.domain.usecase.GetSessionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getSessionStats: GetSessionStatsUseCase,
    private val getSessionHistory: GetSessionHistoryUseCase,
    private val sessionRepository: SessionRepository,
    private val soundRepository: SoundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getSessionStats(), getSessionHistory()) { stats, sessions ->
                StatsUiState(
                    totalFocusMinutes = stats.totalFocusMinutes,
                    completedSessionCount = stats.completedSessionCount,
                    sessions = sessions.map { session ->
                        SessionRow(
                            id = session.id,
                            soundNameRes = soundRepository.getSoundById(session.soundId)?.nameRes,
                            durationMinutes = session.durationMinutes,
                            completedAt = session.completedAt,
                            wasCompleted = session.wasCompleted
                        )
                    }
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onDeleteSession(id: Long) {
        viewModelScope.launch { sessionRepository.deleteSession(id) }
    }
}
```

- [ ] **Step 7: Escribir los strings**

Crear `feature/stats/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="stats_title">Statistics</string>
    <string name="stats_back">Back</string>

    <!-- Totals -->
    <string name="stats_total_focus">Total focus time</string>
    <string name="stats_sessions_completed">Sessions completed</string>
    <plurals name="stats_minutes">
        <item quantity="one">%1$d min</item>
        <item quantity="other">%1$d min</item>
    </plurals>

    <!-- History -->
    <string name="stats_history">History</string>
    <string name="stats_empty">No sessions yet. Finish a focus session and it will show up here.</string>
    <string name="stats_unknown_sound">Removed sound</string>
    <string name="stats_delete_session">Delete session</string>
</resources>
```

`stats_unknown_sound` es el texto de reserva del criterio 4 del spec.

- [ ] **Step 8: Escribir la pantalla**

Crear `StatsScreen.kt`:

```kotlin
package com.jbgsoft.ambio.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.stats_back)
                    )
                }
                Text(
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Total(
                    label = stringResource(R.string.stats_total_focus),
                    value = pluralStringResource(
                        R.plurals.stats_minutes,
                        uiState.totalFocusMinutes,
                        uiState.totalFocusMinutes
                    )
                )
                Total(
                    label = stringResource(R.string.stats_sessions_completed),
                    value = uiState.completedSessionCount.toString()
                )
            }

            Text(
                text = stringResource(R.string.stats_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            if (uiState.sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.sessions, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            onDelete = { viewModel.onDeleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionItem(session: SessionRow, onDelete: () -> Unit) {
    val soundName = session.soundNameRes
        ?.let { stringResource(it) }
        ?: stringResource(R.string.stats_unknown_sound)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = soundName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(session.completedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.stats_minutes,
                session.durationMinutes,
                session.durationMinutes
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.stats_delete_session)
            )
        }
    }
}
```

`DateFormat.getDateTimeInstance` respeta el locale del dispositivo por sí solo, así que la
fecha se localiza sin trabajo extra.

- [ ] **Step 9: Ejecutar los tests y verificar el build**

```bash
./gradlew clean lint test assembleDebug > /tmp/t4full.log 2>&1; echo "EXIT=$?"
```

Esperado: verde, **184 tests** (3 nuevos × 2 variantes sobre los 178), 0 errores de lint.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts core/domain feature/stats
git commit -m "feat: add the statistics screen

The data layer was already complete — SessionRepository exposed four
operations and only saveSession was ever called. Sessions recorded since
the first commit are finally visible.

A session whose sound was later removed shows a fallback rather than
crashing; 'wind' became 'cave' in this repo's own history."
```

---

### Task 5: Navegación y tema izado

**Files:**
- Modify: `gradle/libs.versions.toml`
- Create: `app/src/main/java/com/jbgsoft/ambio/AmbioApp.kt`
- Modify: `app/src/main/java/com/jbgsoft/ambio/MainActivity.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `SettingsScreen(onNavigateBack)` de la Tarea 3 y `StatsScreen(onNavigateBack)` de la Tarea 4.
- Produces: la app navegable. Nada posterior lo consume.

- [ ] **Step 1: Declarar `navigation-compose` en el catálogo**

Hoy la 2.9.0 llega transitiva vía `hilt-navigation-compose`, así que el `NavHost` compilaría
sin tocar nada — y eso es justo lo que hay que evitar: la versión la decidiría otra
dependencia. Verifícalo antes:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>/dev/null | grep -o "navigation-compose:[0-9.]*" | sort -u
```

En `gradle/libs.versions.toml`, bloque `[versions]`:

```toml
navigation-compose = "2.9.8"
```

En `[libraries]`:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
```

Y en `app/build.gradle.kts`, junto a las demás dependencias de AndroidX:

```kotlin
    implementation(libs.androidx.navigation.compose)
```

Tras el cambio, el mismo comando debe mostrar `2.9.8`.

- [ ] **Step 2: Añadir los módulos nuevos a `app`**

En `app/build.gradle.kts`, en el bloque de módulos de proyecto:

```kotlin
    implementation(project(":feature:settings"))
    implementation(project(":feature:stats"))
```

- [ ] **Step 3: Escribir el composable raíz**

Crear `app/src/main/java/com/jbgsoft/ambio/AmbioApp.kt`:

```kotlin
package com.jbgsoft.ambio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jbgsoft.ambio.feature.home.HomeScreen
import com.jbgsoft.ambio.feature.settings.SettingsScreen
import com.jbgsoft.ambio.feature.stats.StatsScreen
import com.jbgsoft.ambio.ui.theme.AmbioTheme

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STATS = "stats"
}

@Composable
fun AmbioApp(viewModel: AmbioAppViewModel = hiltViewModel()) {
    val soundTheme by viewModel.soundTheme.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // The theme wraps the whole graph, not just Home, so Settings and Stats
    // inherit the palette of the selected sound.
    AmbioTheme(soundTheme = soundTheme) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToStats = { navController.navigate(Routes.STATS) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.STATS) {
                StatsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
```

Crear también `app/src/main/java/com/jbgsoft/ambio/AmbioAppViewModel.kt`:

```kotlin
package com.jbgsoft.ambio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.usecase.GetSelectedSoundUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AmbioAppViewModel @Inject constructor(
    getSelectedSound: GetSelectedSoundUseCase
) : ViewModel() {

    val soundTheme: StateFlow<SoundTheme> = getSelectedSound()
        .map { it.theme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SoundTheme.RAIN
        )
}
```

**Por qué hace falta este ViewModel.** El tema depende del sonido seleccionado, y hasta ahora
sólo `HomeScreen` lo conocía. Para que Ajustes y Estadísticas hereden la paleta, alguien por
encima del `NavHost` tiene que observarlo.

`GetSelectedSoundUseCase` tiene ya la firma `operator fun invoke(): Flow<Sound>`, así que
encaja tal cual.

- [ ] **Step 4: Montar el composable raíz**

`app/src/main/java/com/jbgsoft/ambio/MainActivity.kt` queda entero así:

```kotlin
package com.jbgsoft.ambio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmbioApp()
        }
    }
}
```

- [ ] **Step 5: Quitar el tema de `HomeScreen` y añadir los accesos**

En `HomeScreen.kt`, la firma pasa a:

```kotlin
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
```

**Quitar el envoltorio `AmbioTheme(soundTheme = ...) { ... }`** que hoy rodea todo el
contenido (`HomeScreen.kt:61`), dejando el `Surface` como raíz. El import de `AmbioTheme`
sobra; el de `SoundTheme` puede seguir haciendo falta para `AmbientEffectsOverlay`.

Añadir los dos accesos. Van dentro del `Column` desplazable, **antes** del `ModeToggle`, como
una fila alineada a la derecha:

```kotlin
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = onNavigateToStats) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = stringResource(R.string.action_open_stats)
                                    )
                                }
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.action_open_settings)
                                    )
                                }
                            }
```

Imports nuevos: `androidx.compose.material.icons.filled.BarChart`,
`androidx.compose.material.icons.filled.Settings`, `androidx.compose.material3.IconButton`.

Añadir a `feature/home/src/main/res/values/strings.xml`, en la sección de controles:

```xml
    <string name="action_open_settings">Settings</string>
    <string name="action_open_stats">Statistics</string>
```

- [ ] **Step 6: Verificar**

```bash
./gradlew clean lint test assembleDebug > /tmp/t5.log 2>&1; echo "EXIT=$?"
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>/dev/null | grep -o "navigation-compose:[0-9.]*" | sort -u
```

Esperado: verde, **184 tests** (esta tarea no añade ninguno), 0 errores de lint, y
`navigation-compose:2.9.8`.

Y comprobar el criterio 6 del spec — que esta fase no haya reintroducido strings
hardcodeados, deshaciendo lo que consiguió la Fase 2. Ojo con el patrón: usa **ambas**
formas, porque la Fase 2 se dejó un literal precisamente por buscar sólo `text = "`, que es
ciego a los argumentos posicionales:

```bash
git grep -nE 'Text\("|text = "|contentDescription = "' -- 'feature/*.kt' 'app/*.kt' 'ui/*.kt' | grep -v Test
```

Esperado: **cero resultados**.

Si algún test de `HomeViewModelTest` fallara por el cambio de firma de `HomeScreen`, revisa:
los tests son del ViewModel, no de la pantalla, así que no deberían verse afectados. Si lo
están, algo más cambió — repórtalo.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app feature/home
git commit -m "feat: add navigation between home, settings and statistics

The theme moves from HomeScreen up to wrap the whole graph, so the other
two screens inherit the selected sound's palette instead of falling back to
the Material default.

navigation-compose was already on the classpath transitively at 2.9.0 via
hilt-navigation-compose; it is now declared explicitly at 2.9.8 rather than
letting another dependency choose its version."
```

---

### Task 6: Verificación en dispositivo

Ningún test comprueba que la app navegue, que los ajustes sobrevivan a un reinicio o que las
estadísticas muestren sesiones reales. Esta tarea lo hace.

**Files:**
- Ninguno por defecto. Lo que surja se commitea aparte.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: el veredicto de la fase.

- [ ] **Step 1: Arrancar el emulador e instalar**

El AVD `Ambio_API37` ya existe de fases anteriores.

```bash
nohup ~/Library/Android/sdk/emulator/emulator -avd Ambio_API37 -no-window -no-snapshot -gpu swiftshader_indirect > /tmp/emu.log 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
adb uninstall com.jbgsoft.ambio 2>/dev/null
./gradlew installDebug
adb shell am start -n com.jbgsoft.ambio/.MainActivity
sleep 8
```

- [ ] **Step 2: Comprobar que la app sigue funcionando**

Esta fase ha tocado el ViewModel principal, el estado de UI y el arranque de la Activity.

```bash
adb shell ps -A | grep ambio || echo "PROCESO MUERTO"
adb logcat -d | grep -E "FATAL|Resources\\\$NotFoundException|IllegalState" | head -5
```

Esperado: proceso vivo, sin excepciones.

- [ ] **Step 3: Navegar a las tres pantallas**

Localiza los dos iconos nuevos y púlsalos:

```bash
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb shell cat /sdcard/ui.xml | tr '>' '>\n' | grep -oE 'content-desc="(Settings|Statistics)"[^>]*bounds="[^"]*"'
```

Pulsa el centro de cada uno, comprueba que la pantalla cambia, y vuelve con el botón atrás.
Confirma que **las tres pantallas usan la paleta del sonido seleccionado** y no el gris por
defecto de Material — es el criterio 1 del spec y lo que justifica haber izado el tema.

- [ ] **Step 4: Comprobar que los ajustes persisten**

En Ajustes, apaga los tres interruptores. Después:

```bash
adb shell am force-stop com.jbgsoft.ambio
adb shell am start -n com.jbgsoft.ambio/.MainActivity
sleep 6
```

Vuelve a Ajustes y confirma que los tres siguen apagados. Vuelve a Home y confirma que **los
efectos de partículas ya no se dibujan**, que es el ajuste con efecto visible inmediato.

- [ ] **Step 5: Comprobar las estadísticas con datos reales**

Con la app recién instalada la base de datos está vacía, así que la pantalla debe mostrar el
texto de "aún no hay sesiones". Para generar una sesión real, pon un temporizador
personalizado de 1 minuto, déjalo terminar, y vuelve a Estadísticas: debe aparecer una
entrada con su sonido, duración y fecha. Bórrala y confirma que desaparece.

Si el temporizador de 1 minuto no fuera alcanzable desde la UI, dilo en el informe en vez de
manipular la base de datos a mano.

- [ ] **Step 6: Apagar el emulador y reportar**

```bash
adb emu kill
```

Documenta en el informe: capturas de las tres pantallas, el resultado de cada comprobación,
y cualquier arreglo que hayas tenido que commitear por separado.

---

## Resultado esperado de la fase

| | Antes | Después |
|---|---|---|
| Pantallas | 1 | 3, con navegación |
| `SessionRepository` | 1 de 5 operaciones usadas | 4 de 5 |
| Sesiones guardadas | invisibles | visibles y borrables |
| Hápticos, chime, efectos | siempre activos | configurables y persistentes |
| Módulos `feature` | 1 | 3 |
| Tests | 164 | 184 |

Al terminar, la Fase 3b puede abordar el mezclador sobre una app que ya tiene dónde poner
sus controles.
