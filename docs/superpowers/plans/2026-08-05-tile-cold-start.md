# Tile cold start Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tapping the Quick Settings tile with the app closed actually play the stored mix, instead of playing nothing, stealing audio focus, and crashing.

**Architecture:** `MixPlayer` refuses to play — and refuses to request audio focus — when it holds no sounds, and tells the service instead. `AudioService` owns the asynchronous part: it fetches the stored mix through a narrow `MixSource` interface declared in `media`, loads it, and plays; if there is still nothing, it stops itself rather than waiting for a `startForeground` that will never come.

**Tech Stack:** Kotlin 2.3.21, Media3 1.10.1 (`SimpleBasePlayer`), Hilt 2.60.1, coroutines.

**Spec:** `docs/superpowers/specs/2026-08-05-tile-cold-start-design.md`

## Global Constraints

- **`media` must declare no `project(...)` dependency.** It declares none today and four branches of this project have kept it that way. `MixSource` is declared *in* `media` over its own `MixEntry` type; the implementation lives in `core:di`, which already sees `core:domain`, `core:data` and `:media`.
- **`MixPlayer` must not gain a `Context`.** That is what keeps it unit-testable on the JVM, and JVM tests are the only coverage this project has — it has no instrumented tests and will not get any.
- **Focus is requested only after there is something to play.** Requesting it first is the bug that silences whatever else the user was listening to.
- Lint stays at 0 errors. **Kotlin compiler warnings stay at 2** (both pre-existing in `ui/theme/Theme.kt`). Measure with `--rerun-tasks --no-build-cache`.
- No hardcoded user-facing strings.
- Build: `./gradlew assembleDebug` · Lint: `./gradlew lint` · Tests: `./gradlew test`

## Facts verified by execution, not by reading

- **The failure is reproducible on `emulator-5554`** with `adb shell cmd statusbar expand-settings` followed by `adb shell cmd statusbar click-tile com.jbgsoft.ambio/com.jbgsoft.ambio.feature.tile.PlaybackTile`. That two-step recipe is what makes the tile actually listening when the click arrives; a bare `click-tile` does not reproduce a real tap. Symptoms: `PlaybackState {state=NONE(0)}` throughout, `MediaFocusControl: requestAudioFocus()` from the app, then `ForegroundServiceDidNotStartInTimeException`.
- **Routing already works** — logcat shows `Background started FGS: Allowed … cmp=com.jbgsoft.ambio/.media.AudioService … tempAllowListReason:<tile onclick>`. Nothing about the intent path needs changing.
- `core/di/build.gradle.kts` declares `core:domain`, `core:data`, `core:common` **and** `:media`, so it can see both `SoundRepository` and `MixEntry`.
- `MixPlayer`'s constructor today is `(looper: Looper, createTrack: (String) -> SoundTrack, audioFocus: AudioFocus)`.
- `AudioService` builds it at `media/.../AudioService.kt:76` and is already `@AndroidEntryPoint`.

## One deliberate refinement of the spec

The spec put `MixSource` in `MixPlayer`'s constructor and had `handleSetPlayWhenReady` load the mix itself, returning a `ListenableFuture` that completes later. That works, but it drags a `CoroutineScope` and `SettableFuture` plumbing into the one class whose simplicity is the reason it can be tested at all.

This plan keeps the split where the spec's own reasoning points: **`MixPlayer` stays synchronous and decides; `AudioService` does the asynchronous work.** `MixPlayer` gains a callback, not a data source. The behaviour, the ordering, and the "stop rather than hang" outcome are exactly as the spec requires — only the owner of the coroutine changes.

---

## File Structure

**New**

| File | Responsibility |
|---|---|
| `media/src/main/java/com/jbgsoft/ambio/media/MixSource.kt` | One-method interface over `MixEntry`, declared where `media` can see it |
| `core/di/src/main/java/com/jbgsoft/ambio/core/di/MixSourceModule.kt` | Implements it from `SoundRepository` and binds it |

**Modified** — `MixPlayer.kt`, `MixPlayerTest.kt`, `AudioService.kt`, `MixBundleTest.kt` (it constructs `MixPlayer` directly).

---

## Task 1: `MixPlayer` refuses to play nothing

**Files:**
- Create: `media/src/main/java/com/jbgsoft/ambio/media/MixSource.kt`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/MixPlayer.kt`
- Modify: `media/src/test/java/com/jbgsoft/ambio/media/MixPlayerTest.kt`
- Modify: `media/src/test/java/com/jbgsoft/ambio/media/MixBundleTest.kt`

**Interfaces:**
- Consumes: `MixEntry(soundId: String, audioRes: Int, level: Float)`, `AudioFocus`, `SoundTrack` — all already in `media`.
- Produces:
  - `interface MixSource { suspend fun currentMix(): List<MixEntry> }`
  - `MixPlayer(looper: Looper, createTrack: (String) -> SoundTrack, audioFocus: AudioFocus, onPlayRequestedWithEmptyMix: () -> Unit)`

- [ ] **Step 1: Write the failing tests**

`MixPlayerTest` already has a `FakeTrack`, a `FakeFocus`, a `tracks` map and a `player()` helper. Add a counter beside them and pass it into `player()`:

```kotlin
    private var emptyPlayRequests = 0
```

and in the `player()` helper, pass `{ emptyPlayRequests++ }` as the fourth argument. Every existing test keeps working, because they all activate a sound before playing.

Then add:

```kotlin
    @Test
    fun `playing with no sounds does not request audio focus`() {
        val mix = player()

        mix.play()

        assertThat(focus.requests).isEqualTo(0)
    }

    @Test
    fun `playing with no sounds tells the service instead`() {
        val mix = player()

        mix.play()

        assertThat(emptyPlayRequests).isEqualTo(1)
    }

    @Test
    fun `playing with no sounds leaves the player paused`() {
        val mix = player()

        mix.play()

        assertThat(mix.playWhenReady).isFalse()
    }

    @Test
    fun `playing with sounds present still works and does not call back`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.play()

        assertThat(mix.playWhenReady).isTrue()
        assertThat(focus.requests).isEqualTo(1)
        assertThat(emptyPlayRequests).isEqualTo(0)
        assertThat(tracks["rain"]!!.paused).isFalse()
    }

    @Test
    fun `pausing with no sounds does not call back`() {
        val mix = player()

        mix.pause()

        assertThat(emptyPlayRequests).isEqualTo(0)
    }
```

> The fourth test is the one that stops this becoming a regression: it pins that the normal path is untouched. Without it, an implementation that never plays anything would pass the first three.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :media:testDebugUnitTest --tests '*MixPlayerTest*' 2>&1 | grep -E "^e: |BUILD|FAILED" | tail -5
```

Expected: compilation failure — the `player()` helper now passes four arguments to a three-argument constructor.

> Note the grep pattern: the Kotlin compiler emits `e: `, not `error:`. Earlier plans in this project used `error:` and produced bare `BUILD FAILED` lines with no diagnostic.

- [ ] **Step 3: Declare the interface**

Create `media/src/main/java/com/jbgsoft/ambio/media/MixSource.kt`:

```kotlin
package com.jbgsoft.ambio.media

/**
 * Where the service gets the mix to play when it starts with none.
 *
 * Declared here, over media's own [MixEntry], so this module keeps declaring no project
 * dependency at all — the stored mix lives behind SoundRepository in core:domain, which
 * media is not allowed to reach. Whoever can see both implements this; today that is
 * core:di.
 */
interface MixSource {
    /** Never empty in practice: the repository guarantees at least one active sound. */
    suspend fun currentMix(): List<MixEntry>
}
```

- [ ] **Step 4: Make `MixPlayer` refuse**

Add the callback to the constructor:

```kotlin
class MixPlayer(
    looper: Looper,
    private val createTrack: (soundId: String) -> SoundTrack,
    private val audioFocus: AudioFocus,
    private val onPlayRequestedWithEmptyMix: () -> Unit
) : SimpleBasePlayer(looper) {
```

and reorder the top of `handleSetPlayWhenReady`, so the emptiness check comes **before** the focus request:

```kotlin
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            // Before anything else, and specifically before asking for audio focus.
            // A play request can arrive from the Quick Settings tile with the app closed,
            // and this player then holds nothing: taking focus at that point silences
            // whatever the user was actually listening to, in order to play silence.
            // The service owns the fix — it can read the stored mix, and this class
            // deliberately cannot.
            if (entries.isEmpty()) {
                onPlayRequestedWithEmptyMix()
                return Futures.immediateVoidFuture()
            }
            if (!audioFocus.request()) return Futures.immediateVoidFuture()
            pausedByFocusLoss = false
            // A granted request means we hold full focus, so nothing is ducking us any
            // more. GAINED cannot be relied on to clear this: a permanent LOST abandons
            // the focus, so no GAINED will ever arrive, and without this the mix would
            // come back at 0.2x for good.
            duckMultiplier = 1f
            applyVolumes()
        } else {
            // A deliberate pause keeps the focus: abandoning and re-requesting on every
            // pause would let another app take our place while the user is deciding.
            pausedByFocusLoss = false
        }
        playWhenReadyValue = playWhenReady
        entries.values.forEach { if (playWhenReady) it.track.resume() else it.track.pause() }
        return Futures.immediateVoidFuture()
    }
```

- [ ] **Step 5: Fix the other test file that builds a `MixPlayer`**

`MixBundleTest.kt` constructs `MixPlayer` directly and will no longer compile. Pass `{}` as the fourth argument — that file tests bundle encoding, not playback, so an empty callback is correct there and not a shortcut.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew :media:testDebugUnitTest 2>&1 | grep -E "^e: |BUILD|FAILED" | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Prove the guard discriminates**

Delete the `if (entries.isEmpty())` block, re-run, and confirm that exactly the three empty-mix tests fail. Then restore it with `git checkout -- media/` and confirm green again. Record both outputs — a guard nobody has watched fail has not been tested.

- [ ] **Step 8: Commit**

```bash
git add media/
git commit -m "fix: do not take audio focus to play nothing

A play request can arrive from the Quick Settings tile with the app closed,
and MixPlayer then holds no sounds. It asked for audio focus first, so the
tap silenced whatever the user was listening to and then played silence.

The player now refuses and tells the service, which is the only side that
can read the stored mix."
```

---

## Task 2: The service loads the stored mix, or stops

**Files:**
- Create: `core/di/src/main/java/com/jbgsoft/ambio/core/di/MixSourceModule.kt`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/AudioService.kt`

**Interfaces:**
- Consumes: `MixSource`, `MixEntry`, `MixPlayer(looper, createTrack, audioFocus, onPlayRequestedWithEmptyMix)` (Task 1); `SoundRepository.getActiveMix(): Flow<List<ActiveSound>>` and `ActiveSound(sound, level)` from `core:domain`.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Implement and bind the source**

Create `core/di/src/main/java/com/jbgsoft/ambio/core/di/MixSourceModule.kt`:

```kotlin
package com.jbgsoft.ambio.core.di

import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.media.MixEntry
import com.jbgsoft.ambio.media.MixSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the stored mix into the media module.
 *
 * This lives in core:di because it is the only module that sees both sides:
 * SoundRepository in core:domain, and MixEntry in media. The media module declares no
 * project dependency at all, and this is what lets that stay true while the service
 * still gets a mix to play.
 */
@Singleton
class RepositoryMixSource @Inject constructor(
    private val soundRepository: SoundRepository
) : MixSource {
    override suspend fun currentMix(): List<MixEntry> =
        soundRepository.getActiveMix().first().map { active ->
            MixEntry(
                soundId = active.sound.id,
                audioRes = active.sound.audioRes,
                level = active.level
            )
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MixSourceModule {
    @Binds
    @Singleton
    abstract fun bindMixSource(impl: RepositoryMixSource): MixSource
}
```

> `RepositoryMixSource` sits in the same file as its module, following `RepositoryModule.kt`'s shape in this package.

- [ ] **Step 2: Wire it into the service**

In `AudioService.kt`, inject the source and give `MixPlayer` its callback. Add the field:

```kotlin
    @Inject
    lateinit var mixSource: MixSource

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
```

and change the construction at line 76:

```kotlin
        player = MixPlayer(
            mainLooper,
            { ExoPlayerSoundTrack(this) },
            AndroidAudioFocus(this),
            ::loadStoredMixAndPlay
        )
```

then add the handler:

```kotlin
    /**
     * Called when something asks this service to play while it holds no sounds — the
     * Quick Settings tile with the app closed, in practice.
     *
     * Starting the service is not enough on its own: it comes up empty, and an empty
     * player publishes an empty timeline, so Media3 shows no notification, so
     * startForeground() is never called, so the system kills the process with
     * ForegroundServiceDidNotStartInTimeException. Loading the mix is what closes that
     * chain.
     */
    private fun loadStoredMixAndPlay() {
        serviceScope.launch {
            val mix = mixSource.currentMix()
            if (mix.isEmpty()) {
                // Should not happen — the repository never yields an empty mix — but the
                // bug this guards against was a service waiting forever for a
                // startForeground that could not come. Stopping is the difference between
                // a no-op and a system-level crash.
                stopSelf()
                return@launch
            }
            // Empty title, deliberately: media cannot see sound names — they are string
            // resources in core:data, which this module is not allowed to reach — and the
            // notification's text is not what this fix is about. The app overwrites it with
            // a real title the moment it next pushes a mix.
            player.setMix(mix, "")
            player.play()
        }
    }
```

Cancel the scope in `onDestroy`, beside the existing teardown:

```kotlin
        serviceScope.cancel()
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :media:compileDebugKotlin :core:di:compileDebugKotlin :app:compileDebugKotlin 2>&1 | grep -E "^e: |BUILD|FAILED" | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Confirm the layering constraint still holds**

```bash
grep -c "project(" media/build.gradle.kts
```

Expected: `0`. If this is not zero, the fix has broken the constraint it was designed around and must be reworked, not patched.

- [ ] **Step 5: Commit**

```bash
git add media/ core/di/
git commit -m "fix: load the stored mix when the service starts with none

The tile starts AudioService, but starting it was never enough — it came up
empty and played silence until the system killed it for never calling
startForeground.

MixSource is declared in media over its own MixEntry type and implemented in
core:di, the one module that sees both SoundRepository and media, so media
still declares no project dependency."
```

---

## Task 3: Verify it on the emulator, because nothing else will

This project has no instrumented tests. Task 1's guard is covered by JVM tests; **everything else here is unverified until this task runs**, and the last branch shipped a claim that turned out false precisely because a device check was listed and never run.

**Files:** none, unless it finds something.

- [ ] **Step 1: Install on a running emulator**

```bash
adb devices
./gradlew installDebug
```

If no emulator is attached, say so and stop — do not start one, the controller manages it.

- [ ] **Step 2: Reproduce the original failure is gone**

```bash
adb shell am force-stop com.jbgsoft.ambio
adb logcat -c
adb shell cmd statusbar add-tile com.jbgsoft.ambio/com.jbgsoft.ambio.feature.tile.PlaybackTile
adb shell cmd statusbar expand-settings
sleep 2
adb shell cmd statusbar click-tile com.jbgsoft.ambio/com.jbgsoft.ambio.feature.tile.PlaybackTile
sleep 12
```

Then measure, and report each number:

```bash
PID=$(adb shell pidof com.jbgsoft.ambio | tr -d '\r'); echo "pid: ${PID:-none}"
adb shell dumpsys audio | grep "AudioPlaybackConfiguration" | grep "/$PID " | grep -c "state:started"
adb logcat -d | grep -c "ForegroundServiceDidNotStartInTime"
adb shell dumpsys media_session | grep -m1 "state=PlaybackState"
```

Expected: a live pid, **a non-zero count of started tracks**, **zero** `ForegroundServiceDidNotStartInTime`, and a `PlaybackState` that is not `NONE(0)`.

The `expand-settings` step is load-bearing — without it the tile is not listening and `click-tile` does not reproduce a real tap. That is why the previous attempt at this verification came back inconclusive.

- [ ] **Step 3: Check the warm path did not regress**

With audio now playing from step 2, click the tile again and confirm playback stops; click once more and confirm it resumes. Report the started-track count after each.

- [ ] **Step 4: Report**

State each measurement individually. If something fails, say what and stop rather than patching around it — a failure here means the design is wrong, not the code.

---

## Self-Review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| Cold tap plays the stored mix (criterion 1) | 2, verified in 3 |
| No focus request with an empty mix (criterion 2) | 1, three JVM tests |
| Service stops rather than hanging (criterion 3) | 2 step 2, verified in 3 |
| Warm path unchanged (criterion 4) | 1's fourth test + 3 step 3 |
| `media` declares no project dependency (criterion 5) | 2 step 4, checked mechanically |
| New logic covered by JVM tests (criterion 6) | 1 |
| Lint 0, warnings 2 (criterion 7) | the controller's gate |
| The `expand-settings` + `click-tile` recipe | 3 step 2 |

**Where this plan departs from the spec, and why.** The spec put `MixSource` in `MixPlayer`'s constructor and had the player load the mix itself through a deferred `ListenableFuture`. This plan gives `MixPlayer` a callback instead and puts the coroutine in `AudioService`. Same ordering, same outcomes, but `MixPlayer` stays synchronous — and its being trivially testable is the reason the audio-focus bug was catchable at all. Recorded here rather than done silently.

**Placeholder scan.** Clean after one fix: the first draft called `mixTitleFor(mix)`, a function defined in no task. The mix title is now an explicit empty string, with the reason in a comment — `media` cannot see sound names (they are string resources in `core:data`) and the notification's text is not what this fix is about.

**Type consistency.** `MixSource.currentMix(): List<MixEntry>` is declared in Task 1 and implemented in Task 2. `MixPlayer`'s fourth parameter is `onPlayRequestedWithEmptyMix: () -> Unit` in Task 1's constructor, Task 1's test helper, Task 1 step 5's `MixBundleTest` fix, and Task 2's `::loadStoredMixAndPlay` reference. `MixEntry(soundId, audioRes, level)` matches the existing declaration.

**What stays unverified.** Whether a real user tapping a real tile on real hardware behaves as the emulator does. Everything else in this plan is either a JVM test or a measured emulator check.
