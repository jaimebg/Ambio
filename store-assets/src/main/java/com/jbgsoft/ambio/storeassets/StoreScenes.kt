package com.jbgsoft.ambio.storeassets

import androidx.compose.runtime.Composable
import com.jbgsoft.ambio.core.data.repository.SOUND_CATALOGUE
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.feature.home.HomeScreen
import com.jbgsoft.ambio.feature.home.HomeUiState
import com.jbgsoft.ambio.feature.home.components.SoundPickerContent
import com.jbgsoft.ambio.feature.settings.SettingsScreen
import com.jbgsoft.ambio.feature.settings.SettingsUiState
import com.jbgsoft.ambio.feature.stats.SessionRow
import com.jbgsoft.ambio.feature.stats.StatsScreen
import com.jbgsoft.ambio.feature.stats.StatsUiState

/**
 * A scene is a screen plus the exact state it should be caught in.
 *
 * Every value is hardcoded: no ViewModel, no MediaSession, no DataStore. The
 * point is that a scene renders the same way on any machine, on any day, so a
 * regenerated set of assets diffs cleanly instead of churning.
 */
enum class StoreScene(val id: String, val captionRes: Int) {
    MIX("mix", R.string.shot_mix),
    PICKER("picker", R.string.shot_picker),
    TIMER("timer", R.string.shot_timer),
    STATS("stats", R.string.shot_stats),
    SETTINGS("settings", R.string.shot_settings);

    /**
     * The mix this scene is caught playing, and with it the scene's colour.
     *
     * A different one per shot, sequenced warm to cool: embers, then a spread of
     * all three families, then forest, then water, then the quiet end of the
     * catalogue. One mix across the whole set rendered five teal screens with
     * the same three tiles lit, which sells neither the palette nor the
     * catalogue. Walking the hues also makes the panorama a journey rather than
     * one colour smeared across five frames.
     *
     * A computed property rather than a constructor argument: an enum entry
     * cannot safely read a top-level val declared below it.
     */
    val mix: List<ActiveSound>
        get() = when (this) {
            // Embers: fireplace, cafe and brown noise all sit in the warm corner.
            MIX -> mixOf("fireplace" to 0.85f, "cafe" to 0.55f, "brown_noise" to 0.4f)
            // The widest spread in the catalogue, warm through green to blue,
            // which is precisely what this caption claims.
            PICKER -> mixOf("fireplace" to 0.7f, "forest" to 0.8f, "ocean" to 0.6f)
            TIMER -> mixOf("forest" to 0.85f, "birds" to 0.6f, "crickets" to 0.45f)
            STATS -> mixOf("ocean" to 0.8f, "stream" to 0.6f, "wind" to 0.45f)
            SETTINGS -> mixOf("rain" to 0.75f, "cave" to 0.6f, "white_noise" to 0.35f)
        }

    /**
     * The glows the frame behind the device is built from.
     *
     * Deliberately the same for every shot, and deliberately not this scene's
     * own mix. The frame is one panorama sliced five ways, so if each slice
     * brought its own colours the set gained a hard seam at every boundary and
     * stopped reading as one image. The devices carry the colour instead: each
     * one wears its own mix, so the set still sweeps warm to violet while the
     * ground under it stays continuous.
     */
    val glows: List<SoundGlow> get() = PANORAMA_GLOWS

    /**
     * The palette the app itself would be wearing.
     *
     * AmbioTheme defaults to RAIN, so leaving this out renders every screen in
     * blue no matter what is in the mix -- which would put a screenshot of
     * palette mixing on the store that does not show palette mixing. The app
     * derives it the same way, in AmbioAppViewModel.
     */
    val palette: AmbioPalette
        get() = mixPalettes(mix.map { it.sound.theme })
}

/** Warm, green and blue: the widest span the catalogue offers in three stops. */
private val PANORAMA_GLOWS = listOf(SoundGlow.FIREPLACE, SoundGlow.FOREST, SoundGlow.OCEAN)

private fun mixOf(vararg parts: Pair<String, Float>): List<ActiveSound> =
    parts.map { (id, level) -> ActiveSound(sound(id), level) }

private fun sound(id: String) = SOUND_CATALOGUE.first { it.id == id }

private fun homeState(mix: List<ActiveSound>) = HomeUiState(
    activeMix = mix,
    availableSounds = SOUND_CATALOGUE,
    volume = 0.7f,
    effectsEnabled = true
)

@Composable
private fun Home(mix: List<ActiveSound>, mode: AppMode) = HomeScreen(
    uiState = homeState(mix).copy(
        mode = mode,
        isPlaying = true,
        selectedPreset = TimerPreset.FOCUS_25,
        timerState = if (mode == AppMode.TIMER) {
            TimerState.Running(
                remainingMs = (18 * 60 + 42) * 1_000L,
                totalMs = 25 * 60 * 1_000L
            )
        } else {
            TimerState.Idle
        }
    ),
    onEvent = {},
    onNavigateToSettings = {},
    onNavigateToStats = {}
)

// The picker's own composable rather than the bottom sheet: a sheet draws into
// its own window, which a decor-view capture does not see.
@Composable
private fun Picker(mix: List<ActiveSound>, columns: Int = 3) = SoundPickerContent(
    sounds = SOUND_CATALOGUE,
    activeMix = mix,
    onToggleSound = {},
    onLevelChange = { _, _ -> },
    onLevelChangeFinished = {},
    columns = columns
)

@Composable
private fun Stats() = StatsScreen(
    uiState = StatsUiState(
        totalFocusMinutes = 1_285,
        completedSessionCount = 47,
        sessions = SAMPLE_SESSIONS
    ),
    onDeleteSession = {},
    onNavigateBack = {}
)

@Composable
private fun Settings() = SettingsScreen(
    uiState = SettingsUiState(
        hapticsEnabled = true,
        chimeEnabled = true,
        effectsEnabled = true
    ),
    onHapticsChanged = {},
    onChimeChanged = {},
    onEffectsChanged = {},
    onNavigateBack = {}
)

/** What the phone panel shows. */
@Composable
fun StoreScene.Content() {
    when (this) {
        StoreScene.MIX -> Home(mix, AppMode.AMBIENT)
        StoreScene.PICKER -> Picker(mix)
        StoreScene.TIMER -> Home(mix, AppMode.TIMER)
        StoreScene.STATS -> Stats()
        StoreScene.SETTINGS -> Settings()
    }
}

/**
 * What the tablet panel shows in a landscape shot.
 *
 * On a tablet the interesting thing is the two-pane Home, so it leads wherever
 * it can, and the picker earns its own shot only at four columns, where all
 * twelve sounds are on screen at once -- which is exactly what that caption
 * claims.
 */
@Composable
fun StoreScene.TabletMain() {
    when (this) {
        StoreScene.MIX -> Home(mix, AppMode.AMBIENT)
        StoreScene.PICKER -> Picker(mix, columns = 4)
        StoreScene.TIMER -> Home(mix, AppMode.TIMER)
        StoreScene.STATS -> Stats()
        StoreScene.SETTINGS -> Settings()
    }
}

// Fixed timestamps, not "now": a shot generated tomorrow must be identical to
// one generated today, and a relative date would change under it.
private const val DAY = 86_400_000L
private const val ANCHOR = 1_760_000_000_000L

private val SAMPLE_SESSIONS = listOf(
    session(1, listOf("forest", "ocean", "cave"), 50, ANCHOR),
    session(2, listOf("rain", "fireplace"), 25, ANCHOR - DAY),
    session(3, listOf("cafe"), 50, ANCHOR - 2 * DAY),
    session(4, listOf("ocean", "birds"), 25, ANCHOR - 3 * DAY),
    session(5, listOf("brown_noise"), 25, ANCHOR - 4 * DAY)
)

private fun session(id: Long, soundIds: List<String>, minutes: Int, at: Long) = SessionRow(
    id = id,
    soundNameResIds = soundIds.map { sound(it).nameRes },
    durationMinutes = minutes,
    completedAt = at
)
