# Centralised audio focus, and the emulator that proves it — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all five ambient sounds actually play at once — they do not today — and prove it with the repository's first instrumented tests.

> **Task 5 was rewritten during execution.** It originally added an emulator job to CI. The owner ruled that out — an emulator costs more CI time per pull request than the whole existing workflow — so the tests ship as documented local tooling instead. Nothing in `.github/workflows/` changes.

**Architecture:** Every `ExoPlayerSoundTrack` stops requesting audio focus; `MixPlayer` requests it once for the whole mix through a narrow `AudioFocus` interface, the same shape `SoundTrack` already uses, so `MixPlayer` keeps no `Context` and stays JVM-testable. Four instrumented tests drive the real UI with UiAutomator and assert against `dumpsys audio` rather than against anything the app reports about itself.

**Tech Stack:** Kotlin 2.3.21, Media3 1.10.1 (`SimpleBasePlayer`), Hilt 2.60.1, Compose, `androidx.test.uiautomator`, Android Test Orchestrator.

**Spec:** `docs/superpowers/specs/2026-08-04-audio-focus-and-device-ci-design.md`

## Global Constraints

- **`MixPlayer` must not gain a `Context`.** Audio focus enters through the `AudioFocus` interface, mirroring `SoundTrack`. A `Context` inside `MixPlayer` destroys its JVM testability, which is the only coverage this class can have.
- **The `media` module must not depend on `core:domain`.**
- **Ducking multiplies, it does not overwrite.** `masterVolume` holds the user's setting; a separate multiplier applies the duck, so restoring is exact.
- **A pause caused by focus loss is not a pause by the user.** Only the former resumes on focus gain. Confusing the two makes hanging up a call resume audio the user had deliberately paused.
- **Instrumented tests assert on `dumpsys audio`, never on `MixPlayer`, the `MediaController`, or the session's `PlaybackState`.** During the entire bug `MixPlayer` reported `PLAYING(3)` with four of five tracks paused — a test asking the app would have passed throughout.
- **Instrumented tests must filter `dumpsys audio` by pid.** Those lines carry `u/pid:<uid>/<pid>` and no package name; without the filter a test counts other apps' audio and passes for the wrong reason.
- Lint stays at 0 errors. **Kotlin compiler warnings stay at 2** (both pre-existing in `ui/theme/Theme.kt`). Measure with `--rerun-tasks --no-build-cache`; cached compile tasks replay output without re-emitting diagnostics.
- No hardcoded user-facing strings. Verify with `grep -rnE 'Text\("|text = "|contentDescription = "' feature/home/src/main feature/stats/src/main`.
- The DataStore key string `"last_sound_id"` must not change.
- Build: `./gradlew assembleDebug` · Lint: `./gradlew lint` · Unit tests: `./gradlew test` · Instrumented: `./gradlew connectedDebugAndroidTest`

## Facts verified by execution, not by reading

These were measured before this plan was written. Trust them.

- On the local AVD `Ambio_API37` (API 37), counting `AudioPlaybackConfiguration` entries for the app's pid: **1 of 5 started** with `handleAudioFocus = true`; **5 of 5 started** with it `false`. The system's audio focus stack held **one** entry for `com.jbgsoft.ambio`, not five.
- With focus disabled and nothing replacing it, `adb emu gsm call` does **not** pause the mix — so removing the flag alone trades one bug for another.
- `adb emu gsm call <number>` and `adb emu gsm cancel <number>` both work and return `OK`.
- The app's own UI exposes the content descriptions the tests will use: `"Play"`, `"Change"`, `"Add <Name> to the mix"`, `"Remove <Name> from the mix"`.
- **System images (now moot, kept as a record):** API 37's package is named `system-images;android-37.0;google_apis;x86_64` — note the `.0` — while `system-images;android-36;google_apis;x86_64` has no suffix. This mattered only for the CI emulator job that Task 5 no longer adds.

---

## File Structure

**New**

| File | Responsibility |
|---|---|
| `media/src/main/java/com/jbgsoft/ambio/media/AudioFocus.kt` | The narrow focus interface, its event enum, and the real `AudioManager`-backed implementation |
| `app/src/androidTest/java/com/jbgsoft/ambio/AudioState.kt` | Reads `dumpsys audio` through `UiAutomation` and counts this process's started tracks |
| `app/src/androidTest/java/com/jbgsoft/ambio/MixerUi.kt` | UiAutomator helpers: launch, press play, open the picker, activate every sound |
| `app/src/androidTest/java/com/jbgsoft/ambio/LaunchTest.kt` | The app starts and stays up |
| `app/src/androidTest/java/com/jbgsoft/ambio/MixPlaybackTest.kt` | Five sounds actually play; losing audio focus pauses them all and regaining it resumes them |
| `app/src/androidTest/java/com/jbgsoft/ambio/FocusIntruder.kt` | Takes transient audio focus the way an incoming call does |
| `app/src/androidTest/java/com/jbgsoft/ambio/MixPersistenceTest.kt` | The mix is rebuilt from disk in a fresh process |

**Modified** — `MixPlayer.kt`, `SoundTrack.kt`, `AudioService.kt`, `MixPlayerTest.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `CLAUDE.md`. **No workflow file is touched.**

---

## Task 1: Centralise audio focus

**Files:**
- Create: `media/src/main/java/com/jbgsoft/ambio/media/AudioFocus.kt`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/MixPlayer.kt:27-30,34-37,89-116,144-159`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/SoundTrack.kt:28-38`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/AudioService.kt:37`
- Modify: `media/src/test/java/com/jbgsoft/ambio/media/MixPlayerTest.kt`

**Interfaces:**
- Consumes: `SoundTrack` (`start`, `setVolume`, `pause`, `resume`, `release`), `MixEntry(soundId, audioRes, level)`.
- Produces:
  - `enum class FocusChange { LOST, LOST_TRANSIENT, LOST_TRANSIENT_DUCK, GAINED }`
  - `interface AudioFocus { fun request(): Boolean; fun abandon(); fun onChange(listener: (FocusChange) -> Unit) }`
  - `class AndroidAudioFocus(context: Context) : AudioFocus`
  - `MixPlayer(looper: Looper, createTrack: (String) -> SoundTrack, audioFocus: AudioFocus)`

- [ ] **Step 1: Write the failing tests**

Add to `media/src/test/java/com/jbgsoft/ambio/media/MixPlayerTest.kt`. Put `FakeFocus` beside the existing `FakeTrack`, and add `private val focus = FakeFocus()` plus pass it into the existing `player()` helper — every existing test keeps working because a granted request is the default.

```kotlin
    private class FakeFocus : AudioFocus {
        var granted = true
        var requests = 0
        var abandons = 0
        private var listener: ((FocusChange) -> Unit)? = null

        override fun request(): Boolean { requests++; return granted }
        override fun abandon() { abandons++ }
        override fun onChange(listener: (FocusChange) -> Unit) { this.listener = listener }

        /** Drives the player the way the system would. */
        fun emit(change: FocusChange) { listener?.invoke(change) }
    }
```

```kotlin
    @Test
    fun `playing requests audio focus once for the whole mix`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.play()

        assertThat(focus.requests).isEqualTo(1)
    }

    @Test
    fun `a denied focus request leaves the mix paused`() {
        focus.granted = false
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.play()

        assertThat(mix.playWhenReady).isFalse()
        assertThat(tracks["rain"]!!.paused).isTrue()
    }

    @Test
    fun `a transient loss pauses every track and keeps the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()

        focus.emit(FocusChange.LOST_TRANSIENT)

        assertThat(tracks.values.all { it.paused }).isTrue()
        assertThat(focus.abandons).isEqualTo(0)
    }

    @Test
    fun `regaining focus after a transient loss resumes every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        focus.emit(FocusChange.LOST_TRANSIENT)

        focus.emit(FocusChange.GAINED)

        assertThat(tracks.values.none { it.paused }).isTrue()
    }

    @Test
    fun `regaining focus does not resume a mix the user paused`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.pause()

        focus.emit(FocusChange.GAINED)

        assertThat(tracks["rain"]!!.paused).isTrue()
        assertThat(mix.playWhenReady).isFalse()
    }

    @Test
    fun `a permanent loss pauses the mix and abandons the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        focus.emit(FocusChange.LOST)

        assertThat(tracks["rain"]!!.paused).isTrue()
        assertThat(focus.abandons).isEqualTo(1)
    }

    @Test
    fun `ducking lowers the volume without stopping or losing the master`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.volume = 0.5f

        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        assertThat(tracks["rain"]!!.paused).isFalse()
        assertThat(tracks["rain"]!!.volume).isWithin(0.001f).of(0.1f)  // 1.0 * 0.5 * 0.2
    }

    @Test
    fun `regaining focus after ducking restores the exact master volume`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.volume = 0.5f
        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        focus.emit(FocusChange.GAINED)

        assertThat(tracks["rain"]!!.volume).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `a sound added while ducked comes in ducked too`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]!!.volume).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `stopping abandons the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.stop()

        assertThat(focus.abandons).isEqualTo(1)
    }

    @Test
    fun `pausing does not abandon the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.pause()

        assertThat(focus.abandons).isEqualTo(0)
    }
```

> The last one matters: abandoning on every pause and re-requesting on every play would let another app slip into the gap, and the user would come back from a pause to find their mix outranked.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :media:testDebugUnitTest --tests '*MixPlayerTest*'
```

Expected: compilation failure — `Unresolved reference: AudioFocus`, `FocusChange`.

- [ ] **Step 3: Write the focus interface and its real implementation**

Create `media/src/main/java/com/jbgsoft/ambio/media/AudioFocus.kt`:

```kotlin
package com.jbgsoft.ambio.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** What the system told us about our hold on audio focus. */
enum class FocusChange { LOST, LOST_TRANSIENT, LOST_TRANSIENT_DUCK, GAINED }

/**
 * Audio focus for the whole mix, as one narrow seam.
 *
 * Narrow for the same reason [SoundTrack] is: MixPlayer must not take a Context, or it
 * stops being unit-testable on the JVM, and this class has no other coverage available.
 *
 * Before this existed, each ExoPlayer requested focus for itself. The system keeps one
 * entry per client, so five players of the same app evicted one another and only the
 * most recently added sound kept playing.
 */
interface AudioFocus {
    /** @return true if focus was granted; the mix must not play without it. */
    fun request(): Boolean
    fun abandon()
    fun onChange(listener: (FocusChange) -> Unit)
}

class AndroidAudioFocus(context: Context) : AudioFocus {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var listener: ((FocusChange) -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val mapped = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAINED
            AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOST
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOST_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LOST_TRANSIENT_DUCK
            else -> return@OnAudioFocusChangeListener
        }
        listener?.invoke(mapped)
    }

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()

    override fun request(): Boolean =
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }

    override fun onChange(listener: (FocusChange) -> Unit) {
        this.listener = listener
    }
}
```

> `AudioFocusRequest` needs API 26; this project's `minSdk` is 31, so no version guard is required.

- [ ] **Step 4: Teach `MixPlayer` to own the focus**

In `MixPlayer.kt`, take the interface in the constructor and add the two pieces of state the spec requires — a duck multiplier separate from the master, and a flag distinguishing a focus pause from a user pause:

```kotlin
class MixPlayer(
    looper: Looper,
    private val createTrack: (soundId: String) -> SoundTrack,
    private val audioFocus: AudioFocus
) : SimpleBasePlayer(looper) {

    private class Entry(val track: SoundTrack, var level: Float)

    private val entries = LinkedHashMap<String, Entry>()
    private var playWhenReadyValue = false
    private var masterVolume = 1f
    private var title = ""

    // Ducking must not overwrite masterVolume, or there is nothing exact to restore.
    private var duckMultiplier = 1f

    // A pause the system caused is not a pause the user asked for. Only the first resumes
    // when focus comes back; conflating them makes hanging up a call restart audio the
    // user had deliberately silenced.
    private var pausedByFocusLoss = false

    init {
        audioFocus.onChange(::onFocusChange)
    }
```

Add the handler and a single place that computes a track's volume:

```kotlin
    private fun onFocusChange(change: FocusChange) {
        when (change) {
            FocusChange.LOST -> {
                if (playWhenReadyValue) pauseForFocusLoss()
                audioFocus.abandon()
            }
            FocusChange.LOST_TRANSIENT -> if (playWhenReadyValue) pauseForFocusLoss()
            FocusChange.LOST_TRANSIENT_DUCK -> {
                duckMultiplier = DUCK_MULTIPLIER
                applyVolumes()
            }
            FocusChange.GAINED -> {
                duckMultiplier = 1f
                applyVolumes()
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    playWhenReadyValue = true
                    entries.values.forEach { it.track.resume() }
                }
            }
        }
        invalidateState()
    }

    private fun pauseForFocusLoss() {
        pausedByFocusLoss = true
        playWhenReadyValue = false
        entries.values.forEach { it.track.pause() }
    }

    private fun applyVolumes() {
        entries.values.forEach { it.track.setVolume(it.level * masterVolume * duckMultiplier) }
    }
```

Replace the three places that set a track's volume with `applyVolumes()` or the same expression:

- `setSoundActive` (line 96): `track.setVolume(masterVolume * duckMultiplier)` — a sound added while ducked must arrive ducked.
- `setSoundLevel` (line 108): `entry.track.setVolume(entry.level * masterVolume * duckMultiplier)`
- `handleSetVolume` (line 157): `applyVolumes()`

Gate playback on the focus, and release it when the mix stops:

```kotlin
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            // Do not play without focus; a denied request leaves the mix paused.
            if (!audioFocus.request()) return Futures.immediateVoidFuture()
            pausedByFocusLoss = false
        } else {
            // A deliberate pause keeps the focus: abandoning and re-requesting on every
            // pause would let another app take our place while the user is deciding.
            pausedByFocusLoss = false
        }
        playWhenReadyValue = playWhenReady
        entries.values.forEach { if (playWhenReady) it.track.resume() else it.track.pause() }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        releaseAllTracks()
        playWhenReadyValue = false
        pausedByFocusLoss = false
        audioFocus.abandon()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        releaseAllTracks()
        audioFocus.abandon()
        return Futures.immediateVoidFuture()
    }
```

Add to the companion object beside `MIX_ITEM_ID`:

```kotlin
        const val DUCK_MULTIPLIER = 0.2f
```

- [ ] **Step 5: Stop every track from requesting focus for itself**

In `SoundTrack.kt`, in `ExoPlayerSoundTrack`'s builder (lines 28-38):

```kotlin
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            // false, deliberately: MixPlayer owns focus for the whole mix. With true, each
            // of the five players requested focus for itself and evicted the others — the
            // system keeps one entry per client, so only the newest sound stayed audible.
            false
        )
        // Also false: five players each pausing on an unplugged headset is five
        // uncoordinated decisions about one mix. MixPlayer's focus loss covers it.
        .setHandleAudioBecomingNoisy(false)
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ONE }
```

- [ ] **Step 6: Wire it in the service**

`AudioService.kt` line 37:

```kotlin
        player = MixPlayer(mainLooper, { ExoPlayerSoundTrack(this) }, AndroidAudioFocus(this))
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew :media:testDebugUnitTest --tests '*MixPlayerTest*'
```

Expected: PASS. The 20 pre-existing tests plus the 11 new ones.

- [ ] **Step 8: Verify the whole project still builds and the warning count holds**

```bash
./gradlew test lint assembleDebug --rerun-tasks --no-build-cache 2>&1 | grep -E "^w:|BUILD|FAILED" | sort -u
```

Expected: BUILD SUCCESSFUL, lint 0 errors, exactly 2 distinct `w:` lines, both in `ui/theme/Theme.kt`.

- [ ] **Step 9: Commit**

```bash
git add media/
git commit -m "fix: give the whole mix one audio focus instead of five

Each ExoPlayer requested focus for itself with identical attributes. The
system keeps one entry per client, so every sound added evicted the one
already playing: measured on API 37, one of five tracks was audible.

MixPlayer now owns the focus through a narrow interface, so it still takes
no Context and stays testable on the JVM."
```

---

## Task 2: The instrumented-test harness, and the app starts

The repository has no `androidTest` source set anywhere and has never run an instrumented test. This task builds the floor and proves it with the smallest possible test.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/jbgsoft/ambio/AudioState.kt`
- Create: `app/src/androidTest/java/com/jbgsoft/ambio/MixerUi.kt`
- Create: `app/src/androidTest/java/com/jbgsoft/ambio/LaunchTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 at compile time.
- Produces:
  - `AudioState.startedTrackCount(): Int` — this process's `state:started` entries in `dumpsys audio`
  - `AudioState.awaitStartedTracks(expected: Int, timeoutMs: Long = 25_000): Int`
  - `MixerUi.launchApp()`, `MixerUi.pressPlay()`, `MixerUi.activateAllSounds()`, `MixerUi.clearAppData()`

- [ ] **Step 1: Declare the dependencies**

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
androidx-test-runner = "1.7.0"
androidx-test-ext-junit = "1.3.0"
androidx-test-uiautomator = "2.3.0"
```

under `[libraries]`:

```toml
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidx-test-runner" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext-junit" }
androidx-test-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "androidx-test-uiautomator" }
```

In `app/build.gradle.kts`'s `dependencies` block:

```kotlin
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
```

`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` is already declared at `app/build.gradle.kts:36`; leave it alone. **Do not add Hilt test infrastructure** — these tests drive the real app through its UI and never inject anything, which is what keeps the bootstrapping cost small.

If any of those three versions does not resolve, run `./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath` to see what is available and pin the newest that does. Record what you changed and why.

- [ ] **Step 2: Write the audio-state reader**

Create `app/src/androidTest/java/com/jbgsoft/ambio/AudioState.kt`:

```kotlin
package com.jbgsoft.ambio

import android.app.UiAutomation
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/**
 * Counts how many audio tracks this process actually has playing, as the *system*
 * sees it.
 *
 * The app's own reports cannot be trusted for this. Throughout the bug these tests
 * exist to catch, MixPlayer published PlaybackState PLAYING(3) to the media session
 * while four of its five tracks sat paused: it was reporting what it had asked for,
 * not what was happening. Any assertion routed through MixPlayer, the MediaController
 * or the session's PlaybackState would have been green the whole time.
 */
object AudioState {

    private val automation: UiAutomation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    fun shell(command: String): String =
        FileInputStream(automation.executeShellCommand(command).fileDescriptor)
            .bufferedReader()
            .use { it.readText() }

    /**
     * The app under test, taken from the instrumentation rather than from BuildConfig.
     *
     * Two reasons, both of which break the naive version. This project does not enable
     * `buildFeatures { buildConfig = true }`, so `BuildConfig` is not generated at all;
     * and even with it enabled, an unqualified `BuildConfig` inside androidTest resolves
     * to the *test* application's, whose id is "com.jbgsoft.ambio.test" — `pidof` would
     * then match nothing and every count would silently be zero.
     */
    val targetPackage: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun appPid(): String? =
        shell("pidof $targetPackage").trim().split(" ").firstOrNull { it.isNotEmpty() }

    /**
     * dumpsys audio lists every app's playback, and those lines carry no package name —
     * only "u/pid:<uid>/<pid>". Filtering by package would match nothing and quietly
     * count the emulator's other audio instead.
     */
    fun startedTrackCount(): Int {
        val pid = appPid() ?: return 0
        return shell("dumpsys audio")
            .lineSequence()
            .filter { it.contains("AudioPlaybackConfiguration") }
            .filter { it.contains("/$pid ") }
            .count { it.contains("state:started") }
    }

    /**
     * Polls rather than sleeping a guessed amount. The app fades sounds in over 8
     * seconds and the system's view lags behind the app's, so a reading taken right
     * after pressing play catches a half-built mix.
     */
    fun awaitStartedTracks(expected: Int, timeoutMs: Long = 25_000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = startedTrackCount()
        while (System.currentTimeMillis() < deadline) {
            if (last == expected) return last
            Thread.sleep(500)
            last = startedTrackCount()
        }
        return last
    }
}
```

- [ ] **Step 3: Write the UI helpers**

Create `app/src/androidTest/java/com/jbgsoft/ambio/MixerUi.kt`. The content descriptions below were read off the running app, not guessed:

```kotlin
package com.jbgsoft.ambio

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Drives the real app the way a person would, through UiAutomator. */
object MixerUi {

    // Not BuildConfig: see the note on AudioState.targetPackage.
    private val PACKAGE: String get() = AudioState.targetPackage
    private const val TIMEOUT = 10_000L

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    val soundNames = listOf("Rain", "Fireplace", "Forest", "Ocean", "Cave")

    fun clearAppData() {
        AudioState.shell("pm clear $PACKAGE")
    }

    fun launchApp() {
        AudioState.shell("am start -n $PACKAGE/.MainActivity")
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT)
    }

    fun forceStop() {
        AudioState.shell("am force-stop $PACKAGE")
    }

    fun isRunning(): Boolean =
        AudioState.shell("pidof $PACKAGE").trim().isNotEmpty()

    fun pressPlay() {
        device.wait(Until.findObject(By.desc("Play")), TIMEOUT)?.click()
    }

    /** Opens the picker, switches every sound on, and closes it. */
    fun activateAllSounds() {
        device.wait(Until.findObject(By.text("Change")), TIMEOUT)?.click()
        soundNames.forEach { name ->
            device.wait(Until.findObject(By.desc("Add $name to the mix")), 3_000)?.click()
        }
        device.pressBack()
        device.waitForIdle()
    }

    fun activeSoundCount(): Int =
        soundNames.count { name ->
            device.findObject(By.desc("Remove $name from the mix")) != null
        }
}
```

- [ ] **Step 4: Write the launch test**

Create `app/src/androidTest/java/com/jbgsoft/ambio/LaunchTest.kt`:

```kotlin
package com.jbgsoft.ambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cheapest test here, and not a formality: in Phase 2 this app crashed on launch
 * for two entire tasks with CI green throughout, because nothing ever ran it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LaunchTest {

    @Test
    fun theAppStartsAndStaysUp() {
        MixerUi.forceStop()
        MixerUi.launchApp()
        Thread.sleep(3_000)

        assertThat(MixerUi.isRunning()).isTrue()
        assertThat(AudioState.shell("logcat -d -b crash").contains("FATAL EXCEPTION")).isFalse()
    }
}
```

Truth is already on the `androidTest` classpath through `libs.bundles.testing`'s presence in the project; if it is not, add `androidTestImplementation(libs.truth)` alongside the three dependencies from step 1.

- [ ] **Step 5: Run it on the emulator**

Start an emulator first if none is attached:

```bash
$ANDROID_HOME/emulator/emulator -avd Ambio_API37 -no-snapshot-load -no-boot-anim &
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do sleep 5; done
```

```bash
./gradlew :app:connectedDebugAndroidTest --tests '*LaunchTest*' 2>&1 | grep -E "BUILD|FAILED|tests" | tail -5
```

Expected: PASS, 1 test. If the emulator has TalkBack enabled it will add audio-focus noise later; disable it now with `adb shell settings put secure enabled_accessibility_services ""`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest/
git commit -m "test: add the first instrumented tests this repository has ever had

The harness asserts against dumpsys audio rather than against anything the
app says about itself, because the bug these tests exist to catch is exactly
one where the app reported PLAYING while four of five tracks were paused."
```

---

## Task 3: Prove the five sounds actually play

**Files:**
- Create: `app/src/androidTest/java/com/jbgsoft/ambio/MixPlaybackTest.kt`

**Interfaces:**
- Consumes: `AudioState.awaitStartedTracks`, `MixerUi.clearAppData/launchApp/activateAllSounds/pressPlay/activeSoundCount` (Task 2); the focus fix from Task 1.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/jbgsoft/ambio/MixPlaybackTest.kt`:

```kotlin
package com.jbgsoft.ambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The regression test for the bug that made this work necessary: the app built five
 * ExoPlayers and only one of them was audible, because each requested audio focus for
 * itself and the newest evicted the rest.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MixPlaybackTest {

    @Before
    fun startFresh() {
        MixerUi.clearAppData()
        MixerUi.launchApp()
    }

    @Test
    fun allFiveSoundsPlayAtOnce() {
        MixerUi.activateAllSounds()
        assertThat(MixerUi.activeSoundCount()).isEqualTo(5)

        MixerUi.pressPlay()

        assertThat(AudioState.awaitStartedTracks(expected = 5)).isEqualTo(5)
    }
}
```

> `activeSoundCount()` is asserted before playing on purpose. Without it, a UI change that stopped the picker from activating sounds would make the audio assertion fail for a reason that has nothing to do with audio focus, and the failure message would send the next reader down the wrong path.

- [ ] **Step 2: Run the test and confirm it passes with the fix**

```bash
./gradlew :app:connectedDebugAndroidTest --tests '*MixPlaybackTest*' 2>&1 | grep -E "BUILD|FAILED|tests" | tail -5
```

Expected: PASS.

- [ ] **Step 3: Prove the test discriminates**

This is the test the whole task exists for; a version of it that passes against the bug is worth nothing. Restore the bug and watch it fail:

```bash
sed -i '' 's/^            false$/            true/' media/src/main/java/com/jbgsoft/ambio/media/SoundTrack.kt
./gradlew :app:connectedDebugAndroidTest --tests '*MixPlaybackTest*' 2>&1 | grep -E "BUILD|FAILED" | tail -3
```

Expected: **FAILED**, with the assertion reporting 1 rather than 5. Then restore and confirm green again:

```bash
git checkout -- media/src/main/java/com/jbgsoft/ambio/media/SoundTrack.kt
./gradlew :app:connectedDebugAndroidTest --tests '*MixPlaybackTest*' 2>&1 | grep -E "BUILD|FAILED" | tail -3
```

Record both outputs in your report. If the test passes with `handleAudioFocus = true`, it is not testing what its name says and must be fixed before this task is done.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/jbgsoft/ambio/MixPlaybackTest.kt
git commit -m "test: assert all five sounds are actually playing

Verified to discriminate: with handleAudioFocus restored to true on each
track, this test fails reporting one started track instead of five."
```

---

## Task 4: Losing focus mid-mix, and surviving a restart

**This task was rewritten during execution.** Its first version could not work, for two independent reasons found by running it. Both are recorded here because they are the kind of thing only execution reveals:

1. **`Emulator.kt` could never have run.** It shelled out to `adb emu gsm call` with a `ProcessBuilder`, but instrumented tests execute *on the device*, where no `adb` binary exists.
2. **The persistence test killed itself.** It called `MixerUi.forceStop()` and `MixerUi.clearAppData()`, and instrumentation runs inside the app's own process — force-stopping the package or clearing its data terminates the test host mid-test.

The replacements are better than workarounds, not worse. The focus test now provokes a *real* system focus loss from inside the test process, which works on physical devices too rather than only on emulators. The persistence test runs under Android Test Orchestrator, which gives every test method a fresh process — so the mix genuinely has to come back from disk.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/androidTest/java/com/jbgsoft/ambio/MixPlaybackTest.kt`
- Create: `app/src/androidTest/java/com/jbgsoft/ambio/FocusIntruder.kt`
- Create: `app/src/androidTest/java/com/jbgsoft/ambio/MixPersistenceTest.kt`

**Interfaces:**
- Consumes: `AudioState.awaitStartedTracks`, `AudioState.shell`, `MixerUi.launchApp/pressPlay/activateAllSounds/activeSoundCount` (Tasks 2-3).
- Produces: `FocusIntruder.grabTransiently()` and `FocusIntruder.release()`.

- [ ] **Step 1: Add Test Orchestrator**

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
androidx-test-orchestrator = "1.6.1"
```

under `[libraries]`:

```toml
androidx-test-orchestrator = { group = "androidx.test", name = "orchestrator", version.ref = "androidx-test-orchestrator" }
```

In `app/build.gradle.kts`:

```kotlin
    androidTestUtil(libs.androidx.test.orchestrator)
```

and inside the `android { }` block:

```kotlin
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
```

> **Do not set `clearPackageData`.** Orchestrator clears app data between tests only when asked, and the persistence test below depends on the mix *surviving* from one method to the next. Turning it on silently deletes the very state under test, and the test would then fail for a reason that has nothing to do with persistence.

If `1.6.1` does not resolve, run `./gradlew :app:dependencies --configuration androidTestUtil` and pin the newest that does; record what you changed.

- [ ] **Step 2: Write the focus intruder**

Create `app/src/androidTest/java/com/jbgsoft/ambio/FocusIntruder.kt`:

```kotlin
package com.jbgsoft.ambio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Takes audio focus away from the app, the way an incoming call does.
 *
 * A phone call interrupts music by requesting transient audio focus; every other
 * holder gets AUDIOFOCUS_LOSS_TRANSIENT and is expected to pause. This asks the
 * system for exactly that, so what the app receives is a genuine framework callback
 * and not a stub of one — the system decides, not the test.
 *
 * It replaces an earlier attempt that shelled out to `adb emu gsm call`. That could
 * never have worked: instrumented tests run on the device, where there is no adb
 * binary. It is also better than the thing it replaces, because this runs on a
 * physical phone as well as on an emulator.
 *
 * The real `adb emu gsm call` path was verified by hand on API 37 and took the mix
 * 5 -> 0 -> 5, so this is a faithful proxy for it and not a weaker substitute.
 */
object FocusIntruder {

    private val audioManager: AudioManager
        get() = InstrumentationRegistry.getInstrumentation().context
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var request: AudioFocusRequest? = null

    /** Grabs transient focus. The app under test should pause every sound. */
    fun grabTransiently() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        request = req
        check(audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "FocusIntruder could not take audio focus; the test cannot prove anything"
        }
    }

    /** Gives it back. The app under test should resume every sound. */
    fun release() {
        request?.let { audioManager.abandonAudioFocusRequest(it) }
        request = null
    }
}
```

> The `check(...)` matters. If the request were ever denied, a test that carried on would assert "the mix paused" against a mix nobody ever interrupted, and pass for the wrong reason.

- [ ] **Step 3: Write the focus-loss test**

Add to `MixPlaybackTest`:

```kotlin
    @Test
    fun losingFocusPausesTheWholeMixAndGettingItBackResumesIt() {
        MixerUi.activateAllSounds()
        MixerUi.pressPlay()
        assertThat(AudioState.awaitStartedTracks(expected = 5)).isEqualTo(5)

        FocusIntruder.grabTransiently()
        try {
            assertThat(AudioState.awaitStartedTracks(expected = 0)).isEqualTo(0)
        } finally {
            FocusIntruder.release()
        }

        assertThat(AudioState.awaitStartedTracks(expected = 5)).isEqualTo(5)
    }
```

> The `finally` is not decoration. Without it, a failed middle assertion leaves the test process holding audio focus, and every later test in the run fails for a reason that has nothing to do with what it was testing.

- [ ] **Step 4: Run it and prove it discriminates**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jbgsoft.ambio.MixPlaybackTest 2>&1 \
  | grep -E "BUILD|FAILED|tests" | tail -5
```

Expected: PASS, 2 tests.

Then break the production behaviour it guards and confirm it fails. Comment out the `LOST_TRANSIENT` branch's `pauseForFocusLoss()` call in `MixPlayer.kt`'s `onFocusChange`, re-run, and expect a failure reporting 5 where 0 was expected. Restore with `git checkout -- media/` and confirm green again. Record both outputs.

- [ ] **Step 5: Write the persistence test**

Create `app/src/androidTest/java/com/jbgsoft/ambio/MixPersistenceTest.kt`:

```kotlin
package com.jbgsoft.ambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Two methods, deliberately, because a single one could not do this.
 *
 * Under Android Test Orchestrator each test method runs in its own process, so the
 * app is genuinely torn down between the two halves and the second has to rebuild
 * the mix from DataStore. Written as one method it would have had to kill the app
 * itself — and instrumentation lives inside the app's process, so that kills the test.
 *
 * Phase 3b's persistence fix is covered by JVM tests over the repository; nothing
 * until now checked that the whole path — DataStore, repository, service and UI —
 * actually reassembles a five-sound mix after the process dies.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class MixPersistenceTest {

    @Test
    fun step1_activateAllFiveSounds() {
        MixerUi.launchApp()
        MixerUi.activateAllSounds()

        assertThat(MixerUi.activeSoundCount()).isEqualTo(5)
    }

    @Test
    fun step2_theMixIsRebuiltInAFreshProcess() {
        MixerUi.launchApp()
        MixerUi.pressPlay()

        assertThat(AudioState.awaitStartedTracks(expected = 5)).isEqualTo(5)
    }
}
```

> `activeSoundCount()` only reads while the picker sheet is open, and `activateAllSounds()` closes it — reopen it before counting, the way `MixPlaybackTest` already does.

- [ ] **Step 6: Prove the two methods really ran in different processes**

This is the claim the whole test rests on, and it is invisible in a pass/fail line. Have each method record `android.os.Process.myPid()` to logcat, run the class, and read the two values back:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jbgsoft.ambio.MixPersistenceTest 2>&1 \
  | grep -E "BUILD|FAILED|tests" | tail -5
adb logcat -d | grep "MixPersistenceTest pid"
```

Expected: PASS, 2 tests, and **two different pids**. If the pids match, Orchestrator is not actually in effect, the second method is reading state from a process that never died, and the test proves nothing — fix the configuration before continuing. Remove the logging once confirmed, and report both pids.

- [ ] **Step 7: Run the whole instrumented suite**

```bash
./gradlew :app:connectedDebugAndroidTest 2>&1 | grep -E "BUILD|FAILED|tests" | tail -6
```

Expected: PASS, 5 tests across four classes, none skipped.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest/
git commit -m "test: cover losing audio focus mid-mix and surviving a restart

The focus test provokes a real transient focus loss from the test process,
which is what an incoming call does and works on physical devices too. The
persistence test runs under Test Orchestrator, so its two halves execute in
different processes and the mix genuinely has to come back from disk."
```

---

## Task 5: Make the suite reliable, and write down how to run it

**This task was rewritten during execution.** Its original version added an emulator job to
`.github/workflows/ci.yml`. The owner ruled that out: an emulator adds several minutes to
every pull request — more than lint, unit tests, `assembleDebug` and `bundleRelease` take
together — and the cost is not considered worth it. No CI changes are made.

The tests are still worth having; they simply live as local tooling. That does not lower the
bar for reliability. A suite that fails one run in five is not usable locally either — nobody
runs it twice to find out whether the red meant anything.

**Files:**
- Modify: `app/src/androidTest/java/com/jbgsoft/ambio/MixerUi.kt`
- Modify: `CLAUDE.md`
- **Do not touch `.github/workflows/ci.yml`.**

**Interfaces:**
- Consumes: `MixerUi`, `AudioState` (Tasks 2-4).
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Close the cold-start race in `launchApp()`**

Roughly one full-suite run in five fails with `was: 0` at `MixPersistenceTest.kt:46`, on a
cold-process first launch. The cause is understood and needs no production change.

`SoundRepositoryImpl.getActiveMix()` is a `combine` over the DataStore flow, so it emits
nothing until the first asynchronous disk read completes, and `HomeViewModel` seeds its mix at
`emptyList()` until then. `MixerUi.launchApp()` currently waits only for the app's root window
to exist, so it can return while the UI still shows that empty seed — and a test that reads the
mix in that window sees nothing.

Make `launchApp()` wait for evidence the mix has actually hydrated: a real sound affordance
rather than the window or a static label. Give it a bounded timeout and fail clearly, naming
what it waited for, if it never arrives.

**Do not use a fixed `Thread.sleep`.** A sleep tuned to this emulator is a flake waiting to
return on slower hardware, and this task exists precisely because an intermittent test is
worse than none.

- [ ] **Step 2: Prove the suite is stable**

```bash
./gradlew :app:connectedDebugAndroidTest 2>&1 | grep -E "BUILD|FAILED|tests" | tail -5
```

Run it **five consecutive times** and report each result individually. All five must pass. One
green run proves nothing about an intermittent defect; that is the whole lesson of this task.

If five clean runs are not reachable, stop and report BLOCKED with the evidence rather than
declaring it done.

- [ ] **Step 3: Write down how to run them**

`CLAUDE.md` documents this project's build and validation commands. The instrumented suite is
useless if nobody knows it exists, and it is now the only thing standing between this bug and
its return. Add it to the Validation section, including:

- that it needs a running emulator or attached device, and that CI does **not** run it
- the command, `./gradlew :app:connectedDebugAndroidTest`
- how to run one class: `-Pandroid.testInstrumentationRunnerArguments.class=com.jbgsoft.ambio.MixPlaybackTest` — and that `--tests` does **not** work for this task
- what the suite covers, in one line: the app launches, five sounds actually play at once, losing audio focus pauses the whole mix, and the mix is rebuilt from disk in a fresh process
- that it should be run before releasing, since nothing else exercises audio at all

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/ CLAUDE.md
git commit -m "test: make the instrumented suite stable, and document running it

launchApp() returned as soon as the window existed, which could be before
the stored mix had been read back from DataStore — roughly one full-suite
run in five failed on that race. It now waits for the mix itself.

These tests are local tooling by decision: an emulator job would cost more
CI time per pull request than the entire existing workflow."
```

---

## Self-Review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| `handleAudioFocus = false` on every track | 1, step 5 |
| `MixPlayer` requests focus once, via a narrow interface | 1, steps 3-4 |
| No `Context` in `MixPlayer` (criterion 7) | 1 — enforced by the constructor and the JVM tests |
| The four focus events behave as tabulated | 1, step 1 tests + step 4 handler |
| Duck multiplies by 0.2 and restores exactly | 1 — `DUCK_MULTIPLIER`, two tests |
| Focus requested in `handleSetPlayWhenReady(true)`, released in stop/release/permanent loss, **not** on user pause | 1, step 4 + two tests |
| A user pause does not resume on focus gain (criterion 3) | 1 — `pausedByFocusLoss` + its test |
| `setHandleAudioBecomingNoisy` centralised | 1, step 5 |
| Five started tracks, asserted (criterion 1) | 3 |
| Incoming call pauses and resumes (criterion 2) | 4 |
| App launches without a fatal (criterion 4) | 2 |
| Mix survives the process dying (criterion 5) | 4 |
| Tests run on an emulator on every PR (criterion 6) | 5 |
| Assertions go through `dumpsys audio`, filtered by pid | 2 — `AudioState` |
| Lint 0, warnings 2 (criterion 8) | 1, step 8 |
| No hardcoded strings (criterion 9) | No task adds user-facing strings; the grep is in Global Constraints |

**A type error caught in this review, worth knowing about before you hit it.** The first draft used `BuildConfig.APPLICATION_ID` to find the app's package. That fails twice over: this project never enables `buildFeatures { buildConfig = true }`, so `BuildConfig` is not generated; and inside `androidTest` an unqualified `BuildConfig` resolves to the *test* application's, whose id ends in `.test`. Either way `pidof` matches nothing and every audio count comes back zero — a suite that passes its launch test and reports zero started tracks forever. It now reads the package from `InstrumentationRegistry.getInstrumentation().targetContext.packageName`.

**Two risks this plan names rather than hides**

1. **`Emulator.kt` as written probably does not work.** Instrumented tests run on the device, and there is no `adb` binary there. Task 4 step 1 says so explicitly and gives the fallback — drive the call from the CI side and split the assertions across methods. It is written this way rather than pre-solved because the fallback costs more and should only be paid if needed; what must not happen is a test that skips silently and reads as green.
2. **The CI emulator's API level.** API 36 is pinned because 37's package carries a `.0` suffix the action does not construct. If 36 also fails, the run says so — which is why Task 5 forbids closing on anything but a green GitHub Actions run.

**One thing deliberately not covered.** Nothing asserts that the five sounds are *audibly* well balanced, or how the slider feels under a finger. An emulator cannot answer either honestly, and pretending otherwise would put a green check on a question nobody asked it.
