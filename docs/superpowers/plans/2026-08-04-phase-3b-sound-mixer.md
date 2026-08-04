# Phase 3b — Multi-sound mixer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play up to five ambient sounds simultaneously, each with its own level, with a mixed palette that stays WCAG AA across all 31 possible mixes — and fix the bug that loses the selected sound on restart.

**Architecture:** One `ExoPlayer` per active sound, wrapped by a `MixPlayer : SimpleBasePlayer` that delegates to none of them and publishes one synthetic `MediaItem` to the `MediaSession`. Per-sound commands reach the service through Media3 custom session commands carrying primitives. Persistence widens a single id into a comma-joined list, which is a backward-compatible superset, so neither Room nor DataStore migrates.

**Tech Stack:** Kotlin 2.3.21, Media3 1.10.1 (`SimpleBasePlayer`, custom `SessionCommand`), Hilt 2.60.1, Compose BOM 2026.06.01, Room 2.8.4, DataStore, MockK, Truth, Robolectric 4.16.1.

**Spec:** `docs/superpowers/specs/2026-08-04-sound-mixer-design.md`

## Global Constraints

- **The DataStore key string stays `"last_sound_id"`.** The Kotlin field renames to `lastMix`; the key does not. Changing it compiles and silently wipes every user's stored mix.
- **The Room column stays `soundId: String`.** No schema change, no migration, no `@Database` version bump.
- **`media` must not depend on `core:domain`.** Custom commands carry primitives only: an opaque id string, an `@RawRes` int, and a display title.
- **The active mix is never empty.** Deactivating the last active sound is a no-op enforced in `SoundRepositoryImpl`, not only in the UI.
- **Colour ignores volume.** The palette is a function of *which* sounds are active, never of their levels.
- **Single-sound palettes must not change.** The five hand-tuned `SoundTheme` values stay byte-identical; the derivation rules apply only when two or more sounds are active.
- **`Math.round` is half-up.** Banker's rounding shifts two of the 31 palettes by one point per channel. All tabulated hex values in this plan assume half-up.
- **No hardcoded user-facing strings.** New strings go to the owning module's `strings.xml`. Verify with `grep -rnE 'Text\("|text = "|contentDescription = "'`.
- **Lint stays at 0 errors. Kotlin compiler warnings stay at 2** (the Phase 3a baseline). Do not introduce a third.
- **Test baseline: 92 `@Test` methods = 184 executed tests** (debug + release variants both run; `android.onlyEnableUnitTestForTheTestedBuildType=false` is deliberate). The count must only go up.
- Build: `./gradlew assembleDebug` · Lint: `./gradlew lint` · Tests: `./gradlew test`

---

## File Structure

**New files**

| File | Responsibility |
|---|---|
| `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/ActiveSound.kt` | A sound plus its own level |
| `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/MixCodec.kt` | The comma-joined string format, both directions |
| `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/AmbioPalette.kt` | The six colour roles, and the mixing rules |
| `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetActiveMixUseCase.kt` | Replaces `GetSelectedSoundUseCase` |
| `media/src/main/java/com/jbgsoft/ambio/media/MixCommands.kt` | Custom session command names and bundle keys |
| `media/src/main/java/com/jbgsoft/ambio/media/SoundTrack.kt` | Narrow interface over one looping sound, plus its ExoPlayer implementation |
| `media/src/main/java/com/jbgsoft/ambio/media/MixPlayer.kt` | `SimpleBasePlayer` presenting N tracks as one logical playback |

**Deleted**

| File | Why |
|---|---|
| `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/SelectSoundUseCase.kt` | Dead: defined, never called. `HomeViewModel` inlines the same two calls. |
| `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetSelectedSoundUseCase.kt` | Superseded by `GetActiveMixUseCase` |

**Modified** — `SoundRepository`, `SoundRepositoryImpl`, `UserPreferences`, `PreferencesDataStore`, `PreferencesRepository(+Impl)`, `SoundTheme`, `Theme.kt`, `ThemeContrastTest`, `AmbioAppViewModel`, `HomeViewModel`, `HomeUiState`, `HomeEvent`, `SoundBottomSheet`, `SoundCard`, `CurrentSoundBar`, `AudioService`, `AudioServiceConnection`, `StatsViewModel`, `StatsUiState`, `StatsScreen`, and two `strings.xml`.

---

## Task 1: Fix the selected-sound persistence bug

Ships on its own, before any mixer work. `getSelectedSound()` is removed in Task 5 and these three lines are rewritten — the durable deliverable is **the test**, which does not exist today and is what let the bug through.

**Files:**
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImpl.kt:67-82`
- Create: `core/data/src/test/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImplTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing later tasks depend on. `SoundRepositoryImpl(preferencesDataStore: PreferencesDataStore)` keeps its constructor.

- [ ] **Step 1: Write the failing test**

Create `core/data/src/test/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImplTest.kt`:

```kotlin
package com.jbgsoft.ambio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SoundRepositoryImplTest {

    private fun repositoryStoring(lastSoundId: String): SoundRepositoryImpl {
        val dataStore = mockk<PreferencesDataStore>()
        every { dataStore.preferences } returns flowOf(UserPreferences(lastSoundId = lastSoundId))
        return SoundRepositoryImpl(dataStore)
    }

    @Test
    fun `selected sound comes from the stored preference when nothing was selected yet`() = runTest {
        val repository = repositoryStoring("forest")

        assertThat(repository.getSelectedSound().first().id).isEqualTo("forest")
    }

    @Test
    fun `an explicit selection wins over the stored preference`() = runTest {
        val repository = repositoryStoring("forest")

        repository.setSelectedSound("ocean")

        assertThat(repository.getSelectedSound().first().id).isEqualTo("ocean")
    }

    @Test
    fun `an unknown stored id falls back to the first sound`() = runTest {
        val repository = repositoryStoring("wind")

        assertThat(repository.getSelectedSound().first().id).isEqualTo("rain")
    }
}
```

- [ ] **Step 2: Run the test and confirm the first one fails**

```bash
./gradlew :core:data:testDebugUnitTest --tests '*SoundRepositoryImplTest*'
```

Expected: `selected sound comes from the stored preference when nothing was selected yet` FAILS with `expected: forest / but was: rain`. The other two pass. That failure **is** the bug — do not proceed until you have seen it.

- [ ] **Step 3: Fix the repository**

In `SoundRepositoryImpl.kt`, replace lines 67 and 73-82 so the flow starts empty and the stored preference is reachable:

```kotlin
    private val selectedSoundIdFlow = MutableStateFlow<String?>(null)

    override fun getAllSounds(): List<Sound> = sounds

    override fun getSoundById(id: String): Sound? = sounds.find { it.id == id }

    override fun getSelectedSound(): Flow<Sound> = combine(
        selectedSoundIdFlow,
        preferencesDataStore.preferences
    ) { currentId, prefs ->
        currentId?.let { getSoundById(it) }
            ?: getSoundById(prefs.lastSoundId)
            ?: sounds.first()
    }

    override suspend fun setSelectedSound(soundId: String) {
        selectedSoundIdFlow.value = soundId
    }
```

- [ ] **Step 4: Run the tests and confirm all three pass**

```bash
./gradlew :core:data:testDebugUnitTest --tests '*SoundRepositoryImplTest*'
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImpl.kt \
        core/data/src/test/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImplTest.kt
git commit -m "fix: restore the selected sound on a cold start

The state flow seeded itself with the literal \"rain\", so the fallback to
the stored preference behind it was unreachable and lastSoundId was written
without ever being read. Phase 3a amplified this: since the theme wraps the
navigation graph, all three screens came up blue instead of just home."
```

---

## Task 2: The mix codec

**Files:**
- Create: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/ActiveSound.kt`
- Create: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/MixCodec.kt`
- Create: `core/domain/src/test/java/com/jbgsoft/ambio/core/domain/model/MixCodecTest.kt`

**Interfaces:**
- Consumes: `Sound(id, nameRes, icon, audioRes, illustrationRes, theme)` from `core.domain.model`.
- Produces:
  - `data class ActiveSound(val sound: Sound, val level: Float)`
  - `MixCodec.decode(encoded: String, allSounds: List<Sound>): List<ActiveSound>` — never returns empty; falls back to the first sound at level 1.0
  - `MixCodec.encode(mix: List<ActiveSound>, withLevels: Boolean): String`

- [ ] **Step 1: Write the failing test**

Create `core/domain/src/test/java/com/jbgsoft/ambio/core/domain/model/MixCodecTest.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MixCodecTest {

    // Ids and order mirror SoundRepositoryImpl; the resource ids are irrelevant here.
    private val sounds = listOf("rain", "fireplace", "forest", "ocean", "cave")
        .mapIndexed { index, id ->
            Sound(
                id = id,
                nameRes = index,
                icon = Icons.Default.WaterDrop,
                audioRes = index,
                illustrationRes = index,
                theme = SoundTheme.entries[index]
            )
        }

    private fun decode(encoded: String) = MixCodec.decode(encoded, sounds)

    @Test
    fun `a bare id is read as a single sound at full level`() {
        val mix = decode("rain")

        assertThat(mix.map { it.sound.id }).containsExactly("rain")
        assertThat(mix.single().level).isEqualTo(1.0f)
    }

    @Test
    fun `several ids without levels are all read at full level`() {
        val mix = decode("rain,fireplace")

        assertThat(mix.map { it.sound.id }).containsExactly("rain", "fireplace").inOrder()
        assertThat(mix.map { it.level }).containsExactly(1.0f, 1.0f)
    }

    @Test
    fun `levels are read when present`() {
        val mix = decode("rain:1.0,fireplace:0.6")

        assertThat(mix.map { it.level }).containsExactly(1.0f, 0.6f).inOrder()
    }

    @Test
    fun `ids are emitted in the canonical order regardless of activation order`() {
        val mix = decode("cave,rain")

        assertThat(MixCodec.encode(mix, withLevels = false)).isEqualTo("rain,cave")
    }

    @Test
    fun `unknown ids are discarded without dropping the rest`() {
        val mix = decode("rain,wind,ocean")

        assertThat(mix.map { it.sound.id }).containsExactly("rain", "ocean").inOrder()
    }

    @Test
    fun `a string with no usable id falls back to the first sound`() {
        val mix = decode("wind,thunder")

        assertThat(mix.map { it.sound.id }).containsExactly("rain")
        assertThat(mix.single().level).isEqualTo(1.0f)
    }

    @Test
    fun `an empty string falls back to the first sound`() {
        assertThat(decode("").map { it.sound.id }).containsExactly("rain")
    }

    @Test
    fun `levels outside zero to one are clamped when decoded`() {
        val mix = decode("rain:2.5,fireplace:-0.4")

        assertThat(mix.map { it.level }).containsExactly(1.0f, 0.0f).inOrder()
    }

    @Test
    fun `a malformed level falls back to full`() {
        val mix = decode("rain:loud")

        assertThat(mix.single().level).isEqualTo(1.0f)
    }

    @Test
    fun `encoding with levels uses two decimals`() {
        val mix = listOf(ActiveSound(sounds[0], 1.0f), ActiveSound(sounds[1], 0.6f))

        assertThat(MixCodec.encode(mix, withLevels = true)).isEqualTo("rain:1.00,fireplace:0.60")
    }

    @Test
    fun `encoding without levels emits bare ids`() {
        val mix = listOf(ActiveSound(sounds[0], 0.6f))

        assertThat(MixCodec.encode(mix, withLevels = false)).isEqualTo("rain")
    }

    @Test
    fun `encode then decode round-trips`() {
        val original = decode("rain:0.30,ocean:0.75")

        val roundTripped = decode(MixCodec.encode(original, withLevels = true))

        assertThat(roundTripped.map { it.sound.id }).isEqualTo(original.map { it.sound.id })
        assertThat(roundTripped.map { it.level }).isEqualTo(original.map { it.level })
    }

    @Test
    fun `a duplicated id is kept once`() {
        val mix = decode("rain,rain:0.2")

        assertThat(mix.map { it.sound.id }).containsExactly("rain")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:domain:testDebugUnitTest --tests '*MixCodecTest*'
```

Expected: compilation failure — `Unresolved reference: MixCodec` and `ActiveSound`.

- [ ] **Step 3: Write the implementation**

Create `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/ActiveSound.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.model

/**
 * A sound that is currently part of the mix, with its own level.
 * The master volume is applied separately, in the media layer.
 */
data class ActiveSound(
    val sound: Sound,
    val level: Float
)
```

Create `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/MixCodec.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.model

import java.util.Locale

/**
 * The storage format for a mix, shared by DataStore and Room.
 *
 * A comma-joined list of ids is a backward-compatible superset of a single id:
 * "rain" (the format written before the mixer existed) still reads as a mix of
 * one, so neither store needs a migration. Levels are an optional ":level"
 * suffix — Room omits them, DataStore writes them.
 */
object MixCodec {

    private const val DEFAULT_LEVEL = 1.0f

    /**
     * Never returns an empty list: a string with no usable id falls back to the
     * first sound, which is what [List.first] did before the mixer.
     */
    fun decode(encoded: String, allSounds: List<Sound>): List<ActiveSound> {
        val levelsById = encoded.split(',')
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val id = trimmed.substringBefore(':')
                val level = trimmed.substringAfter(':', "")
                    .toFloatOrNull()
                    ?.coerceIn(0f, 1f)
                    ?: DEFAULT_LEVEL
                id to level
            }
            .toMap()

        val mix = allSounds
            .filter { levelsById.containsKey(it.id) }
            .map { ActiveSound(it, levelsById.getValue(it.id)) }

        return mix.ifEmpty { listOf(ActiveSound(allSounds.first(), DEFAULT_LEVEL)) }
    }

    /**
     * Emits ids in the order of the sound list, not the order they were activated,
     * so the same mix always produces the same string.
     */
    fun encode(mix: List<ActiveSound>, withLevels: Boolean): String =
        mix.joinToString(",") { active ->
            if (withLevels) {
                "${active.sound.id}:${String.format(Locale.ROOT, "%.2f", active.level)}"
            } else {
                active.sound.id
            }
        }
}
```

> `decode` filters `allSounds` rather than iterating the parsed segments, which is what gives canonical ordering and de-duplication for free. `Locale.ROOT` matters: with a comma decimal separator, `"%.2f"` would emit `0,60` and split the segment in two.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :core:domain:testDebugUnitTest --tests '*MixCodecTest*'
```

Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/ActiveSound.kt \
        core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/MixCodec.kt \
        core/domain/src/test/java/com/jbgsoft/ambio/core/domain/model/MixCodecTest.kt
git commit -m "feat: add the mix storage format

A comma-joined id list is a backward-compatible superset of a single id, so
the existing DataStore key and Room column carry a mix without a migration."
```

---

## Task 3: Palette mixing, and a contrast test that stops sampling

**Files:**
- Create: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/AmbioPalette.kt`
- Modify: `ui/src/main/java/com/jbgsoft/ambio/ui/theme/Theme.kt:20-53`
- Modify: `ui/src/test/java/com/jbgsoft/ambio/ui/theme/ThemeContrastTest.kt`
- Modify: `app/src/main/java/com/jbgsoft/ambio/AmbioAppViewModel.kt` — call-site only, so the module still compiles

**Interfaces:**
- Consumes: `SoundTheme` (unchanged values), `ActiveSound` from Task 2.
- Produces:
  - `data class AmbioPalette(primary, onPrimary, secondary, background, surface, surfaceVariant: Color)`
  - `fun SoundTheme.toPalette(): AmbioPalette`
  - `fun mixPalettes(themes: List<SoundTheme>): AmbioPalette` — single-element input returns that theme's palette unchanged
  - `AmbioTheme(palette: AmbioPalette = SoundTheme.RAIN.toPalette(), content: @Composable () -> Unit)`

- [ ] **Step 1: Write the failing test**

Replace the whole of `ui/src/test/java/com/jbgsoft/ambio/ui/theme/ThemeContrastTest.kt`:

```kotlin
package com.jbgsoft.ambio.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import com.jbgsoft.ambio.core.domain.model.toPalette
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG AA: 3.0 for UI components, 4.5 for normal text.
 *
 * Because the mix ignores per-sound volume, the palette space is finite — the
 * 31 non-empty subsets of five sounds — so this test enumerates all of them
 * instead of sampling. A weighted mix would make the space continuous and only
 * sampling would be possible.
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

    /** All 31 non-empty subsets of the five themes, each with a readable label. */
    private fun allMixes(): List<Pair<String, AmbioPalette>> {
        val themes = SoundTheme.entries
        return (1 until (1 shl themes.size)).map { bits ->
            val subset = themes.filterIndexed { index, _ -> (bits shr index) and 1 == 1 }
            subset.joinToString("+") { it.name } to mixPalettes(subset)
        }
    }

    @Test
    fun `there are exactly 31 mixes`() {
        assertThat(allMixes()).hasSize(31)
    }

    @Test
    fun `primary is legible against background and surface in every mix`() {
        allMixes().forEach { (label, palette) ->
            assertWithMessage("%s: primary on background", label)
                .that(contrast(palette.primary, palette.background)).isAtLeast(3.0)
            assertWithMessage("%s: primary on surface", label)
                .that(contrast(palette.primary, palette.surface)).isAtLeast(3.0)
        }
    }

    @Test
    fun `onPrimary is legible on primary and on secondary in every mix`() {
        allMixes().forEach { (label, palette) ->
            assertWithMessage("%s: onPrimary on primary", label)
                .that(contrast(palette.onPrimary, palette.primary)).isAtLeast(4.5)
            assertWithMessage("%s: onPrimary on secondary", label)
                .that(contrast(palette.onPrimary, palette.secondary)).isAtLeast(4.5)
        }
    }

    @Test
    fun `white is legible on the container colour used by Theme in every mix`() {
        // Theme.kt maps primaryContainer and secondaryContainer to surfaceVariant,
        // and their on- roles to white. This is the pair that guards that mapping.
        allMixes().forEach { (label, palette) ->
            assertWithMessage("%s: white on surfaceVariant", label)
                .that(contrast(Color.White, palette.surfaceVariant)).isAtLeast(4.5)
        }
    }

    @Test
    fun `a single sound keeps its hand-tuned palette untouched`() {
        SoundTheme.entries.forEach { theme ->
            assertWithMessage("%s must not be altered by the mixing rules", theme.name)
                .that(mixPalettes(listOf(theme))).isEqualTo(theme.toPalette())
        }
    }

    @Test
    fun `mixing is independent of the order the sounds were activated`() {
        val forwards = mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE))
        val backwards = mixPalettes(listOf(SoundTheme.FIREPLACE, SoundTheme.RAIN))

        assertThat(forwards).isEqualTo(backwards)
    }

    @Test
    fun `known mixes produce the tabulated colours`() {
        // Half-up rounding. These exact values are in the spec; if they change,
        // the spec's table is wrong too.
        val rainFire = mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE))
        assertThat(rainFire.primary).isEqualTo(Color(0xFFA66F78))
        assertThat(rainFire.onPrimary).isEqualTo(Color.Black)
        assertThat(rainFire.background).isEqualTo(Color(0xFF241C26))

        val fireOcean = mixPalettes(listOf(SoundTheme.FIREPLACE, SoundTheme.OCEAN))
        assertThat(fireOcean.primary).isEqualTo(Color(0xFF77756D))

        val everything = mixPalettes(SoundTheme.entries)
        assertThat(everything.primary).isEqualTo(Color(0xFF6D8187))
        assertThat(everything.background).isEqualTo(Color(0xFF1B1E22))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :ui:testDebugUnitTest --tests '*ThemeContrastTest*'
```

Expected: compilation failure — `Unresolved reference: AmbioPalette`, `mixPalettes`, `toPalette`.

- [ ] **Step 3: Write the palette and the mixing rules**

Create `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/AmbioPalette.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/** The six colour roles the app themes, independent of where they came from. */
data class AmbioPalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color
)

fun SoundTheme.toPalette(): AmbioPalette = AmbioPalette(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant
)

private const val TEXT_CONTRAST = 4.5
private const val ADJUST_STEP = 0.01f
private const val MAX_ADJUST_STEPS = 100

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
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Per-channel arithmetic mean, half-up, in 8-bit space. */
private fun averageOf(colors: List<Color>): Color {
    fun mean(channel: (Color) -> Float): Int =
        (colors.sumOf { (channel(it) * 255f).roundToInt() }.toFloat() / colors.size).roundToInt()
    return Color(mean { it.red }, mean { it.green }, mean { it.blue })
}

private fun Color.lightenOneStep(): Color = Color(
    (red * 255f + (255f - red * 255f) * ADJUST_STEP).roundToInt(),
    (green * 255f + (255f - green * 255f) * ADJUST_STEP).roundToInt(),
    (blue * 255f + (255f - blue * 255f) * ADJUST_STEP).roundToInt()
)

private fun Color.darkenOneStep(): Color = Color(
    (red * 255f * (1f - ADJUST_STEP)).roundToInt(),
    (green * 255f * (1f - ADJUST_STEP)).roundToInt(),
    (blue * 255f * (1f - ADJUST_STEP)).roundToInt()
)

private fun Color.adjustedUntilLegible(against: Color): Color {
    var result = this
    var steps = 0
    while (contrast(against, result) < TEXT_CONTRAST && steps < MAX_ADJUST_STEPS) {
        result = if (against == Color.Black) result.lightenOneStep() else result.darkenOneStep()
        steps++
    }
    return result
}

/**
 * A single sound keeps its hand-tuned palette untouched. Two or more average all
 * six roles per channel, ignoring volume — so the colour changes only when a sound
 * is added or removed, never when a slider moves, which is what keeps the palette
 * space finite and exhaustively testable.
 *
 * The on- colours are the exception: averaging them is what made 26 of the 31
 * mixes fail WCAG AA. Each theme's onPrimary is a tinted background, and their
 * mean lands too light against a primary that has drifted to mid-tone. They are
 * derived instead, and the primary and secondary are nudged if that is still short.
 */
fun mixPalettes(themes: List<SoundTheme>): AmbioPalette {
    require(themes.isNotEmpty()) { "The active mix is never empty" }
    if (themes.size == 1) return themes.single().toPalette()

    var primary = averageOf(themes.map { it.primary })
    var secondary = averageOf(themes.map { it.secondary })

    val on = listOf(Color.White, Color.Black).maxByOrNull { candidate ->
        minOf(contrast(candidate, primary), contrast(candidate, secondary))
    } ?: Color.Black

    primary = primary.adjustedUntilLegible(against = on)
    secondary = secondary.adjustedUntilLegible(against = on)

    return AmbioPalette(
        primary = primary,
        onPrimary = on,
        secondary = secondary,
        background = averageOf(themes.map { it.background }),
        surface = averageOf(themes.map { it.surface }),
        surfaceVariant = averageOf(themes.map { it.surfaceVariant })
    )
}
```

> `Color(Int, Int, Int)` takes 0-255 components; `Color.red/green/blue` return 0f-1f floats. Every conversion back to 8-bit goes through `roundToInt()`, which is half-up — the spec's tabulated hex values depend on it.

- [ ] **Step 4: Switch the theme to take a palette**

In `ui/src/main/java/com/jbgsoft/ambio/ui/theme/Theme.kt`, change the import of `SoundTheme` to also import `AmbioPalette` and `toPalette`, change the signature, and repoint the six `targetValue` expressions:

```kotlin
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.toPalette

@Composable
fun AmbioTheme(
    palette: AmbioPalette = SoundTheme.RAIN.toPalette(),
    content: @Composable () -> Unit
) {
    val animatedPrimary by animateColorAsState(
        targetValue = palette.primary,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "primary"
    )
```

…and the same substitution (`soundTheme.X` → `palette.X`) for `onPrimary`, `secondary`, `background`, `surface` and `surfaceVariant`. Nothing below line 55 changes: the animation machinery animates six `Color` values regardless of where they came from, so the cross-fade when a sound is added or removed comes for free.

- [ ] **Step 5: Keep `app` compiling**

`AmbioAppViewModel` still exposes a single `SoundTheme`; only the call site needs adapting. In `app/src/main/java/com/jbgsoft/ambio/`, find where `AmbioTheme(soundTheme = ...)` is called and change it to pass a palette:

```kotlin
AmbioTheme(palette = soundTheme.toPalette()) {
```

adding `import com.jbgsoft.ambio.core.domain.model.toPalette`. Task 5 replaces this with the real mix.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew :ui:testDebugUnitTest --tests '*ThemeContrastTest*'
```

Expected: PASS, 7 tests. If `known mixes produce the tabulated colours` fails, the rounding is wrong — check that every 8-bit conversion uses `roundToInt()` and not truncation.

- [ ] **Step 7: Verify the whole project still builds**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL, no new warnings.

- [ ] **Step 8: Commit**

```bash
git add core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/AmbioPalette.kt \
        ui/src/main/java/com/jbgsoft/ambio/ui/theme/Theme.kt \
        ui/src/test/java/com/jbgsoft/ambio/ui/theme/ThemeContrastTest.kt \
        app/src/main/java/com/jbgsoft/ambio/
git commit -m "feat: mix sound palettes, and enumerate all 31 in the contrast test

Averaging all six roles fails WCAG AA in 26 of the 31 mixes, every failure on
onPrimary against primary. Deriving the on- colours instead of averaging them,
plus a 1%-step nudge for the one mix still short, brings it to zero.

Ignoring volume is what makes the palette space finite, so the contrast test
enumerates every mix instead of sampling."
```

---

## Task 4: Widen the stored preference to a mix

**Files:**
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/model/UserPreferences.kt:5`
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/repository/PreferencesRepository.kt:9`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/datastore/PreferencesDataStore.kt:34,47-51`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/repository/PreferencesRepositoryImpl.kt:16-18`
- Modify: `core/data/src/test/java/com/jbgsoft/ambio/core/data/datastore/PreferencesDataStoreTest.kt` (add one test)
- Modify: `core/data/src/test/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImplTest.kt` (rename the field in the three existing tests)
- Modify: `feature/home/src/test/java/com/jbgsoft/ambio/feature/home/HomeViewModelTest.kt:132,327`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `UserPreferences.lastMix: String = "rain"` (replaces `lastSoundId`)
  - `PreferencesDataStore.setLastMix(encoded: String)`
  - `PreferencesRepository.setLastMix(encoded: String)`

- [ ] **Step 1: Write the failing test**

Append two tests to the existing class in `core/data/src/test/java/com/jbgsoft/ambio/core/data/datastore/PreferencesDataStoreTest.kt`. It already has a `newPreferencesDataStore()` helper backed by a `TemporaryFolder` rule — use it, and do not add a second fixture style:

```kotlin
    @Test
    fun `the stored mix survives a write and reads back verbatim`() = runTest {
        val dataStore = newPreferencesDataStore()

        dataStore.setLastMix("rain:1.00,fireplace:0.60")

        assertThat(dataStore.preferences.first().lastMix).isEqualTo("rain:1.00,fireplace:0.60")
    }

    @Test
    fun `the mix defaults to rain when nothing was ever stored`() = runTest {
        assertThat(newPreferencesDataStore().preferences.first().lastMix).isEqualTo("rain")
    }
```

> Phase 3a made `DataStore<Preferences>` a constructor parameter precisely so each test gets its own file — the file-scoped `by preferencesDataStore(...)` delegate is cached one-per-process and made three tests share state.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:data:testDebugUnitTest --tests '*PreferencesDataStoreTest*'
```

Expected: compilation failure — `Unresolved reference: setLastMix` and `lastMix`.

- [ ] **Step 3: Rename the field, keep the key**

`UserPreferences.kt` line 5:

```kotlin
    // Session state — where the user left off
    val lastMix: String = "rain",
```

`PreferencesDataStore.kt` — the key object is **unchanged**; only the mapping and the setter rename:

```kotlin
    private object PreferencesKeys {
        // The key string must stay "last_sound_id": it now holds a mix, but changing
        // the string would compile fine and silently wipe every user's stored mix.
        val LAST_SOUND_ID = stringPreferencesKey("last_sound_id")
```

```kotlin
            lastMix = prefs[PreferencesKeys.LAST_SOUND_ID] ?: "rain",
```

```kotlin
    suspend fun setLastMix(encoded: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SOUND_ID] = encoded
        }
    }
```

`PreferencesRepository.kt` line 9 → `suspend fun setLastMix(encoded: String)`.
`PreferencesRepositoryImpl.kt` lines 16-18 → delegate to `preferencesDataStore.setLastMix(encoded)`.

- [ ] **Step 4: Update the existing call sites and tests**

Compile and fix each error:

```bash
./gradlew assembleDebug
```

`HomeViewModel.kt:163` becomes `preferencesRepository.setLastMix(sound.id)` for now — Task 6 replaces it with the encoded mix. `SoundRepositoryImplTest` uses `UserPreferences(lastMix = ...)`. `HomeViewModelTest:132` mocks `setLastMix(any())` and line 327 verifies `setLastMix("forest")`.

- [ ] **Step 5: Run the full test suite**

```bash
./gradlew test
```

Expected: PASS, 114 `@Test` methods = 228 executed. See the running count in Self-Review; if a number comes out *lower* than the table says, a test variant has been silently dropped — that is exactly what AGP 9 did in Phase 0, halving the suite without failing.

- [ ] **Step 6: Commit**

```bash
git add -A core/domain core/data feature/home
git commit -m "refactor: widen the stored sound preference into a mix

The Kotlin field renames to lastMix because it no longer holds one id. The
DataStore key string stays \"last_sound_id\" — changing it compiles and
silently wipes every user's stored mix."
```

---

## Task 5: The mix repository

**Files:**
- Modify: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/repository/SoundRepository.kt`
- Create: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetActiveMixUseCase.kt`
- Delete: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetSelectedSoundUseCase.kt`
- Delete: `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/SelectSoundUseCase.kt`
- Modify: `core/data/src/main/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/jbgsoft/ambio/core/data/repository/SoundRepositoryImplTest.kt`
- Modify: `app/src/main/java/com/jbgsoft/ambio/AmbioAppViewModel.kt`

**Interfaces:**
- Consumes: `ActiveSound`, `MixCodec` (Task 2), `AmbioPalette`, `mixPalettes` (Task 3), `PreferencesDataStore.setLastMix` (Task 4).
- Produces:
  - `SoundRepository.getActiveMix(): Flow<List<ActiveSound>>` — never empty, canonical order
  - `SoundRepository.setSoundActive(soundId: String, active: Boolean)` — deactivating the last one is a no-op
  - `SoundRepository.setSoundLevel(soundId: String, level: Float)`
  - `GetActiveMixUseCase.invoke(): Flow<List<ActiveSound>>`
  - `AmbioAppViewModel.palette: StateFlow<AmbioPalette>`

- [ ] **Step 1: Write the failing test**

Replace `SoundRepositoryImplTest.kt` entirely:

```kotlin
package com.jbgsoft.ambio.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SoundRepositoryImplTest {

    private val dataStore = mockk<PreferencesDataStore>(relaxed = true)

    private fun repositoryStoring(lastMix: String): SoundRepositoryImpl {
        every { dataStore.preferences } returns flowOf(UserPreferences(lastMix = lastMix))
        coEvery { dataStore.setLastMix(any()) } returns Unit
        return SoundRepositoryImpl(dataStore)
    }

    @Test
    fun `the mix comes from the stored preference before anything is toggled`() = runTest {
        val repository = repositoryStoring("forest")

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("forest")
    }

    @Test
    fun `a stored multi-sound mix is restored with its levels`() = runTest {
        val repository = repositoryStoring("rain:1.00,ocean:0.40")

        val mix = repository.getActiveMix().first()

        assertThat(mix.map { it.sound.id }).containsExactly("rain", "ocean").inOrder()
        assertThat(mix.map { it.level }).containsExactly(1.0f, 0.4f).inOrder()
    }

    @Test
    fun `activating a sound adds it to the mix`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("cave", active = true)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain", "cave").inOrder()
    }

    @Test
    fun `deactivating a sound removes it from the mix`() = runTest {
        val repository = repositoryStoring("rain,cave")

        repository.setSoundActive("rain", active = false)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("cave")
    }

    @Test
    fun `deactivating the last active sound does nothing`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("rain", active = false)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain")
    }

    @Test
    fun `setting a level changes only that sound`() = runTest {
        val repository = repositoryStoring("rain,ocean")

        repository.setSoundLevel("ocean", 0.25f)

        val mix = repository.getActiveMix().first()
        assertThat(mix.single { it.sound.id == "ocean" }.level).isEqualTo(0.25f)
        assertThat(mix.single { it.sound.id == "rain" }.level).isEqualTo(1.0f)
    }

    @Test
    fun `a level is clamped into zero to one`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundLevel("rain", 4f)

        assertThat(repository.getActiveMix().first().single().level).isEqualTo(1.0f)
    }

    @Test
    fun `activating an unknown sound does nothing`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("thunder", active = true)

        assertThat(repository.getActiveMix().first().map { it.sound.id })
            .containsExactly("rain")
    }

    @Test
    fun `every change is written back to the store with its levels`() = runTest {
        val repository = repositoryStoring("rain")

        repository.setSoundActive("cave", active = true)

        coVerify { dataStore.setLastMix("rain:1.00,cave:1.00") }
    }

    @Test
    fun `all five sounds can be active at once`() = runTest {
        val repository = repositoryStoring("rain")

        listOf("fireplace", "forest", "ocean", "cave")
            .forEach { repository.setSoundActive(it, active = true) }

        assertThat(repository.getActiveMix().first()).hasSize(5)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:data:testDebugUnitTest --tests '*SoundRepositoryImplTest*'
```

Expected: compilation failure — `Unresolved reference: getActiveMix`, `setSoundActive`, `setSoundLevel`.

- [ ] **Step 3: Change the interface**

`core/domain/src/main/java/com/jbgsoft/ambio/core/domain/repository/SoundRepository.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound
import kotlinx.coroutines.flow.Flow

interface SoundRepository {
    fun getAllSounds(): List<Sound>
    fun getSoundById(id: String): Sound?

    /** Never emits an empty list: at least one sound is always active. */
    fun getActiveMix(): Flow<List<ActiveSound>>

    /** Deactivating the last active sound is a no-op, enforced here and not only in the UI. */
    suspend fun setSoundActive(soundId: String, active: Boolean)

    suspend fun setSoundLevel(soundId: String, level: Float)
}
```

- [ ] **Step 4: Rewrite the implementation**

In `SoundRepositoryImpl.kt`, keep the `sounds` list exactly as it is and replace everything from line 67 down:

```kotlin
    private val mixOverride = MutableStateFlow<String?>(null)

    override fun getAllSounds(): List<Sound> = sounds

    override fun getSoundById(id: String): Sound? = sounds.find { it.id == id }

    override fun getActiveMix(): Flow<List<ActiveSound>> = combine(
        mixOverride,
        preferencesDataStore.preferences
    ) { override, prefs ->
        MixCodec.decode(override ?: prefs.lastMix, sounds)
    }

    override suspend fun setSoundActive(soundId: String, active: Boolean) {
        if (getSoundById(soundId) == null) return
        val current = currentMix()
        val updated = when {
            active -> if (current.any { it.sound.id == soundId }) current
                      else current + ActiveSound(getSoundById(soundId)!!, 1.0f)
            // The mix is never empty.
            current.size == 1 -> return
            else -> current.filterNot { it.sound.id == soundId }
        }
        persist(updated)
    }

    override suspend fun setSoundLevel(soundId: String, level: Float) {
        val current = currentMix()
        if (current.none { it.sound.id == soundId }) return
        persist(
            current.map { active ->
                if (active.sound.id == soundId) active.copy(level = level.coerceIn(0f, 1f))
                else active
            }
        )
    }

    private suspend fun currentMix(): List<ActiveSound> =
        MixCodec.decode(mixOverride.value ?: preferencesDataStore.preferences.first().lastMix, sounds)

    private suspend fun persist(mix: List<ActiveSound>) {
        val encoded = MixCodec.encode(
            // Re-decoding normalises the order before it is written or observed.
            MixCodec.decode(MixCodec.encode(mix, withLevels = true), sounds),
            withLevels = true
        )
        mixOverride.value = encoded
        preferencesDataStore.setLastMix(encoded)
    }
```

Adjust the imports at the top of the file: add `ActiveSound`, `MixCodec`, and `kotlinx.coroutines.flow.first`.

> `mixOverride` exists for the same reason the old `selectedSoundIdFlow` did — DataStore writes are asynchronous and the UI must not wait a frame for its own toggle. Unlike the old one it starts `null`, so the stored value is reachable, which is the Task 1 fix carried forward.

- [ ] **Step 5: Replace the use cases**

Delete both old files and create `core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetActiveMixUseCase.kt`:

```kotlin
package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveMixUseCase @Inject constructor(
    private val soundRepository: SoundRepository
) {
    operator fun invoke(): Flow<List<ActiveSound>> = soundRepository.getActiveMix()
}
```

```bash
git rm core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/GetSelectedSoundUseCase.kt \
       core/domain/src/main/java/com/jbgsoft/ambio/core/domain/usecase/SelectSoundUseCase.kt
```

> `SelectSoundUseCase` was already dead before this phase: defined, injected nowhere, and duplicating the two calls `HomeViewModel` makes inline.

- [ ] **Step 6: Point the app theme at the mixed palette**

`app/src/main/java/com/jbgsoft/ambio/AmbioAppViewModel.kt` — expose a palette instead of a `SoundTheme`:

```kotlin
    val palette: StateFlow<AmbioPalette> = getActiveMix()
        .map { mix -> mixPalettes(mix.map { it.sound.theme }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SoundTheme.RAIN.toPalette())
```

with the constructor taking `getActiveMix: GetActiveMixUseCase`. Keep whatever `stateIn` parameters the existing declaration uses — only the source and the mapping change. Update the composable call site to `AmbioTheme(palette = palette)`.

- [ ] **Step 7: Make `HomeViewModel` compile**

`HomeViewModel.kt:79` — replace the `getSelectedSound()` observation with the mix, taking the first entry so the rest of the file is untouched for now:

```kotlin
        soundRepository.getActiveMix()
            .onEach { mix ->
                _uiState.update { it.copy(activeMix = mix, selectedSound = mix.first().sound) }
            }
            .launchIn(viewModelScope)
```

Add `val activeMix: List<ActiveSound> = emptyList()` to `HomeUiState`. `selectSound` (line 159) becomes:

```kotlin
    private fun selectSound(sound: Sound) {
        haptic { heavyClick() }
        viewModelScope.launch {
            soundRepository.setSoundActive(sound.id, active = true)
        }
        _uiState.update { it.copy(showSoundPicker = false) }
        if (_uiState.value.isPlaying) {
            playSoundAudio(sound)
        }
    }
```

Task 7 rewrites the audio side; this step only keeps the module compiling and the existing behaviour intact.

- [ ] **Step 8: Run the tests**

```bash
./gradlew test
```

Expected: PASS. `HomeViewModelTest` needs its `soundRepository` mock updated — `every { getActiveMix() } returns activeMixFlow` and `coEvery { setSoundActive(any(), any()) } just Runs` — and line 316's `coVerify { soundRepository.setSelectedSound("forest") }` becomes `coVerify { soundRepository.setSoundActive("forest", true) }`. Line 327's `setLastMix` verification moves into the repository's own test and should be deleted from `HomeViewModelTest`.

- [ ] **Step 9: Commit**

```bash
git add -A core feature app
git commit -m "feat: turn the sound repository into a mix repository

getActiveMix never emits empty and deactivating the last active sound is a
no-op enforced here, not only in the UI — which is what makes the palette
space exactly the 31 non-empty subsets.

Deletes SelectSoundUseCase, which was dead before this phase."
```

---

## Task 6: `MixPlayer`

**Files:**
- Create: `media/src/main/java/com/jbgsoft/ambio/media/SoundTrack.kt`
- Create: `media/src/main/java/com/jbgsoft/ambio/media/MixPlayer.kt`
- Create: `media/src/test/java/com/jbgsoft/ambio/media/MixPlayerTest.kt`
- Modify: `media/build.gradle.kts` — add the test dependencies

**Interfaces:**
- Consumes: nothing from `core:domain`. **`media` must not start depending on it.**
- Produces:
  - `interface SoundTrack { fun start(audioRes: Int); fun setVolume(level: Float); fun pause(); fun resume(); fun release() }`
  - `class MixPlayer(looper: Looper, createTrack: (String) -> SoundTrack) : SimpleBasePlayer(looper)`
  - `MixPlayer.setSoundActive(id: String, audioRes: Int, active: Boolean)`
  - `MixPlayer.setSoundLevel(id: String, level: Float)`
  - `MixPlayer.setMixTitle(title: String)`

- [ ] **Step 1: Add the test dependencies**

`media/build.gradle.kts` has no test dependencies at all today. Add exactly the three lines `core/data/build.gradle.kts` uses — `libs.bundles.testing` already contains junit, mockk, turbine, truth and kotlinx-coroutines-test:

```kotlin
    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
```

- [ ] **Step 2: Write the failing test**

Create `media/src/test/java/com/jbgsoft/ambio/media/MixPlayerTest.kt`:

```kotlin
package com.jbgsoft.ambio.media

import android.os.Looper
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MixPlayer is deliberately built on a narrow SoundTrack interface rather than on
 * Player, so the mixing logic can be tested without a device, a decoder, or an
 * audio file. Robolectric supplies only the Looper that SimpleBasePlayer requires.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MixPlayerTest {

    private class FakeTrack : SoundTrack {
        var startedWith: Int? = null
        var volume: Float = 0f
        var paused = false
        var released = false

        override fun start(audioRes: Int) { startedWith = audioRes }
        override fun setVolume(level: Float) { volume = level }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun release() { released = true }
    }

    private val tracks = mutableMapOf<String, FakeTrack>()

    private fun player(): MixPlayer =
        MixPlayer(Looper.getMainLooper()) { id -> FakeTrack().also { tracks[id] = it } }

    @Test
    fun `activating a sound starts a track for it`() {
        val mix = player()

        mix.setSoundActive("rain", audioRes = 42, active = true)

        assertThat(tracks["rain"]!!.startedWith).isEqualTo(42)
    }

    @Test
    fun `deactivating a sound releases its track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 42, active = true)

        mix.setSoundActive("rain", audioRes = 42, active = false)

        assertThat(tracks["rain"]!!.released).isTrue()
    }

    @Test
    fun `five sounds play at once`() {
        val mix = player()

        listOf("rain", "fireplace", "forest", "ocean", "cave")
            .forEachIndexed { index, id -> mix.setSoundActive(id, audioRes = index, active = true) }

        assertThat(tracks.values.count { !it.released }).isEqualTo(5)
    }

    @Test
    fun `a track's volume is its own level times the master`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.setSoundLevel("ocean", 0.5f)
        mix.volume = 0.4f

        assertThat(tracks["rain"]!!.volume).isWithin(0.001f).of(0.4f)
        assertThat(tracks["ocean"]!!.volume).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `pausing pauses every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()

        mix.pause()

        assertThat(tracks.values.all { it.paused }).isTrue()
    }

    @Test
    fun `playing resumes every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.pause()

        mix.play()

        assertThat(tracks["rain"]!!.paused).isFalse()
    }

    @Test
    fun `a sound activated while playing starts unpaused`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]!!.paused).isFalse()
    }

    @Test
    fun `stopping releases every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.stop()

        assertThat(tracks.values.all { it.released }).isTrue()
    }

    @Test
    fun `the player reports playing once a sound is active and play was called`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.play()

        assertThat(mix.playWhenReady).isTrue()
        assertThat(mix.playbackState).isEqualTo(Player.STATE_READY)
    }

    @Test
    fun `an empty mix is idle`() {
        assertThat(player().playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `the mix title reaches the media metadata`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.setMixTitle("Rain + Fireplace")

        assertThat(mix.mediaMetadata.title.toString()).isEqualTo("Rain + Fireplace")
    }

    @Test
    fun `activating the same sound twice does not restart it`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        val first = tracks["rain"]

        mix.setSoundActive("rain", audioRes = 1, active = true)

        assertThat(tracks["rain"]).isSameInstanceAs(first)
        assertThat(first!!.released).isFalse()
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :media:testDebugUnitTest --tests '*MixPlayerTest*'
```

Expected: compilation failure — `Unresolved reference: MixPlayer`, `SoundTrack`.

- [ ] **Step 4: Write `SoundTrack`**

Create `media/src/main/java/com/jbgsoft/ambio/media/SoundTrack.kt`:

```kotlin
package com.jbgsoft.ambio.media

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * One looping ambient sound. Narrow on purpose: MixPlayer holds five of these and
 * needs exactly these five operations, so the mixing logic can be unit-tested
 * against a fake instead of against the whole Player interface.
 */
interface SoundTrack {
    fun start(@RawRes audioRes: Int)
    fun setVolume(level: Float)
    fun pause()
    fun resume()
    fun release()
}

/** The real thing: one ExoPlayer looping one raw resource. */
class ExoPlayerSoundTrack(private val context: Context) : SoundTrack {

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true // handleAudioFocus — see the spec: verified on device, not in CI
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ONE }

    override fun start(@RawRes audioRes: Int) {
        val uri = Uri.parse("android.resource://${context.packageName}/$audioRes")
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun setVolume(level: Float) { player.volume = level.coerceIn(0f, 1f) }
    override fun pause() { player.pause() }
    override fun resume() { player.play() }
    override fun release() { player.release() }
}
```

- [ ] **Step 5: Write `MixPlayer`**

Create `media/src/main/java/com/jbgsoft/ambio/media/MixPlayer.kt`:

```kotlin
package com.jbgsoft.ambio.media

import android.os.Looper
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Presents N simultaneously playing sounds to the MediaSession as one logical
 * playback.
 *
 * It deliberately delegates to none of the underlying tracks. Designating one as a
 * leader and forwarding to it is less code, but the leader stops existing when the
 * user removes that sound from the mix, and a MediaSession's player cannot be
 * swapped after it is built — reassigning it would restart a sound the user did
 * not touch.
 */
class MixPlayer(
    looper: Looper,
    private val createTrack: (soundId: String) -> SoundTrack
) : SimpleBasePlayer(looper) {

    private class Entry(val track: SoundTrack, var level: Float)

    private val entries = LinkedHashMap<String, Entry>()
    private var playWhenReadyValue = false
    private var masterVolume = 1f
    private var title = ""

    private val commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_RELEASE,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE
        )
        .build()

    // --- the mixer's own API, reached through custom session commands ---

    fun setSoundActive(soundId: String, @RawRes audioRes: Int, active: Boolean) {
        if (active) {
            if (entries.containsKey(soundId)) return
            val track = createTrack(soundId)
            track.start(audioRes)
            entries[soundId] = Entry(track, level = 1f)
            track.setVolume(masterVolume)
            if (!playWhenReadyValue) track.pause()
        } else {
            entries.remove(soundId)?.track?.release()
        }
        invalidateState()
    }

    fun setSoundLevel(soundId: String, level: Float) {
        val entry = entries[soundId] ?: return
        entry.level = level.coerceIn(0f, 1f)
        entry.track.setVolume(entry.level * masterVolume)
        invalidateState()
    }

    fun setMixTitle(title: String) {
        this.title = title
        invalidateState()
    }

    // --- SimpleBasePlayer ---

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReadyValue, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (entries.isEmpty()) Player.STATE_IDLE else Player.STATE_READY)
            .setVolume(masterVolume)

        if (entries.isNotEmpty()) {
            builder.setPlaylist(listOf(mixItem()))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(0L)
        }
        return builder.build()
    }

    private fun mixItem(): MediaItemData =
        MediaItemData.Builder(MIX_ITEM_ID)
            .setMediaItem(MediaItem.Builder().setMediaId(MIX_ITEM_ID).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setIsSeekable(false)
            // Endless looping ambience: live, with no meaningful duration to report.
            .setIsDynamic(true)
            .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playWhenReadyValue = playWhenReady
        entries.values.forEach { if (playWhenReady) it.track.resume() else it.track.pause() }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        masterVolume = volume.coerceIn(0f, 1f)
        entries.values.forEach { it.track.setVolume(it.level * masterVolume) }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        releaseAllTracks()
        playWhenReadyValue = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseAllTracks()
        return Futures.immediateVoidFuture()
    }

    private fun releaseAllTracks() {
        entries.values.forEach { it.track.release() }
        entries.clear()
    }

    private companion object {
        const val MIX_ITEM_ID = "ambio_mix"
    }
}
```

> If the compiler reports that `handleSetVolume(Float)` is ambiguous with the two-argument overload, override the single-argument one explicitly as written — Media3 1.10.1 declares both and the one-argument form is the one `Player.setVolume` routes to.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew :media:testDebugUnitTest --tests '*MixPlayerTest*'
```

Expected: PASS, 12 tests.

- [ ] **Step 7: Commit**

```bash
git add media/
git commit -m "feat: add MixPlayer, N simultaneous sounds behind one Player

SimpleBasePlayer with no leader: designating one ExoPlayer as leader and
forwarding to it is less code, but the leader stops existing when that sound
leaves the mix, and a MediaSession's player cannot be swapped after build.

Built on a narrow SoundTrack interface so the mixing logic is unit-testable
without a device, a decoder, or an audio file."
```

---

## Task 7: Wire the mixer through the session

**Files:**
- Create: `media/src/main/java/com/jbgsoft/ambio/media/MixCommands.kt`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/AudioService.kt`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/AudioServiceConnection.kt`

**Interfaces:**
- Consumes: `MixPlayer`, `ExoPlayerSoundTrack` (Task 6).
- Produces:
  - `AudioServiceConnection.setSoundActive(soundId: String, audioRes: Int, active: Boolean)`
  - `AudioServiceConnection.setMixTitle(title: String)`
  - `AudioServiceConnection.setSoundLevel(soundId: String, level: Float)`
  - `playSound(...)` is **removed**; callers move to `setSoundActive` + `setMixTitle`.
  - `play()`, `pause()`, `stop()`, `setVolume()` keep their signatures and their fades.

- [ ] **Step 1: Define the command vocabulary**

Create `media/src/main/java/com/jbgsoft/ambio/media/MixCommands.kt`:

```kotlin
package com.jbgsoft.ambio.media

/**
 * The control channel between AudioServiceConnection and the service.
 *
 * The connection talks to AudioService through a MediaController, and the Player
 * interface it exposes has no room for "set sound X to 60%". Media3's custom
 * session commands are that channel.
 *
 * Everything here is a primitive: the media module does not depend on core:domain
 * and does not start to. The service never learns what a Sound is.
 */
object MixCommands {
    const val SET_ACTIVE = "com.jbgsoft.ambio.SET_SOUND_ACTIVE"
    const val SET_LEVEL = "com.jbgsoft.ambio.SET_SOUND_LEVEL"
    const val SET_TITLE = "com.jbgsoft.ambio.SET_MIX_TITLE"

    const val ARG_SOUND_ID = "sound_id"
    const val ARG_AUDIO_RES = "audio_res"
    const val ARG_ACTIVE = "active"
    const val ARG_LEVEL = "level"
    const val ARG_TITLE = "title"
}
```

- [ ] **Step 2: Swap the player in the service**

In `AudioService.kt`, replace the `ExoPlayer` field and its construction with a `MixPlayer`, and give the callback the two new responsibilities:

```kotlin
    private var mediaSession: MediaSession? = null
    private lateinit var player: MixPlayer

    override fun onCreate() {
        super.onCreate()

        player = MixPlayer(mainLooper) { ExoPlayerSoundTrack(this) }

        // …the existing sessionActivityPendingIntent block is unchanged…

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .apply { sessionActivityPendingIntent?.let { setSessionActivity(it) } }
            .build()
    }
```

`onDestroy` already calls `player.release()`, which `MixPlayer.handleRelease` now routes to every track.

Replace the empty `MediaSessionCallback` with one that declares and handles the custom commands:

```kotlin
    private inner class MediaSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = SessionCommands.Builder()
                .add(SessionCommand(MixCommands.SET_ACTIVE, Bundle.EMPTY))
                .add(SessionCommand(MixCommands.SET_LEVEL, Bundle.EMPTY))
                .add(SessionCommand(MixCommands.SET_TITLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                MixCommands.SET_ACTIVE -> player.setSoundActive(
                    soundId = args.getString(MixCommands.ARG_SOUND_ID).orEmpty(),
                    audioRes = args.getInt(MixCommands.ARG_AUDIO_RES),
                    active = args.getBoolean(MixCommands.ARG_ACTIVE)
                )
                MixCommands.SET_LEVEL -> player.setSoundLevel(
                    soundId = args.getString(MixCommands.ARG_SOUND_ID).orEmpty(),
                    level = args.getFloat(MixCommands.ARG_LEVEL)
                )
                MixCommands.SET_TITLE -> player.setMixTitle(
                    args.getString(MixCommands.ARG_TITLE).orEmpty()
                )
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
```

Add the imports: `android.os.Bundle`, `androidx.media3.session.SessionCommand`, `SessionCommands`, `SessionResult`, `SessionError`, `com.google.common.util.concurrent.Futures`, `ListenableFuture`. If `SessionError.ERROR_NOT_SUPPORTED` does not resolve in 1.10.1, use `SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)` — check which the artifact exposes before guessing.

- [ ] **Step 3: Give the connection the three new calls**

In `AudioServiceConnection.kt`, delete `playSound(...)` (lines 109-142) and add:

```kotlin
    fun setSoundActive(soundId: String, @RawRes audioRes: Int, active: Boolean) {
        val args = Bundle().apply {
            putString(MixCommands.ARG_SOUND_ID, soundId)
            putInt(MixCommands.ARG_AUDIO_RES, audioRes)
            putBoolean(MixCommands.ARG_ACTIVE, active)
        }
        send(MixCommands.SET_ACTIVE, args)
    }

    fun setSoundLevel(soundId: String, level: Float) {
        val args = Bundle().apply {
            putString(MixCommands.ARG_SOUND_ID, soundId)
            putFloat(MixCommands.ARG_LEVEL, level.coerceIn(0f, 1f))
        }
        send(MixCommands.SET_LEVEL, args)
    }

    fun setMixTitle(title: String) {
        send(MixCommands.SET_TITLE, Bundle().apply { putString(MixCommands.ARG_TITLE, title) })
    }

    private fun send(action: String, args: Bundle) {
        val mediaController = controller
        if (mediaController == null) {
            Log.w(TAG, "Cannot send $action - controller not connected")
            return
        }
        mediaController.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }
```

Add imports `android.os.Bundle` and `androidx.media3.session.SessionCommand`; drop `MediaItem`, `MediaMetadata`, `Uri` and `MediaMetadata` if they become unused — an unused import is a warning, and the baseline is fixed at 2.

`play()` needs one change: it must fade in from zero as before, but the mix may already be running, so keep the existing body untouched. `pause()`, `stop()`, `setVolume()` and both fades are unchanged — they act on the controller's `volume`, which `MixPlayer.handleSetVolume` now spreads across every track as the master.

- [ ] **Step 4: Compile only the media module**

```bash
./gradlew :media:assembleDebug
```

Expected: BUILD SUCCESSFUL. Do **not** run `./gradlew assembleDebug` yet: `HomeViewModel.playSoundAudio` calls the `playSound` this task deleted, and `feature:home` cannot compile until Task 8 rewrites it. That is expected, and it is why these two tasks share a commit.

- [ ] **Step 5: Go straight to Task 8**

No commit here. The tree is deliberately mid-change: committing would mean either a broken build or a commented-out method, and both are worse than one larger commit. Task 8 step 9 commits the two together.

---

## Task 8: The mixer in the UI

**Files:**
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeViewModel.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeUiState.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/HomeEvent.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/SoundBottomSheet.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/SoundCard.kt`
- Modify: `feature/home/src/main/java/com/jbgsoft/ambio/feature/home/components/CurrentSoundBar.kt`
- Modify: `feature/home/src/main/res/values/strings.xml`
- Modify: `feature/home/src/test/java/com/jbgsoft/ambio/feature/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `getActiveMix`, `setSoundActive`, `setSoundLevel` (Task 5); `AudioServiceConnection.setSoundActive/setSoundLevel/setMixTitle` (Task 7).
- Produces: `HomeEvent.ToggleSound(sound: Sound)` and `HomeEvent.SetSoundLevel(soundId: String, level: Float)`, replacing `HomeEvent.SelectSound`.

- [ ] **Step 1: Add the strings**

In `feature/home/src/main/res/values/strings.xml`:

```xml
    <string name="mix_sound_count">%1$d sounds</string>
    <string name="mix_level_for">Level for %1$s</string>
    <string name="mix_remove_sound">Remove %1$s from the mix</string>
    <string name="mix_add_sound">Add %1$s to the mix</string>
```

`mix_sound_count` is used from three sounds up; one or two are rendered as their names joined by " + ", which needs no resource.

- [ ] **Step 2: Write the failing test**

`HomeViewModelTest` already declares `testSound` (id `"rain"`, `audioRes = 1`) and `testSoundForest` (id `"forest"`, `audioRes = 3`), and a `selectedSoundFlow: MutableStateFlow<Sound>` seeded with `testSound`. Replace that flow with a mix flow — declare it beside the others at line 77 and initialise it at line 109:

```kotlin
    private lateinit var activeMixFlow: MutableStateFlow<List<ActiveSound>>
```

```kotlin
        activeMixFlow = MutableStateFlow(listOf(ActiveSound(testSound, 1f)))
```

and at line 117 replace `every { getSelectedSound() } returns selectedSoundFlow` with `every { getActiveMix() } returns activeMixFlow`. Then add:

```kotlin
    @Test
    fun `toggling a sound on adds it to the mix and to the audio service`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        coVerify { soundRepository.setSoundActive("forest", true) }
        verify { audioServiceConnection.setSoundActive("forest", 3, true) }
    }

    @Test
    fun `toggling an active sound off removes it`() = runTest {
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 1f))
        val viewModel = createViewModel()

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        coVerify { soundRepository.setSoundActive("forest", false) }
        verify { audioServiceConnection.setSoundActive("forest", 3, false) }
    }

    @Test
    fun `the last active sound cannot be toggled off`() = runTest {
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f))
        val viewModel = createViewModel()

        viewModel.onEvent(HomeEvent.ToggleSound(testSound))
        advanceUntilIdle()

        coVerify(exactly = 0) { soundRepository.setSoundActive("rain", false) }
        verify(exactly = 0) { audioServiceConnection.setSoundActive("rain", 1, false) }
    }

    @Test
    fun `setting a level reaches both the repository and the audio service`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(HomeEvent.SetSoundLevel("rain", 0.3f))
        advanceUntilIdle()

        coVerify { soundRepository.setSoundLevel("rain", 0.3f) }
        verify { audioServiceConnection.setSoundLevel("rain", 0.3f) }
    }

    @Test
    fun `a completed session records every sound in the mix`() = runTest {
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 0.5f))
        val viewModel = createViewModel()
        advanceUntilIdle()

        timerStateFlow.value = TimerState.Completed(wasBreak = false)
        advanceUntilIdle()

        coVerify {
            saveSessionUseCase(
                soundId = "rain,forest",
                durationMinutes = any(),
                wasCompleted = true
            )
        }
    }
```

Add `import com.jbgsoft.ambio.core.domain.model.ActiveSound`. Delete the now-unused `selectedSoundFlow` declaration and its initialiser — an unused private field is a warning, and the baseline is fixed at 2.

> `the last active sound cannot be toggled off` is the one test here that would pass vacuously if written carelessly: seed the mix with **exactly one** sound, or the assertion proves nothing.

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*HomeViewModelTest*'
```

Expected: compilation failure — `Unresolved reference: ToggleSound`, `SetSoundLevel`.

- [ ] **Step 4: Change the events and the state**

`HomeEvent.kt` — replace `data class SelectSound(val sound: Sound)` with:

```kotlin
    data class ToggleSound(val sound: Sound) : HomeEvent()
    data class SetSoundLevel(val soundId: String, val level: Float) : HomeEvent()
```

`HomeUiState.kt` — `selectedSound: Sound?` is replaced by the mix. Keep `availableSounds`:

```kotlin
    val activeMix: List<ActiveSound> = emptyList(),
```

- [ ] **Step 5: Rewrite the ViewModel's sound handling**

In `HomeViewModel.kt`:

```kotlin
    private fun loadInitialData() {
        _uiState.update { it.copy(availableSounds = soundRepository.getAllSounds()) }

        soundRepository.getActiveMix()
            .onEach { mix ->
                _uiState.update { it.copy(activeMix = mix) }
                audioServiceConnection.setMixTitle(mixTitle(mix))
            }
            .launchIn(viewModelScope)
    }

    private fun toggleSound(sound: Sound) {
        haptic { heavyClick() }
        val isActive = _uiState.value.activeMix.any { it.sound.id == sound.id }
        // The repository refuses to empty the mix; don't tell the service otherwise.
        if (isActive && _uiState.value.activeMix.size == 1) return
        viewModelScope.launch {
            soundRepository.setSoundActive(sound.id, active = !isActive)
        }
        audioServiceConnection.setSoundActive(sound.id, sound.audioRes, !isActive)
    }

    private fun setSoundLevel(soundId: String, level: Float) {
        viewModelScope.launch { soundRepository.setSoundLevel(soundId, level) }
        audioServiceConnection.setSoundLevel(soundId, level)
    }

    private fun mixTitle(mix: List<ActiveSound>): String =
        if (mix.size <= 2) {
            mix.joinToString(" + ") { stringProvider.get(it.sound.nameRes) }
        } else {
            stringProvider.get(R.string.mix_sound_count, mix.size)
        }
```

Route the two new events in `onEvent`, delete `playSoundAudio` and its call sites, and change the session save at line 342 to record the whole mix:

```kotlin
            val mix = state.activeMix
            if (mix.isNotEmpty()) {
                val minutes = when (state.selectedPreset) {
                    TimerPreset.FOCUS_25 -> 25
                    TimerPreset.FOCUS_50 -> 50
                    TimerPreset.CUSTOM -> state.customMinutes
                }
                saveSessionUseCase(
                    soundId = MixCodec.encode(mix, withLevels = false),
                    durationMinutes = minutes,
                    wasCompleted = true
                )
            }
```

When playback starts, every active sound must be pushed to the service — the service holds no state across a disconnect. In the `isConnected` observer, on a transition to `true`, replay the mix:

```kotlin
        audioServiceConnection.isConnected
            .onEach { isConnected ->
                _uiState.update { it.copy(isServiceConnected = isConnected) }
                if (isConnected) {
                    _uiState.value.activeMix.forEach { active ->
                        audioServiceConnection.setSoundActive(active.sound.id, active.sound.audioRes, true)
                        audioServiceConnection.setSoundLevel(active.sound.id, active.level)
                    }
                    audioServiceConnection.setMixTitle(mixTitle(_uiState.value.activeMix))
                }
            }
            .launchIn(viewModelScope)
```

- [ ] **Step 6: Turn the picker into a mixer**

**`SoundCard.kt`** — replace the `SoundCard` composable (lines 28-83; leave `ComingSoonCard` alone). The card grows when active to make room for its slider:

```kotlin
@Composable
fun SoundCard(
    sound: Sound,
    isActive: Boolean,
    level: Float,
    canDeactivate: Boolean,
    onToggle: () -> Unit,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val toggleLabel = stringResource(
        if (isActive) R.string.mix_remove_sound else R.string.mix_add_sound,
        stringResource(sound.nameRes)
    )
    val levelLabel = stringResource(R.string.mix_level_for, stringResource(sound.nameRes))

    Card(
        onClick = onToggle,
        enabled = !isActive || canDeactivate,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isActive) 160.dp else 120.dp)
            .semantics { contentDescription = toggleLabel },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = sound.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(sound.nameRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            if (isActive) {
                Slider(
                    value = level,
                    onValueChange = onLevelChange,
                    modifier = Modifier.semantics { contentDescription = levelLabel }
                )
            }
        }
    }
}
```

Add the imports: `androidx.compose.material3.Slider`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`.

> `contentDescription` goes on the card through `semantics`, not on the `Icon` — the icon stays `null` because the card as a whole is the touch target, and TalkBack should announce "Add Rain to the mix", not "Rain" twice.

**`SoundBottomSheet.kt`** — change the signature (lines 25-31) and the grid body (lines 58-64):

```kotlin
fun SoundBottomSheet(
    showSheet: Boolean,
    sounds: List<Sound>,
    activeMix: List<ActiveSound>,
    onToggleSound: (Sound) -> Unit,
    onLevelChange: (String, Float) -> Unit,
    onDismiss: () -> Unit
) {
```

```kotlin
                    items(sounds) { sound ->
                        val active = activeMix.firstOrNull { it.sound.id == sound.id }
                        SoundCard(
                            sound = sound,
                            isActive = active != null,
                            level = active?.level ?: 1f,
                            canDeactivate = activeMix.size > 1,
                            onToggle = { onToggleSound(sound) },
                            onLevelChange = { onLevelChange(sound.id, it) }
                        )
                    }
```

Add `import com.jbgsoft.ambio.core.domain.model.ActiveSound`. The sheet no longer closes on tap — building a mix means several taps, so the user dismisses it.

**`CurrentSoundBar.kt`** — replace `sound: Sound?` with the mix (lines 23-58). The icon is the first sound's; the label matches the one the notification shows:

```kotlin
@Composable
fun CurrentSoundBar(
    activeMix: List<ActiveSound>,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (activeMix.isNotEmpty()) {
                Icon(
                    imageVector = activeMix.first().sound.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (activeMix.size <= 2) {
                        activeMix.joinToString(" + ") { stringResource(it.sound.nameRes) }
                    } else {
                        stringResource(R.string.mix_sound_count, activeMix.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                Text(
                    text = stringResource(R.string.sound_none_selected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalButton(onClick = onChangeClick) {
            Text(stringResource(R.string.action_change_sound))
        }
    }
}
```

Add `import com.jbgsoft.ambio.core.domain.model.ActiveSound`. The empty branch stays: the repository guarantees a non-empty mix, but the very first composition happens before the flow emits.

**`HomeScreen.kt`** — update the three call sites to pass `uiState.activeMix`, `onToggleSound = { viewModel.onEvent(HomeEvent.ToggleSound(it)) }` and `onLevelChange = { id, level -> viewModel.onEvent(HomeEvent.SetSoundLevel(id, level)) }`, following whatever event-dispatch style the file already uses.

- [ ] **Step 7: Run the tests and the build**

```bash
./gradlew test lint assembleDebug
```

Expected: tests PASS, lint 0 errors, build successful, no third compiler warning.

- [ ] **Step 8: Check no string was hardcoded**

```bash
grep -rnE 'Text\("|text = "|contentDescription = "' feature/home/src/main feature/stats/src/main
```

Expected: no output. This is the widened pattern — Phase 2 shipped a hardcoded string past seven reviews because its grep only looked for `text = "` and missed a positional argument.

- [ ] **Step 9: Commit**

```bash
git add -A media feature/home
git commit -m "feat: mix several sounds at once, each with its own level

The sound picker becomes a mixer: cards toggle instead of selecting, and each
active sound carries its own slider. The master volume keeps its fades.

Per-sound commands reach the service as Media3 custom session commands
carrying primitives, since AudioServiceConnection talks to AudioService
through a MediaController and media does not depend on core:domain.

Tasks 7 and 8 land together: removing playSound leaves the tree unbuildable
in between."
```

---

## Task 9: Show every sound of a recorded mix in the history

**Files:**
- Modify: `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsUiState.kt:7`
- Modify: `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsViewModel.kt:37`
- Modify: `feature/stats/src/main/java/com/jbgsoft/ambio/feature/stats/StatsScreen.kt:162-165`
- Modify: `feature/stats/src/test/java/com/jbgsoft/ambio/feature/stats/StatsViewModelTest.kt`

**Interfaces:**
- Consumes: `MixCodec.decode` (Task 2), `SoundRepository.getAllSounds()` (Task 5).
- Produces: `SessionRow.soundNameResIds: List<Int?>` — one entry per recorded id, `null` where the sound no longer exists.

- [ ] **Step 1: Write the failing test**

Add to `StatsViewModelTest`:

```kotlin
    @Test
    fun `a session recorded before the mixer shows its single sound`() = runTest {
        sessionsFlow.value = listOf(session(soundId = "rain"))

        val row = viewModel.uiState.first { it.sessions.isNotEmpty() }.sessions.single()

        assertThat(row.soundNameResIds).hasSize(1)
        assertThat(row.soundNameResIds.single()).isNotNull()
    }

    @Test
    fun `a session recorded with a mix shows every sound`() = runTest {
        sessionsFlow.value = listOf(session(soundId = "rain,fireplace"))

        val row = viewModel.uiState.first { it.sessions.isNotEmpty() }.sessions.single()

        assertThat(row.soundNameResIds).hasSize(2)
        assertThat(row.soundNameResIds.none { it == null }).isTrue()
    }

    @Test
    fun `an unknown id inside a mix does not drop the sounds beside it`() = runTest {
        // "wind" is the real pre-rename id of what is now "cave".
        sessionsFlow.value = listOf(session(soundId = "rain,wind"))

        val row = viewModel.uiState.first { it.sessions.isNotEmpty() }.sessions.single()

        assertThat(row.soundNameResIds).hasSize(2)
        assertThat(row.soundNameResIds[0]).isNotNull()
        assertThat(row.soundNameResIds[1]).isNull()
    }
```

Reuse the existing fixtures in that file; add a `session(soundId: String)` helper only if one is not already there.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :feature:stats:testDebugUnitTest --tests '*StatsViewModelTest*'
```

Expected: compilation failure — `Unresolved reference: soundNameResIds`.

- [ ] **Step 3: Widen the row**

`StatsUiState.kt` line 7:

```kotlin
    // One entry per recorded sound; null where that sound no longer exists.
    val soundNameResIds: List<Int?>,
```

`StatsViewModel.kt` line 37 — resolve each id independently, so one unknown does not take the others with it. `MixCodec.decode` cannot be used here: it discards unknown ids and this screen must render them as the fallback, in position.

```kotlin
                            soundNameResIds = session.soundId
                                .split(',')
                                .map { it.trim().substringBefore(':') }
                                .filter { it.isNotEmpty() }
                                .map { soundRepository.getSoundById(it)?.nameRes },
```

- [ ] **Step 4: Render the list**

`StatsScreen.kt` lines 162-165:

```kotlin
    val soundName = session.soundNameResIds
        .joinToString(" + ") { nameRes ->
            nameRes?.let { stringResource(it) } ?: stringResource(R.string.stats_unknown_sound)
        }
```

- [ ] **Step 5: Run the tests, lint and build**

```bash
./gradlew test lint assembleDebug
```

Expected: all PASS, lint 0 errors, warnings still 2.

- [ ] **Step 6: Verify on a device**

Install and check, in this order:

```bash
./gradlew installDebug
```

1. The app opens on the sound stored from the previous run, not on rain.
2. Activate all five sounds — all five are audible, and the palette shifts to the neutral grey the spec tabulates.
3. Move one sound's slider — only that sound changes, and **the colour does not move**.
4. Move the master — everything changes together.
5. Try to deactivate the last remaining sound — nothing happens.
6. Kill and reopen the app — the same mix and the same levels come back.
7. Complete a timed session with a mix, then open Statistics — the entry lists every sound.
8. **Place a call to the device while a five-sound mix plays.** Every sound must pause, and all of them resume on hang-up. This is criterion 10 and CI cannot check it. If they do not pause together, the fallback is in the spec: centralise focus in `MixPlayer` and set `handleAudioFocus = false` on every `ExoPlayerSoundTrack`.

- [ ] **Step 7: Commit**

```bash
git add -A feature/stats
git commit -m "feat: show every sound of a recorded mix in the history

Each id resolves on its own, so a session recorded with a sound that has since
been renamed shows the fallback in place without taking the sounds beside it
down — the case that already happened once when Wind became Cave."
```

---

## Self-Review

**Spec coverage**

| Spec section | Task |
|---|---|
| §1 persistence bug | 1 |
| §2 comma-joined convention, exact format rules, key invariant | 2, 4 |
| §3 the mix is never empty | 5 (repository), 8 (UI) |
| §4 `MixPlayer`, no leader, JVM-testable | 6 |
| §4 custom session commands, primitives only | 7 |
| §5 master × per-sound level | 6 (`handleSetVolume`), 8 (UI) |
| §6 palette mixing, exact algorithm, 31-palette test | 3 |
| §6 `AmbioTheme` takes a palette | 3 |
| §7 UI: toggles, sliders, mix label | 8 |
| Criterion 5, history with multiple ids | 9 |
| Criterion 10, audio focus on device | 9 step 6 |

**Pre-flight that was executed, not assumed.** Phase 2's plan was written entirely from greps and reading, and shipped three errors that only running things would have caught. Four things here were run before this plan was written:

- `SimpleBasePlayer`, `ForwardingSimpleBasePlayer`, `SessionCommand` and `ConnectionResult.AcceptedResultBuilder` were confirmed present in the actual 1.10.1 artifacts in the Gradle cache, and `getState()` confirmed as the only abstract member.
- The 31 palettes were computed under **Kotlin's** arithmetic — `Color.red` as float32, `roundToInt` half-up, and the double conversion back to 8 bits that `averageOf` performs. It does not diverge from the reference: 0 failures, and the three hex values Task 3 asserts come out exactly. Had it diverged, the tabulated values in this plan and in the spec would both have been wrong.
- `media/build.gradle.kts` was read to confirm it does not depend on `core:domain` — which is what forces the custom commands to carry primitives.
- `SelectSoundUseCase` was confirmed dead by grep across every module before Task 5 was written to delete it.

**Running test count.** Every task states the total it should reach. A number coming out *lower* than this table means a variant was silently dropped, which is what AGP 9 did in Phase 0 — halving the suite from 154 to 77 without a single failure.

| After task | `@Test` methods | Executed (× 2 variants) |
|---|---|---|
| baseline | 92 | 184 |
| 1 | 95 | 190 |
| 2 | 108 | 216 |
| 3 | 112 | 224 |
| 4 | 114 | 228 |
| 5 | 121 | 242 |
| 6 | 133 | 266 |
| 8 | 138 | 276 |
| 9 | 141 | 282 |

Task 3 nets +4 because `ThemeContrastTest` goes from 3 methods to 7; Task 5 nets +7 because `SoundRepositoryImplTest` goes from 3 to 10. Task 8's +5 assumes the two obsolete `SelectSound` assertions in `HomeViewModelTest` are deleted, not adapted.

**Two things this plan changes about the spec's assumptions, deliberately:**

1. **`MixPlayer` depends on `SoundTrack`, not on `Player`.** The spec claims `MixPlayer` is JVM-testable. `SimpleBasePlayer` needs a `Looper` (Robolectric supplies it, and it is already in the catalogue at 4.16.1) but an `ExoPlayer` needs a decoder and an audio file, which no unit test has. Narrowing the dependency to five methods is what makes criterion 7 actually achievable rather than nominally so.

2. **Tasks 7 and 8 share a commit.** Removing `playSound` leaves `HomeViewModel` uncompilable until the UI is rewritten. Splitting them would require committing a commented-out method, which is worse than one larger commit.

**Carried into this phase's ledger, not fixed here:** the media notification still loads no artwork (the illustrations are vector XML, which `RawResourceDataSource` cannot open at all — the fix is bitmap assets); `app/build.gradle.kts` still declares five dependencies `app/src` does not use; there are still no instrumented tests anywhere in the repository.
