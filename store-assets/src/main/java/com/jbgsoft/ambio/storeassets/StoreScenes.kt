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

    /** The glows the frame's gradient is built from, matching what the scene shows. */
    val glows: List<SoundGlow>
        get() = HERO_MIX.map { it.sound.glow }

    /**
     * The palette the app itself would be wearing.
     *
     * AmbioTheme defaults to RAIN, so leaving this out renders every screen in
     * blue no matter what is in the mix -- which would put a screenshot of
     * palette mixing on the store that does not show palette mixing. The app
     * derives it the same way, in AmbioAppViewModel, and holds it across Stats
     * and Settings too, which is why every scene uses the one mix.
     */
    val palette: AmbioPalette
        get() = mixPalettes(HERO_MIX.map { it.sound.theme })
}

private fun sound(id: String) = SOUND_CATALOGUE.first { it.id == id }

/**
 * Forest, ocean and cave: three sounds whose glows are far enough apart that the
 * gradient reads as a blend rather than as one colour, which is the entire claim
 * the mixer screenshots are making.
 */
private val HERO_MIX = listOf(
    ActiveSound(sound("forest"), 0.85f),
    ActiveSound(sound("ocean"), 0.6f),
    ActiveSound(sound("cave"), 0.45f)
)

private val HOME_BASE = HomeUiState(
    activeMix = HERO_MIX,
    availableSounds = SOUND_CATALOGUE,
    volume = 0.7f,
    effectsEnabled = true
)

/**
 * The panel that sits behind, giving the shot its second point.
 *
 * Pairing is deliberate rather than decorative: the mixer shot shows what is
 * being mixed, the timer shot shows the mix it is running over, and the two
 * quiet screens back each other so no shot is a single flat rectangle.
 */
@Composable
fun StoreScene.BackContent() {
    when (this) {
        StoreScene.MIX -> Picker()
        StoreScene.PICKER -> Home(AppMode.AMBIENT)
        StoreScene.TIMER -> Home(AppMode.AMBIENT)
        StoreScene.STATS -> Home(AppMode.TIMER)
        StoreScene.SETTINGS -> Stats()
    }
}

@Composable
private fun Home(mode: AppMode) = HomeScreen(
    uiState = HOME_BASE.copy(
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

@Composable
private fun Picker(columns: Int = 3) = SoundPickerContent(
    sounds = SOUND_CATALOGUE,
    activeMix = HERO_MIX,
    onToggleSound = {},
    onLevelChange = { _, _ -> },
    onLevelChangeFinished = {},
    columns = columns
)

/**
 * What the tablet panel shows in a landscape shot.
 *
 * Not the same pairing as the phone shots. On a tablet the interesting thing is
 * the two-pane Home, so it leads wherever it can, and the picker earns its own
 * shot only at four columns, where all twelve sounds are on screen at once --
 * which is exactly the claim that caption makes.
 */
@Composable
fun StoreScene.TabletMain() {
    when (this) {
        StoreScene.MIX -> Home(AppMode.AMBIENT)
        StoreScene.PICKER -> Picker(columns = 4)
        StoreScene.TIMER -> Home(AppMode.TIMER)
        StoreScene.STATS -> Stats()
        StoreScene.SETTINGS -> Content()
    }
}

/** The phone alongside it: the same app on the other form factor. */
@Composable
fun StoreScene.TabletSide() {
    when (this) {
        StoreScene.MIX -> Picker()
        StoreScene.PICKER -> Home(AppMode.AMBIENT)
        StoreScene.TIMER -> Home(AppMode.AMBIENT)
        StoreScene.STATS -> Home(AppMode.TIMER)
        StoreScene.SETTINGS -> Stats()
    }
}

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
fun StoreScene.Content() {
    when (this) {
        StoreScene.MIX -> HomeScreen(
            uiState = HOME_BASE.copy(
                mode = AppMode.AMBIENT,
                isPlaying = true
            ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStats = {}
        )

        // The picker's own composable rather than the bottom sheet: a sheet draws
        // into its own window, which a decor-view capture does not see.
        StoreScene.PICKER -> SoundPickerContent(
            sounds = SOUND_CATALOGUE,
            activeMix = HERO_MIX,
            onToggleSound = {},
            onLevelChange = { _, _ -> },
            onLevelChangeFinished = {},
            columns = 3
        )

        StoreScene.TIMER -> HomeScreen(
            uiState = HOME_BASE.copy(
                mode = AppMode.TIMER,
                isPlaying = true,
                selectedPreset = TimerPreset.FOCUS_25,
                timerState = TimerState.Running(
                    remainingMs = (18 * 60 + 42) * 1_000L,
                    totalMs = 25 * 60 * 1_000L
                )
            ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStats = {}
        )

        StoreScene.STATS -> StatsScreen(
            uiState = StatsUiState(
                totalFocusMinutes = 1_285,
                completedSessionCount = 47,
                sessions = SAMPLE_SESSIONS
            ),
            onDeleteSession = {},
            onNavigateBack = {}
        )

        StoreScene.SETTINGS -> SettingsScreen(
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
