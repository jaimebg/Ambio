# Fase 2 — i18n y accesibilidad: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que ningún string visible por el usuario esté hardcodeado en Kotlin, que las capas de dominio y datos dejen de contener inglés, que la app sea usable con TalkBack, y que los cinco temas cumplan WCAG AA.

**Architecture:** Cada módulo declara sus propios strings, porque `android.nonTransitiveRClass=true` impide compartirlos. `Sound` y `TimerPreset` pasan a llevar `@StringRes`, siguiendo el patrón que `Sound` ya usa con `@RawRes` y `@DrawableRes`. `HomeViewModel` obtiene un `StringProvider` inyectado, porque construye el texto de la notificación multimedia y un ViewModel no puede llamar a `stringResource()`. El contraste se fija con un test que corre en CI, no con una comprobación manual.

**Tech Stack:** Android resources (`strings.xml`, `plurals`), Compose `stringResource`/`pluralStringResource`, Hilt, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-03-i18n-and-accessibility-design.md`

## Global Constraints

- **No se traduce nada.** Sólo se extrae a recursos, en inglés. Las traducciones son
  contribuciones externas y no entran en esta fase.
- **No se toca el modo claro.** La app usa `darkColorScheme` incondicional y sigue igual.
- **No se corrige el `ImageVector` de `Sound.kt`.** Sigue haciendo que `core:domain` dependa
  de Compose; es refactor de producto, fuera de alcance.
- Los 154 tests siguen pasando y el lint sigue en 0 errores tras cada tarea.
- Ninguna versión de dependencia cambia, salvo añadir `libs.bundles.testing` al módulo `ui`
  en la Tarea 6, que no toca `[versions]`.
- Rama de trabajo: `chore/phase-2-i18n-accessibility` (ya creada, contiene el spec).

## File Structure

| Fichero | Responsabilidad | Tarea |
|---|---|---|
| `core/common/.../resources/StringProvider.kt` | Resolver `@StringRes` fuera de Compose | 1 |
| `core/data/src/main/res/values/strings.xml` | Nombres y descripciones de sonidos | 2 |
| `core/domain/.../model/Sound.kt` | `nameRes` / `descriptionRes` | 2 |
| `core/domain/.../model/TimerPreset.kt` | `displayNameRes` | 3 |
| `feature/home/src/main/res/values/strings.xml` | Strings de UI y plurals | 3, 4 |
| `app/src/main/res/values/strings.xml` | Sólo `app_name` | 4 |
| Componentes de `feature/home` | Semántica de accesibilidad | 5 |
| `ui/.../theme/Color.kt` y `Theme.kt` | Paleta y roles de color | 6 |
| `ui/src/test/.../ThemeContrastTest.kt` | Contraste verificado en CI | 6 |

---

### Task 1: StringProvider

`HomeViewModel` construye el texto de la notificación multimedia —`"Focus Timer"`,
`"Ambient Mode"`, `"12:34 remaining"`— y pasa `sound.name` al servicio de audio. Un
ViewModel no puede llamar a `stringResource()`, que es una función de Compose. Necesita una
abstracción inyectable.

**Files:**
- Create: `core/common/src/main/java/com/jbgsoft/ambio/core/common/resources/StringProvider.kt`
- Create: `core/common/src/test/java/com/jbgsoft/ambio/core/common/resources/StringProviderTest.kt`
- Modify: `core/common/build.gradle.kts`

**Interfaces:**
- Consumes: nada.
- Produces: `interface StringProvider { fun get(@StringRes id: Int, vararg args: Any): String }`
  y su implementación `AndroidStringProvider`, vinculada por Hilt. Las tareas 2, 3 y 4 la
  inyectan en `HomeViewModel`.

- [ ] **Step 1: Escribir el test primero**

Crear `core/common/src/test/java/com/jbgsoft/ambio/core/common/resources/StringProviderTest.kt`:

```kotlin
package com.jbgsoft.ambio.core.common.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StringProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = AndroidStringProvider(context)

    @Test
    fun `resolves a simple string resource`() {
        assertThat(provider.get(android.R.string.ok)).isEqualTo("OK")
    }

    @Test
    fun `resolves a formatted string resource with arguments`() {
        // android.R.string.selected_count tiene el formato "%1$d selected"
        val result = provider.get(android.R.string.selected_count, 3)
        assertThat(result).contains("3")
    }
}
```

Se usan recursos de la plataforma (`android.R.string`) a propósito: el test valida el
mecanismo de resolución sin depender de strings del proyecto que aún no existen.

- [ ] **Step 2: Añadir las dependencias de test al módulo**

`core/common/build.gradle.kts` no tiene bloque de test. Añadir al final de `dependencies`:

```kotlin
    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

```bash
./gradlew :core:common:testDebugUnitTest
```

Esperado: FALLA con `Unresolved reference: AndroidStringProvider`.

- [ ] **Step 4: Implementar**

Crear `core/common/src/main/java/com/jbgsoft/ambio/core/common/resources/StringProvider.kt`:

```kotlin
package com.jbgsoft.ambio.core.common.resources

import android.content.Context
import androidx.annotation.StringRes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves string resources outside Compose, where `stringResource()` is unavailable —
 * ViewModels building media notification text, for example.
 */
interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StringProvider {
    override fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StringProviderModule {
    @Binds
    abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider
}
```

`@param:ApplicationContext` lleva el use-site target que Kotlin 2.3 exige, igual que
`HapticManager` y `ChimePlayer` tras la Fase 0.

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

```bash
./gradlew :core:common:testDebugUnitTest
```

Esperado: PASA, 2 tests.

- [ ] **Step 6: Verificar el build completo**

```bash
./gradlew lint test assembleDebug
```

Esperado: verde. El total de tests sube de 154 a **158** (2 tests nuevos × 2 variantes).
A partir de aquí, la línea base de no regresión es 158.

- [ ] **Step 7: Commit**

```bash
git add core/common
git commit -m "feat: add StringProvider for resolving strings outside Compose

HomeViewModel builds the media notification text and cannot call
stringResource(), which is a Compose function. Mirrors how HapticManager
already takes an injected application Context."
```

---

### Task 2: Sonidos a recursos

**Files:**
- Create: `core/data/src/main/res/values/strings.xml`
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/Sound.kt`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImpl.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/CurrentSoundBar.kt:45`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/SoundCard.kt:60,70`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeViewModel.kt:184`
- Modify: `feature/home/src/test/java/com/jbgsoft/ambio/feature/home/HomeViewModelTest.kt:80-81`

**Interfaces:**
- Consumes: `StringProvider` de la Tarea 1.
- Produces: `Sound(id, nameRes: Int, descriptionRes: Int, icon, audioRes, illustrationRes, theme)`.
  Las tareas 4 y 5 leen esos campos con `stringResource(sound.nameRes)`.

- [ ] **Step 1: Crear los recursos de sonidos**

Crear `core/data/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="sound_rain">Rain</string>
    <string name="sound_rain_description">Gentle rain on a window</string>

    <string name="sound_fireplace">Fireplace</string>
    <string name="sound_fireplace_description">Crackling fireplace warmth</string>

    <string name="sound_forest">Forest</string>
    <string name="sound_forest_description">Peaceful forest ambiance</string>

    <string name="sound_ocean">Ocean</string>
    <string name="sound_ocean_description">Calm ocean waves</string>

    <string name="sound_cave">Cave</string>
    <string name="sound_cave_description">Echoing cave ambiance</string>
</resources>
```

Van en `core:data` porque es donde ya viven los demás recursos del sonido: `res/raw/` con
los audios y `res/drawable/` con las ilustraciones.

- [ ] **Step 2: Cambiar el modelo**

En `core/domain/.../model/Sound.kt`, sustituir `name` y `description`:

```kotlin
package com.jbgsoft.ambio.core.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class Sound(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    @RawRes val audioRes: Int,
    @DrawableRes val illustrationRes: Int,
    val theme: SoundTheme
)
```

- [ ] **Step 3: Actualizar el repositorio**

En `core/data/.../SoundRepositoryImpl.kt`, cada entrada pasa de literales a referencias. La
primera queda así, y las otras cuatro siguen el mismo patrón con su propio id:

```kotlin
        Sound(
            id = "rain",
            nameRes = com.jbgsoft.ambio.core.data.R.string.sound_rain,
            descriptionRes = com.jbgsoft.ambio.core.data.R.string.sound_rain_description,
            icon = Icons.Default.WaterDrop,
            audioRes = com.jbgsoft.ambio.core.data.R.raw.rain_loop,
            illustrationRes = com.jbgsoft.ambio.core.data.R.drawable.illustration_rain,
            theme = SoundTheme.RAIN
        ),
```

Los otros cuatro: `sound_fireplace`/`sound_fireplace_description`,
`sound_forest`/`sound_forest_description`, `sound_ocean`/`sound_ocean_description`,
`sound_cave`/`sound_cave_description`.

- [ ] **Step 4: Actualizar los consumidores de UI**

`CurrentSoundBar.kt:45` — `text = sound.name` pasa a:

```kotlin
                    text = stringResource(sound.nameRes),
```

`SoundCard.kt:60` y `:70` — `sound.name` pasa a `stringResource(sound.nameRes)` en ambos.

Añadir en los tres ficheros el import `androidx.compose.ui.res.stringResource`.

- [ ] **Step 5: Actualizar el ViewModel**

`HomeViewModel.kt:184` pasa `sound.name` al servicio de audio. Inyectar `StringProvider` en
el constructor y resolver ahí:

```kotlin
            name = stringProvider.get(sound.nameRes),
```

Añadir al constructor `private val stringProvider: StringProvider,` y el import
`com.jbgsoft.ambio.core.common.resources.StringProvider`.

- [ ] **Step 6: Actualizar el test que construye un Sound**

`HomeViewModelTest.kt:80-81` construye un `Sound` de prueba con `name`/`description`.
Sustituir por los campos nuevos, usando recursos reales de `core:data`:

```kotlin
        nameRes = com.jbgsoft.ambio.core.data.R.string.sound_rain,
        descriptionRes = com.jbgsoft.ambio.core.data.R.string.sound_rain_description,
```

El test también necesita un `StringProvider` de mentira. Añadir junto a los demás mocks:

```kotlin
    private val stringProvider = object : StringProvider {
        override fun get(id: Int, vararg args: Any): String = "test-string-$id"
    }
```

y pasarlo al constructor del ViewModel donde se construye.

Comprobar si `feature/home/build.gradle.kts` ya depende de `:core:data`; si no, añadir
`testImplementation(project(":core:data"))` para que el test vea esos recursos.

- [ ] **Step 7: Verificar**

```bash
./gradlew lint test assembleDebug
```

Esperado: verde, **158 tests**, 0 errores de lint. Y comprobar que el inglés salió de la
capa de datos:

```bash
git grep -nE 'name = "|description = "' -- core/data core/domain && echo "QUEDA INGLES" || echo "limpio"
```

Esperado: `limpio`.

- [ ] **Step 8: Commit**

```bash
git add core feature/home
git commit -m "refactor: move sound names and descriptions to string resources

Sound now carries @StringRes ids, matching the @RawRes and @DrawableRes it
already had. The data layer no longer contains English."
```

---

### Task 3: TimerPreset y los plurals de minutos

Dos strings llevan la unidad incrustada: `"25 min"` en el enum y `"$value $suffix"`
concatenado en el stepper. Ninguno se traduce bien — la unidad cambia y el orden de palabras
también.

**Files:**
- Create: `feature/home/src/main/res/values/strings.xml`
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/TimerPreset.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/TimerPresetSelector.kt:87,112,176,210`

**Interfaces:**
- Consumes: nada de tareas previas.
- Produces: `feature/home/.../res/values/strings.xml`, que la Tarea 4 amplía;
  `TimerPreset(displayNameRes: Int, focusMinutes, breakMinutes)`.

- [ ] **Step 1: Crear el fichero de strings del módulo**

Crear `feature/home/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Timer presets -->
    <string name="preset_25_min">25 min</string>
    <string name="preset_50_min">50 min</string>
    <string name="preset_custom">Custom</string>

    <!-- Duration stepper -->
    <plurals name="duration_minutes">
        <item quantity="one">%1$d min</item>
        <item quantity="other">%1$d min</item>
    </plurals>
</resources>
```

Los presets se quedan como strings fijos —son etiquetas de botón, no cantidades variables—
mientras que el stepper sí varía y necesita `plurals`. En inglés ambas formas coinciden;
en idiomas con más categorías de plural, el traductor tendrá dónde ponerlas.

- [ ] **Step 2: Cambiar el enum**

El enum **no** puede llevar un `@StringRes`: los ids vivirían en el `R` de `feature:home`, y
`core:domain` no depende de ese módulo ni debe hacerlo. La solución correcta es que el
dominio guarde sólo los datos y la presentación decida cómo se llaman.

`core/domain/.../model/TimerPreset.kt` queda así, entero:

```kotlin
package com.jbgsoft.ambio.core.domain.model

enum class TimerPreset(
    val focusMinutes: Int,
    val breakMinutes: Int
) {
    FOCUS_25(25, 5),
    FOCUS_50(50, 10),
    CUSTOM(0, 0)
}
```

**Esta es la forma correcta:** el dominio guarda los datos (minutos) y la presentación
decide cómo se llaman. Es más limpio que meter `@StringRes` en el dominio.

- [ ] **Step 3: Resolver el nombre en la UI**

En `TimerPresetSelector.kt`, añadir una función privada al final del fichero:

```kotlin
@Composable
private fun TimerPreset.label(): String = stringResource(
    when (this) {
        TimerPreset.FOCUS_25 -> R.string.preset_25_min
        TimerPreset.FOCUS_50 -> R.string.preset_50_min
        TimerPreset.CUSTOM -> R.string.preset_custom
    }
)
```

Y en la línea 87, `label = { Text(preset.displayName) }` pasa a:

```kotlin
                        label = { Text(preset.label()) }
```

- [ ] **Step 4: Cambiar el stepper a plurals**

En `TimerPresetSelector.kt:176`, el parámetro `suffix: String` se elimina de la firma del
composable del stepper. En la línea 210, `text = "$value $suffix"` pasa a:

```kotlin
            text = pluralStringResource(R.plurals.duration_minutes, value, value),
```

Y en la línea 112, la llamada que pasaba `suffix = "min",` pierde ese argumento. Buscar
todas las llamadas al stepper y quitarlo de todas:

```bash
grep -n "suffix" feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/TimerPresetSelector.kt
```

Esperado tras el cambio: sin resultados.

Imports nuevos: `androidx.compose.ui.res.stringResource`,
`androidx.compose.ui.res.pluralStringResource` y `com.jbgsoft.ambio.feature.home.R`.

- [ ] **Step 5: Verificar**

```bash
./gradlew lint test assembleDebug
grep -rn "displayName" --include=*.kt core feature && echo "QUEDA displayName" || echo "limpio"
```

Esperado: build verde, **158 tests**, y `limpio`.

- [ ] **Step 6: Commit**

```bash
git add core/domain feature/home
git commit -m "refactor: move timer preset labels and duration text to resources

'25 min' and the concatenated \"\$value \$suffix\" both baked the unit into
the string. TimerPreset now carries only the minutes; the UI decides what
they are called, and the stepper uses plurals."
```

---

### Task 4: Resto de strings de UI y limpieza del strings.xml muerto

**Files:**
- Modify: `feature/home/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `feature/home/.../HomeScreen.kt:200`
- Modify: `feature/home/.../components/CurrentSoundBar.kt:51,59`
- Modify: `feature/home/.../components/PlayPauseButton.kt:46`
- Modify: `feature/home/.../components/SoundBottomSheet.kt:44`
- Modify: `feature/home/.../components/SoundCard.kt:108,114`
- Modify: `feature/home/.../components/TimerPresetSelector.kt:71,128`
- Modify: `feature/home/.../components/VolumeSlider.kt:36,56`
- Modify: `feature/home/.../components/ModeToggle.kt:33-34`
- Modify: `feature/home/.../components/TimerDisplay.kt:56-62`
- Modify: `feature/home/.../HomeViewModel.kt:175-180`

**Interfaces:**
- Consumes: `feature/home/.../strings.xml` de la Tarea 3; `StringProvider` de la Tarea 1.
- Produces: el conjunto completo de strings de UI, que la Tarea 5 reutiliza al ajustar la
  semántica de accesibilidad.

- [ ] **Step 1: Ampliar el fichero de strings**

Añadir a `feature/home/src/main/res/values/strings.xml`, dentro de `<resources>`:

```xml
    <!-- Modes -->
    <string name="mode_timer">Timer</string>
    <string name="mode_ambient">Ambient</string>

    <!-- Timer states -->
    <string name="state_ambient_mode">Ambient Mode</string>
    <string name="state_break_time">Break Time</string>
    <string name="state_focus">Focus</string>
    <string name="state_paused">Paused</string>
    <string name="state_break_over">Break Over!</string>
    <string name="state_completed">Completed!</string>
    <string name="state_ready">Ready</string>

    <!-- Controls -->
    <string name="action_play">Play</string>
    <string name="action_pause">Pause</string>
    <string name="action_reset">Reset timer</string>
    <string name="action_change_sound">Change</string>

    <!-- Sound picker -->
    <string name="sound_picker_title">Select Sound</string>
    <string name="sound_none_selected">No sound selected</string>
    <string name="sound_more_coming">More coming soon</string>

    <!-- Section labels -->
    <string name="label_focus_duration">Focus Duration</string>
    <string name="label_break_duration">Break Duration</string>

    <!-- Media notification -->
    <string name="notification_focus_timer">Focus Timer</string>
    <string name="notification_time_remaining">%1$s remaining</string>
```

- [ ] **Step 2: Sustituir los literales en los componentes**

En cada fichero, cambiar el literal por `stringResource(R.string.<clave>)` según esta tabla,
añadiendo los imports `androidx.compose.ui.res.stringResource` y
`com.jbgsoft.ambio.feature.home.R` donde falten:

| Fichero:línea | Literal | Clave |
|---|---|---|
| `HomeScreen.kt:200` | `"Reset Timer"` | `action_reset` |
| `CurrentSoundBar.kt:51` | `"No sound selected"` | `sound_none_selected` |
| `CurrentSoundBar.kt:59` | `"Change"` | `action_change_sound` |
| `PlayPauseButton.kt:46` | `"Pause"` / `"Play"` | `action_pause` / `action_play` |
| `SoundBottomSheet.kt:44` | `"Select Sound"` | `sound_picker_title` |
| `SoundCard.kt:114` | `"More coming soon"` | `sound_more_coming` |
| `TimerPresetSelector.kt:71` | `"Focus Duration"` | `label_focus_duration` |
| `TimerPresetSelector.kt:128` | `"Break Duration"` | `label_break_duration` |
| `ModeToggle.kt:33` | `"Timer"` | `mode_timer` |
| `ModeToggle.kt:34` | `"Ambient"` | `mode_ambient` |
| `TimerDisplay.kt:56` | `"Ambient Mode"` | `state_ambient_mode` |
| `TimerDisplay.kt:57` | `"Break Time"` | `state_break_time` |
| `TimerDisplay.kt:58` | `"Focus"` | `state_focus` |
| `TimerDisplay.kt:59` | `"Paused"` | `state_paused` |
| `TimerDisplay.kt:60` | `"Break Over!"` | `state_break_over` |
| `TimerDisplay.kt:61` | `"Completed!"` | `state_completed` |
| `TimerDisplay.kt:62` | `"Ready"` | `state_ready` |

`SoundCard.kt:108` (`contentDescription = "More sounds coming soon"`) **no se toca aquí**:
es semántica de accesibilidad y se resuelve en la Tarea 5.

En `TimerDisplay.kt` los literales están dentro de un `when` que devuelve `String`. Como
`stringResource` es `@Composable`, el `when` debe estar en un contexto composable — ya lo
está, porque la función que lo contiene es `@Composable`. Si el compilador se queja, mover
el `when` al cuerpo del composable en vez de a una propiedad calculada.

- [ ] **Step 3: Sustituir los strings del ViewModel**

`HomeViewModel.kt:175-180` construye el texto de la notificación. Sustituir por:

```kotlin
                    stringProvider.get(
                        R.string.notification_time_remaining,
                        "${minutes}:${seconds.toString().padStart(2, '0')}"
                    )
                } else {
                    stringProvider.get(R.string.notification_focus_timer)
                }
            }
            AppMode.AMBIENT -> stringProvider.get(R.string.state_ambient_mode)
```

`stringProvider` ya está inyectado desde la Tarea 2. Añadir el import
`com.jbgsoft.ambio.feature.home.R` si falta.

- [ ] **Step 4: Limpiar el strings.xml muerto**

`app/src/main/res/values/strings.xml` tiene 25 strings de los que sólo `app_name` se usa,
desde el manifest. Sustituir el fichero entero por:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Ambio</string>
</resources>
```

Verificar antes de borrar que nadie más los lee:

```bash
git grep -n "R.string" -- '*.kt' | grep -v "feature/home\|core/common\|core/data" || echo "nadie fuera de los modulos migrados"
grep -rn "@string/" --include=*.xml app core feature media ui
```

Esperado: el segundo comando devuelve **sólo** la línea `android:label="@string/app_name"`
del manifest.

- [ ] **Step 5: Verificar que no queda ningún literal**

```bash
./gradlew lint test assembleDebug
git grep -nE 'text = "|contentDescription = "' -- 'feature/*.kt' 'ui/*.kt' | grep -v Test
```

Esperado: build verde, **158 tests**. El `grep` sólo debe devolver
`SoundCard.kt:108`, que es la Tarea 5.

- [ ] **Step 6: Commit**

```bash
git add app feature/home
git commit -m "refactor: move remaining UI strings to resources

Also removes the 24 dead strings from app's strings.xml. They were
unreachable from the modules that render the UI — app depends on
feature:home, not the other way round, and nonTransitiveRClass is on."
```

---

### Task 5: Semántica de accesibilidad

**Files:**
- Modify: `feature/home/src/main/res/values/strings.xml`
- Modify: `feature/home/.../components/SoundCard.kt:60,108`
- Modify: `feature/home/.../components/TimerPresetSelector.kt:201,235`
- Modify: `feature/home/.../components/VolumeSlider.kt:36,56`
- Modify: `feature/home/.../components/TimerDisplay.kt`

**Interfaces:**
- Consumes: los strings de las tareas 3 y 4.
- Produces: nada que consuman otras tareas.

- [ ] **Step 1: Añadir los strings de accesibilidad**

Añadir a `feature/home/src/main/res/values/strings.xml`:

```xml
    <!-- Accessibility -->
    <string name="a11y_decrease_focus">Decrease focus duration</string>
    <string name="a11y_increase_focus">Increase focus duration</string>
    <string name="a11y_decrease_break">Decrease break duration</string>
    <string name="a11y_increase_break">Increase break duration</string>
    <string name="a11y_volume_slider">Volume</string>
```

- [ ] **Step 2: Dar contexto a los botones del stepper**

Hoy `"Decrease"` y `"Increase"` (`TimerPresetSelector.kt:201,235`) aparecen en **tres**
contextos distintos: duración de foco, de descanso y volumen. Un usuario ciego oye
"disminuir" sin saber qué disminuye.

El composable del stepper recibe dos parámetros nuevos junto a los que ya tiene:

```kotlin
    @StringRes decreaseDescription: Int,
    @StringRes increaseDescription: Int,
```

y los usa en los dos `Icon`:

```kotlin
                contentDescription = stringResource(decreaseDescription),
```
```kotlin
                contentDescription = stringResource(increaseDescription),
```

Las dos llamadas al stepper pasan los suyos: la de foco `a11y_decrease_focus` /
`a11y_increase_focus`, y la de descanso `a11y_decrease_break` / `a11y_increase_break`.

Import nuevo en el fichero: `androidx.annotation.StringRes`.

- [ ] **Step 3: Silenciar los elementos decorativos**

`SoundCard.kt:60` pone `contentDescription = sound.name` sobre la ilustración del sonido,
pero el nombre ya se lee en el `Text` de la línea 70. TalkBack lo anuncia dos veces.
Cambiar a:

```kotlin
                contentDescription = null,
```

`SoundCard.kt:108` (`"More sounds coming soon"`) acompaña a un `Text` con el mismo
significado en la línea 114. Cambiar igualmente a `contentDescription = null`.

`VolumeSlider.kt:36,56` describen los iconos de volumen bajo y alto, que son decorativos:
el control real es el slider. Cambiar ambos a `contentDescription = null` y etiquetar el
slider en su lugar:

```kotlin
        modifier = Modifier.semantics {
            contentDescription = <valor de a11y_volume_slider>
        }
```

resolviendo el string con `stringResource(R.string.a11y_volume_slider)` antes del bloque
`semantics`, ya que dentro no se puede llamar a funciones composable. Imports:
`androidx.compose.ui.semantics.semantics` y `androidx.compose.ui.semantics.contentDescription`.

- [ ] **Step 4: Anunciar los cambios de estado del temporizador**

Pasar de Focus a Break, o llegar a Completed, es el evento central de la app y hoy sólo
existe visualmente. En `TimerDisplay.kt`, el `Text` que muestra el estado recibe:

```kotlin
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
```

`Polite` y no `Assertive` a propósito: el cambio es informativo, no urgente, y no debe
cortar lo que TalkBack esté leyendo.

Imports: `androidx.compose.ui.semantics.semantics`,
`androidx.compose.ui.semantics.liveRegion`, `androidx.compose.ui.semantics.LiveRegionMode`.

- [ ] **Step 5: Comprobar las áreas táctiles**

Material exige 48dp mínimo. Revisar los tamaños declarados:

```bash
grep -rn "\.size(" feature/home/src/main/java/com/jbgsoft/ambio/feature/home/ | grep -vE "iconSize|imageSize"
```

Cualquier elemento **clicable** por debajo de 48dp necesita
`Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` o `Modifier.minimumInteractiveComponentSize()`.
Los iconos decorativos dentro de un botón mayor no cuentan: lo que importa es el área
clicable. Anotar en el informe cuáles se ajustaron y cuáles ya cumplían.

- [ ] **Step 6: Verificar**

```bash
./gradlew lint test assembleDebug
```

Esperado: verde, **158 tests**. El lint de Android incluye comprobaciones de accesibilidad
(`ContentDescription`, `ClickableViewAccessibility`): si aparece alguna nueva, arreglarla.

- [ ] **Step 7: Commit**

```bash
git add feature/home
git commit -m "feat: improve TalkBack semantics

The stepper's Decrease/Increase buttons appear in three contexts and said
only 'Decrease'. Decorative icons no longer duplicate adjacent text, and
timer state changes are announced through a polite live region."
```

---

### Task 6: Contraste, con test que lo vigila

**Files:**
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/SoundTheme.kt`
- Delete: `ui/src/main/java/com/jbgsoft/ambio/ui/theme/Color.kt`
- Modify: `ui/src/main/java/com/jbgsoft/ambio/ui/theme/Theme.kt:55-72`
- Create: `ui/src/test/java/com/jbgsoft/ambio/ui/theme/ThemeContrastTest.kt`
- Modify: `ui/build.gradle.kts`

**Dónde viven los colores, porque hay dos sitios y sólo uno cuenta.** `Theme.kt` lee
`soundTheme.primary`, `soundTheme.background`, etc. del enum `SoundTheme` de `core:domain`.
El fichero `ui/.../theme/Color.kt` es una segunda copia de la paleta que **nadie
referencia** — verificable con `git grep "RainPrimary" -- '*.kt'`, que sólo devuelve su
propia declaración. Sus valores además ya divergen: define Cave en gris `7B8794` mientras el
enum real la define en marrón `6B5B4F`. **Los cambios van en `SoundTheme.kt`; `Color.kt` se
borra.**

**Interfaces:**
- Consumes: nada de tareas previas.
- Produces: un test que falla si alguien rompe el contraste, sustituyendo la comprobación
  manual que pedía el spec.

- [ ] **Step 1: Escribir el test primero**

Crear `ui/src/test/java/com/jbgsoft/ambio/ui/theme/ThemeContrastTest.kt`:

```kotlin
package com.jbgsoft.ambio.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG AA: 3.0 for UI components, 4.5 for normal text.
 * Guards the palette so a future colour tweak cannot silently make the app
 * unreadable — four of five themes failed before Phase 2.
 */
class ThemeContrastTest {

    private fun Color.relativeLuminance(): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `primary is legible against background and surface in every theme`() {
        SoundTheme.entries.forEach { theme ->
            assertThat(contrast(theme.primary, theme.background)).isAtLeast(3.0)
            assertThat(contrast(theme.primary, theme.surface)).isAtLeast(3.0)
        }
    }

    @Test
    fun `onPrimary is legible on primary and on secondary in every theme`() {
        SoundTheme.entries.forEach { theme ->
            assertThat(contrast(theme.onPrimary, theme.primary)).isAtLeast(4.5)
            assertThat(contrast(theme.onPrimary, theme.secondary)).isAtLeast(4.5)
        }
    }

    @Test
    fun `white is legible on the container colour used by Theme`() {
        // Theme.kt maps primaryContainer and secondaryContainer to surfaceVariant,
        // and their on- roles to white. This is the pair that guards that mapping.
        SoundTheme.entries.forEach { theme ->
            assertThat(contrast(Color.White, theme.surfaceVariant)).isAtLeast(4.5)
        }
    }
}
```

- [ ] **Step 2: Añadir dependencias de test al módulo ui**

`ui/build.gradle.kts` no tiene bloque de test. Añadir al final de `dependencies`:

```kotlin
    // Testing
    testImplementation(libs.bundles.testing)
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

```bash
./gradlew :ui:testDebugUnitTest
```

Esperado: FALLA. **Los cinco temas incumplen algo**: Forest 1.87 y Cave 2.36 contra surface;
Rain 3.87 y Fireplace 3.50 en onPrimary; Ocean 2.80 contra surface. Ése es exactamente el
fallo que el test existe para detectar.

- [ ] **Step 4: Corregir la paleta en SoundTheme.kt**

En `core/domain/.../model/SoundTheme.kt`, cada entrada cambia `primary` y `onPrimary`. El
resto de campos —`secondary`, `background`, `surface`, `surfaceVariant`— **no se tocan**.

```kotlin
    RAIN(
        primary = Color(0xFF6481EB),
        onPrimary = Color(0xFF1A1F3C),
```
```kotlin
    FIREPLACE(
        primary = Color(0xFFE85D04),
        onPrimary = Color(0xFF2D1810),
```
```kotlin
    FOREST(
        primary = Color(0xFF44A178),
        onPrimary = Color(0xFF1B2E1F),
```
```kotlin
    OCEAN(
        primary = Color(0xFF0087CE),
        onPrimary = Color(0xFF0A1929),
```
```kotlin
    CAVE(
        primary = Color(0xFF927D6C),
        onPrimary = Color(0xFF1C1816),
```

`FIREPLACE.primary` mantiene su valor: ya cumplía contra fondo y superficie. Lo que cambia en
él es sólo `onPrimary`. En cada tema, el `onPrimary` nuevo **es su propio `background`** — no
es un color inventado, es uno que ya estaba en la paleta.

- [ ] **Step 4b: Borrar la paleta muerta**

```bash
git rm ui/src/main/java/com/jbgsoft/ambio/ui/theme/Color.kt
git grep -n "theme.Color\|RainPrimary\|CavePrimary\|WindPrimary" -- '*.kt'
```

Esperado: el `grep` no devuelve nada. Si devuelve algo, ese fichero sí usaba la paleta
muerta — parar y reportarlo en vez de borrar.

- [ ] **Step 5: Separar los roles de contenedor en Theme.kt**

Éste es el paso que evita una regresión. `Theme.kt:55-72` reutiliza `animatedOnPrimary` en
cuatro roles, pero `primaryContainer` y `secondaryContainer` apuntan a `surfaceVariant`, que
es **oscuro**. Con el `onPrimary` nuevo, esos dos quedarían en oscuro-sobre-oscuro con
contraste de 1.35 a 1.58.

Cambiar sólo esas dos líneas:

```kotlin
        onPrimaryContainer = Color.White,
```
```kotlin
        onSecondaryContainer = Color.White,
```

Blanco sobre `surfaceVariant` da 9.80–12.17 en los cinco temas. Las líneas `onPrimary` y
`onSecondary` se quedan con `animatedOnPrimary`, que ahora es el color oscuro correcto para
los colores claros sobre los que van.

- [ ] **Step 6: Ejecutar el test y verificar que pasa**

```bash
./gradlew :ui:testDebugUnitTest
```

Esperado: PASA, 3 tests.

- [ ] **Step 7: Verificar el build completo**

```bash
./gradlew lint test assembleDebug
```

Esperado: verde. El total sube a **164 tests** (3 nuevos × 2 variantes sobre los 158).

- [ ] **Step 8: Commit**

```bash
git add core/domain ui
git commit -m "fix: make every theme meet WCAG AA contrast

All five themes failed. Forest and Cave were worst, at 1.87 and 2.36 for
primary against surface, making their play buttons nearly invisible.

The fix is mostly in onPrimary. Forcing white there required primaries to
be both light enough to stand out on a dark background and dark enough to
carry white text — a constraint Forest and Cave cannot satisfy at any
lightness. Using each theme's own background colour as onPrimary resolves
it, and only Forest and Cave change visibly.

Also deletes ui/theme/Color.kt, a second copy of the palette that nothing
referenced and whose values had already drifted from the real ones. A unit
test now guards the ratios."
```

---

### Task 7: Auditoría con TalkBack en dispositivo

Ningún test automático comprueba cómo suena la app. Esta tarea la recorre entera con
TalkBack, que es el criterio que el spec pide.

**Files:**
- Ninguno por defecto. Los arreglos que surjan se commitean aparte.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: el veredicto de la fase.

- [ ] **Step 1: Arrancar el emulador API 37 e instalar**

```bash
~/Library/Android/sdk/emulator/emulator -avd Ambio_API37 -no-window -no-snapshot -gpu swiftshader_indirect &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
./gradlew installDebug
```

- [ ] **Step 2: Activar TalkBack**

```bash
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

Si el emulador no trae TalkBack (las imágenes `google_apis` sí, las `default` no),
comprobarlo con:

```bash
adb shell pm list packages | grep -i talkback
```

Si no está, **parar y reportarlo**: la auditoría necesita una imagen con Google APIs.

- [ ] **Step 3: Recorrer la app y capturar lo que anuncia**

Con la app abierta, volcar el árbol de accesibilidad, que es lo que TalkBack lee:

```bash
adb shell uiautomator dump /sdcard/a11y.xml
adb shell cat /sdcard/a11y.xml | tr '>' '>\n' | grep -oE 'content-desc="[^"]*"|text="[^"]*"' | grep -v '=""'
```

Comprobar, uno por uno:

1. Cada control interactivo tiene texto o `content-desc` que dice **qué hace**.
2. Los botones del stepper dicen qué ajustan, no sólo "Decrease"/"Increase".
3. La ilustración del sonido **no** aparece con descripción propia: su nombre ya lo lleva el
   texto de al lado.
4. Los iconos de volumen no aparecen; sí el slider, etiquetado.
5. No hay ningún control con `content-desc` vacío o genérico.

- [ ] **Step 4: Comprobar el anuncio del cambio de estado**

Iniciar un temporizador corto y verificar que el cambio de estado se emite como evento de
accesibilidad:

```bash
adb shell input tap 540 1787
adb logcat -c
sleep 5
adb logcat -d | grep -iE "TYPE_WINDOW_CONTENT_CHANGED|announce|liveRegion" | head -5
```

Esperado: aparecen eventos de contenido cambiado sobre el texto de estado. Si no aparece
nada, el `liveRegion` de la Tarea 5 no está surtiendo efecto.

- [ ] **Step 5: Verificar los cinco temas en pantalla**

Cambiar de sonido y comprobar visualmente que el botón de play y las etiquetas se distinguen
del fondo en los cinco temas — sobre todo en Forest, que es el que más cambia:

```bash
for i in 1 2 3 4 5; do
  adb shell input tap 912 2211   # boton "Change"
  sleep 2
  adb exec-out screencap -p > /tmp/tema-$i.png
  sleep 1
done
```

Revisar las capturas. El test de la Tarea 6 ya garantiza los números; esto confirma que el
resultado se ve bien.

- [ ] **Step 6: Comprobar que la app sigue funcionando**

Esta fase ha tocado el modelo de dominio, el repositorio, el ViewModel y la inyección de
dependencias. Un build verde no prueba que el audio siga sonando — es exactamente lo que
pasó en la Fase 0, donde la app crasheaba al arrancar con el CI en verde.

```bash
adb shell ps -A | grep ambio || echo "PROCESO MUERTO"
adb shell dumpsys activity services com.jbgsoft.ambio | grep -oE "isForeground=true|types=0x[0-9a-f]+"
adb shell dumpsys media_session | grep -oE "state=PLAYING\(3\), position=[0-9]+"
adb logcat -d | grep -E "FATAL|NoSuchMethod|Resources\\\$NotFoundException" | head -5
```

Esperado: proceso vivo, `isForeground=true` con `types=0x00000002`, `state=PLAYING(3)` con la
posición avanzando, y **ningún** `Resources$NotFoundException` — que sería la señal de que
algún `@StringRes` apunta a un recurso que no existe en ese módulo.

- [ ] **Step 7: Apagar el emulador y reportar**

```bash
adb emu kill
```

Documentar en el informe: qué anunció cada control, qué se arregló durante la auditoría (si
algo), y las capturas de los cinco temas. Si algún arreglo requirió tocar código, commitearlo
por separado con su propio mensaje.

---

## Resultado esperado de la fase

| | Antes | Después |
|---|---|---|
| `stringResource` en el proyecto | 0 usos | todos los strings de UI |
| Strings hardcodeados en Kotlin | ~33 | 0 |
| Inglés en dominio y datos | sí | no |
| `app/res/values/strings.xml` | 25 strings, 24 muertos | 1, el que se usa |
| Traducir a un idioma nuevo | tocar código | añadir un fichero |
| Temas que cumplen WCAG AA | 1 de 5 | 5 de 5, con test que lo vigila |
| Tests | 154 | 164 |

Al terminar, la Fase 3 puede añadir pantallas nuevas sabiendo que sus strings van a recursos
desde el principio, y "traduce Ambio al alemán" pasa a ser una contribución que no toca
código.
