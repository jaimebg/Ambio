# Widget and Quick Settings tile — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pause and resume the sound mix from the home screen and from Quick Settings, without opening the app — including when the app has not been opened since boot.

**Architecture:** Neither surface builds a `MediaController`. Both send an `ACTION_MEDIA_BUTTON` intent to Media3's `MediaButtonReceiver`, which routes it to `AudioService` and starts the service if it is not running. The widget's content comes from a pure `widgetDisplay(mix, isPlaying)` function so the only logic here is JVM-testable; the Glance composable is a thin renderer over it.

**Tech Stack:** Kotlin 2.3.21, Glance 1.1.1, Media3 1.10.1 (`MediaButtonReceiver`), Hilt 2.60.1, DataStore, `TileService`.

**Spec:** `docs/superpowers/specs/2026-08-05-widget-and-tile-design.md`

## Global Constraints

- **Neither the widget nor the tile may build a `MediaController`.** Both are ephemeral processes; an async `buildAsync` can outlive them and fail intermittently. Playback control goes through `ACTION_MEDIA_BUTTON` only.
- **The widget shows no per-sound icons.** `Sound.icon` is a Compose `ImageVector` and Glance cannot render one — its `Image` takes an `ImageProvider` over a drawable or bitmap. Do not add drawables to work around this; the spec ruled it out.
- **The mix title uses the app's existing rule**, so all three surfaces say the same thing: names joined with `" + "` up to two sounds, `mix_sound_count` from three. `HomeViewModel.mixTitle` (`feature/home/.../HomeViewModel.kt:243-248`) is the reference.
- **The palette comes from `mixPalettes(themes)`.** Do not reimplement or approximate it — it is verified against WCAG AA across all 31 combinations.
- **`AudioService` must broadcast "not playing" as it is destroyed.** Glance keeps the last state written; without this the widget shows a pause button over a service that no longer exists.
- **`media` must not gain a dependency on any feature module.** It declares no project dependencies at all today, and `feature:widget` pulls `core:domain` — which `media` has been kept clear of across three branches. The service broadcasts; the widget module listens.
- Lint stays at 0 errors. **Kotlin compiler warnings stay at 2** (both pre-existing in `ui/theme/Theme.kt`). Measure with `--rerun-tasks --no-build-cache`.
- No hardcoded user-facing strings. Verify with `grep -rnE 'Text\("|text = "|contentDescription = "' feature/widget/src/main`.
- **This project has no instrumented tests and will not get any.** Everything except `widgetDisplay()` is verified by hand on an emulator (Task 5). Do not propose adding UI tests.
- Build: `./gradlew assembleDebug` · Lint: `./gradlew lint` · Tests: `./gradlew test`

## Facts verified by execution, not by reading

- **Glance `1.1.1` resolves against this toolchain.** Probed by temporarily adding `androidx.glance:glance-appwidget:1.1.1` to `feature:stats` and running `:feature:stats:dependencies --configuration debugCompileClasspath`; it resolved to `glance-appwidget:1.1.1 → glance:1.1.1` with no conflict, and the probe was reverted. 1.1.1 is the newest **stable** release: 1.2.0 only reaches `rc01` and 1.3.0 only `alpha02`. Resolution is not compilation — Task 1 is what proves it compiles under Kotlin 2.3.21.
- **`androidx.media3.session.MediaButtonReceiver` exists in Media3 1.10.1** and exposes `handleIntentAndMaybeStartTheService(Context, Intent)` — starting the service is its documented job, which is what makes the cold-start case work with no special path.
- **`feature` modules in this project have no manifest of their own**; everything is declared in `app/src/main/AndroidManifest.xml`. This module is the exception, by necessity — see the File Structure note.
- `StringProvider` (`core:common`) is how this project resolves strings outside Compose. It is a Hilt `@Singleton`.
- The library convention plugin sets `compileSdk = 37`, `minSdk = 31`, Java 17. `TileService` needs API 24 and Glance API 21, so no compatibility guards are required.

---

## File Structure

**New module `feature:widget`**

| File | Responsibility |
|---|---|
| `feature/widget/build.gradle.kts` | Module setup |
| `feature/widget/src/main/AndroidManifest.xml` | Declares the widget receiver, the tile service, and Media3's `MediaButtonReceiver` |
| `.../widget/WidgetDisplay.kt` | The pure function — all the logic, all the tests |
| `.../widget/PlayPauseIntent.kt` | Builds the one intent both surfaces send |
| `.../widget/PlaybackTile.kt` | The Quick Settings tile |
| `.../widget/AmbioWidget.kt` | The Glance widget: reads state, renders `WidgetDisplay` |
| `.../widget/AmbioWidgetReceiver.kt` | Glance's `AppWidgetProvider` |
| `.../widget/WidgetUpdater.kt` | The single entry point for "refresh the widget" |
| `.../widget/PlaybackStateReceiver.kt` | Turns the service's playback broadcast into a refresh |
| `feature/widget/src/main/res/xml/widget_info.xml` | Size and metadata |
| `feature/widget/src/test/.../WidgetDisplayTest.kt` | JVM tests |

**Modified** — `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `media/.../AudioService.kt` (broadcast only, no new module dependency), `app/.../AmbioApp.kt` and `AmbioAppViewModel.kt` — the latter exposes only `palette` today and needs the mix itself, since two different mixes can share a palette while having different titles.

---

## Task 1: The module, and the only logic worth testing

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `feature/widget/build.gradle.kts`
- Create: `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/WidgetDisplay.kt`
- Create: `feature/widget/src/main/res/values/strings.xml`
- Create: `feature/widget/src/test/java/com/jbgsoft/ambio/feature/widget/WidgetDisplayTest.kt`

**Interfaces:**
- Consumes: `ActiveSound(sound, level)`, `Sound(id, nameRes, icon, audioRes, illustrationRes, theme)`, `SoundTheme`, `AmbioPalette`, `mixPalettes(themes: List<SoundTheme>): AmbioPalette` — all in `com.jbgsoft.ambio.core.domain.model`.
- Produces:
  - `data class WidgetDisplay(val title: String, val palette: AmbioPalette, val isPlaying: Boolean)`
  - `fun widgetDisplay(mix: List<ActiveSound>, isPlaying: Boolean, names: (Int) -> String, countLabel: (Int) -> String): WidgetDisplay`

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, beside the other feature modules:

```kotlin
include(":feature:widget")
```

In `gradle/libs.versions.toml` under `[versions]`:

```toml
glance = "1.1.1"
```

under `[libraries]`:

```toml
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

Create `feature/widget/build.gradle.kts`, following `feature/stats/build.gradle.kts`:

```kotlin
plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.feature.widget"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Glance
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Media3, for MediaButtonReceiver and the media-button intent
    implementation(libs.bundles.media3)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)
}
```

> Glance 1.1.1 was verified to *resolve* against this toolchain, not to compile under Kotlin 2.3.21's Compose compiler. Step 4 is what proves that. If it fails, the ordered fallback is `1.2.0-rc01`, then `1.3.0-alpha02`; record which you used and the error that forced it.

- [ ] **Step 2: Write the failing test**

Create `feature/widget/src/test/java/com/jbgsoft/ambio/feature/widget/WidgetDisplayTest.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import org.junit.Test

class WidgetDisplayTest {

    private val names = mapOf(1 to "Rain", 2 to "Fireplace", 3 to "Forest")

    private fun sound(id: String, nameRes: Int, theme: SoundTheme) = Sound(
        id = id,
        nameRes = nameRes,
        icon = Icons.Default.WaterDrop,
        audioRes = 0,
        illustrationRes = 0,
        theme = theme
    )

    private val rain = sound("rain", 1, SoundTheme.RAIN)
    private val fireplace = sound("fireplace", 2, SoundTheme.FIREPLACE)
    private val forest = sound("forest", 3, SoundTheme.FOREST)

    private fun display(mix: List<ActiveSound>, isPlaying: Boolean = false) =
        widgetDisplay(
            mix = mix,
            isPlaying = isPlaying,
            names = { names.getValue(it) },
            countLabel = { "$it sounds" }
        )

    @Test
    fun `one sound shows its name`() {
        val d = display(listOf(ActiveSound(rain, 1f)))

        assertThat(d.title).isEqualTo("Rain")
    }

    @Test
    fun `two sounds are joined with a plus`() {
        val d = display(listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f)))

        assertThat(d.title).isEqualTo("Rain + Fireplace")
    }

    @Test
    fun `three or more sounds show a count instead of names`() {
        val d = display(
            listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f), ActiveSound(forest, 1f))
        )

        assertThat(d.title).isEqualTo("3 sounds")
    }

    @Test
    fun `the palette is the mix of the active sounds' themes`() {
        val d = display(listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f)))

        assertThat(d.palette).isEqualTo(mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE)))
    }

    @Test
    fun `a single sound keeps its own hand-tuned palette`() {
        val d = display(listOf(ActiveSound(rain, 1f)))

        assertThat(d.palette).isEqualTo(mixPalettes(listOf(SoundTheme.RAIN)))
    }

    @Test
    fun `the volume levels do not affect the palette`() {
        val loud = display(listOf(ActiveSound(rain, 1f), ActiveSound(fireplace, 1f)))
        val quiet = display(listOf(ActiveSound(rain, 0.1f), ActiveSound(fireplace, 0.9f)))

        assertThat(quiet.palette).isEqualTo(loud.palette)
    }

    @Test
    fun `the playing flag is carried through`() {
        assertThat(display(listOf(ActiveSound(rain, 1f)), isPlaying = true).isPlaying).isTrue()
        assertThat(display(listOf(ActiveSound(rain, 1f)), isPlaying = false).isPlaying).isFalse()
    }

    @Test
    fun `an empty mix falls back to the default palette rather than crashing`() {
        // The repository guarantees a non-empty mix, but the widget can render before
        // DataStore has been read on a cold start, and a widget must never crash the
        // launcher.
        val d = display(emptyList())

        assertThat(d.title).isEmpty()
        assertThat(d.palette).isEqualTo(mixPalettes(listOf(SoundTheme.RAIN)))
    }
}
```

> The last test matters more than it looks. `mixPalettes` requires a non-empty list and throws otherwise; a widget that throws takes the launcher's rendering with it. This pins the guard.

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :feature:widget:testDebugUnitTest --tests '*WidgetDisplayTest*'
```

Expected: compilation failure — `Unresolved reference: widgetDisplay`.

- [ ] **Step 4: Write the implementation**

Create `feature/widget/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="widget_sound_count">%1$d sounds</string>
    <string name="widget_play">Play</string>
    <string name="widget_pause">Pause</string>
    <string name="widget_description">Play or pause your sound mix</string>
</resources>
```

Create `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/WidgetDisplay.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes

/** Everything the widget draws, and nothing about how it is drawn. */
data class WidgetDisplay(
    val title: String,
    val palette: AmbioPalette,
    val isPlaying: Boolean
)

/**
 * The widget's only logic, deliberately kept as a pure function.
 *
 * This project has no instrumented tests, so nothing will ever check that the widget
 * paints. Keeping the decisions here — what it says, what colour it is — means the part
 * that can be wrong is the part that is covered.
 *
 * [names] and [countLabel] are passed in rather than resolved here so this stays free of
 * Android: the callers hand it StringProvider or stringResource.
 */
fun widgetDisplay(
    mix: List<ActiveSound>,
    isPlaying: Boolean,
    names: (Int) -> String,
    countLabel: (Int) -> String
): WidgetDisplay {
    // A widget renders before DataStore has been read on a cold start, and mixPalettes
    // rejects an empty list. A widget that throws takes the launcher's rendering with it.
    if (mix.isEmpty()) {
        return WidgetDisplay(
            title = "",
            palette = mixPalettes(listOf(SoundTheme.RAIN)),
            isPlaying = isPlaying
        )
    }

    // The same rule as HomeViewModel.mixTitle and the media notification, so all three
    // surfaces say the same thing.
    val title = if (mix.size <= 2) {
        mix.joinToString(" + ") { names(it.sound.nameRes) }
    } else {
        countLabel(mix.size)
    }

    return WidgetDisplay(
        title = title,
        palette = mixPalettes(mix.map { it.sound.theme }),
        isPlaying = isPlaying
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew :feature:widget:testDebugUnitTest --tests '*WidgetDisplayTest*'
```

Expected: PASS, 8 tests. **This is also the step that proves Glance 1.1.1 compiles under this toolchain** — the module now builds with it on the classpath. If compilation fails inside Glance rather than in your code, take the fallback from step 1 and record it.

- [ ] **Step 6: Verify the whole project still builds**

```bash
./gradlew test lint assembleDebug --rerun-tasks --no-build-cache 2>&1 | grep -E "^w:|BUILD|FAILED" | sort -u
```

Expected: BUILD SUCCESSFUL, lint 0 errors, exactly 2 distinct `w:` lines, both in `ui/theme/Theme.kt`.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml feature/widget/
git commit -m "feat: add the widget module and its display logic

The only logic the widget has is a pure function, because this project has
no instrumented tests and nothing will ever check that the widget paints.
What can be wrong is what is covered."
```

---

## Task 2: The play/pause intent, and the Quick Settings tile

The tile is smaller than the widget and shares its control path, so it comes first: it proves the intent actually reaches the service before any Glance code exists to confuse the diagnosis.

**Files:**
- Create: `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/PlayPauseIntent.kt`
- Create: `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/PlaybackTile.kt`
- Create: `feature/widget/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `fun playPauseIntent(context: Context): Intent`
  - `class PlaybackTile : TileService`

- [ ] **Step 1: Write the intent builder**

Create `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/PlayPauseIntent.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.media3.session.MediaButtonReceiver

/**
 * The one intent both the widget and the tile send.
 *
 * Neither surface builds a MediaController: they are ephemeral processes, and
 * MediaController.buildAsync can outlive them, which fails intermittently rather than
 * cleanly. A media-button intent needs no connection at all — Media3's MediaButtonReceiver
 * routes it to AudioService and starts the service if it is not already running, which is
 * what makes pressing play work hours after the app was last opened.
 */
fun playPauseIntent(context: Context): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        setClass(context, MediaButtonReceiver::class.java)
        putExtra(
            Intent.EXTRA_KEY_EVENT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }
```

- [ ] **Step 2: Write the tile**

Create `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/PlaybackTile.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings toggle for the mix.
 *
 * The label is the app's name rather than the mix's: a tile is too narrow for
 * "Rain + Fireplace", and the system truncates without warning.
 */
class PlaybackTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        sendBroadcast(playPauseIntent(this))
        // The tile flips immediately rather than waiting for the service to come up and
        // report back: the user tapped it, and a control that lags its own tap reads as
        // broken. The next onStartListening corrects it if the service disagreed.
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (WidgetUpdater.isPlaying(this@PlaybackTile)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}
```

> `WidgetUpdater.isPlaying(context)` arrives in Task 4. Until then this will not compile — that is expected and step 4 says so. Do not stub it with a literal to make the build pass; a stub that returns `false` looks identical to a working tile that is never playing, and it would survive review.

- [ ] **Step 3: Declare both components**

Create `feature/widget/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>

        <!-- Media3 routes ACTION_MEDIA_BUTTON here and starts AudioService if needed.
             Declared in this module rather than in app/ so the declaration sits beside
             the code that sends to it. -->
        <receiver
            android:name="androidx.media3.session.MediaButtonReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MEDIA_BUTTON" />
            </intent-filter>
        </receiver>

        <service
            android:name=".PlaybackTile"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

> `@string/app_name` and `@mipmap/ic_launcher` live in `app`, not here. `android.nonTransitiveRClass=true` forbids cross-module *code* references to another module's resources, but **manifest** references are resolved by the merger against the merged resource set, so this works. If the merger rejects it, the fallback is a local `widget_tile_label` string in this module — record which you needed.

In `app/build.gradle.kts`'s `dependencies`, add the module so its manifest is merged in:

```kotlin
    implementation(project(":feature:widget"))
```

- [ ] **Step 4: Confirm it does not build yet, for the right reason**

```bash
./gradlew :feature:widget:compileDebugKotlin 2>&1 | grep -E "error:|BUILD" | head -5
```

Expected: FAILURE with `Unresolved reference: WidgetUpdater`. That is the only error you should see. If anything else fails — the manifest merger, Glance, the Media3 import — fix that now; it will be much harder to diagnose once the widget exists too.

- [ ] **Step 5: Commit**

Do not commit yet. `feature:widget` does not compile until Task 4 supplies `WidgetUpdater`, and a commit that does not build is worse than a larger one. Task 4 commits Tasks 2, 3 and 4 together; note that in its message.

---

## Task 3: The Glance widget

**Files:**
- Create: `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/AmbioWidget.kt`
- Create: `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/AmbioWidgetReceiver.kt`
- Create: `feature/widget/src/main/res/xml/widget_info.xml`
- Modify: `feature/widget/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `widgetDisplay(...)` and `WidgetDisplay` (Task 1), `playPauseIntent(context)` (Task 2), `WidgetUpdater.currentDisplay(context)` (Task 4).
- Produces: `object AmbioWidget : GlanceAppWidget`, `class AmbioWidgetReceiver : GlanceAppWidgetReceiver`.

- [ ] **Step 1: Declare the widget's size**

Create `feature/widget/src/main/res/xml/widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:minWidth="250dp"
    android:minHeight="50dp"
    android:resizeMode="horizontal"
    android:targetCellWidth="4"
    android:targetCellHeight="1"
    android:widgetCategory="home_screen"
    android:initialLayout="@layout/glance_default_loading_layout" />
```

> `@layout/glance_default_loading_layout` comes from the Glance library itself; it is what the launcher shows while Glance composes. Referencing it is the documented setup, not a placeholder.

- [ ] **Step 2: Write the widget**

Create `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/AmbioWidget.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight

/**
 * A 4x1 widget: the mix's name, and one button.
 *
 * It renders a [WidgetDisplay] and decides nothing itself — every question of what it says
 * or what colour it is was already answered by [widgetDisplay], where it is testable.
 */
object AmbioWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = WidgetUpdater.currentDisplay(context)
        provideContent { Content(context, display) }
    }

    @Composable
    private fun Content(context: Context, display: WidgetDisplay) {
        val onBackground = ColorProvider(display.palette.onPrimary)
        GlanceTheme {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(display.palette.surface))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 16.dp)
                    .clickable(actionSendBroadcast(playPauseIntent(context))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display.title,
                    style = TextStyle(
                        color = ColorProvider(display.palette.primary),
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = context.getString(
                        if (display.isPlaying) R.string.widget_pause else R.string.widget_play
                    ),
                    style = TextStyle(color = onBackground, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
```

> The button is text, not an icon, for the same reason the sounds have no icons: Glance cannot render a Compose `ImageVector`, and the spec ruled out adding drawables for the widget alone.

Create `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/AmbioWidgetReceiver.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AmbioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AmbioWidget
}
```

- [ ] **Step 3: Declare the widget**

Add to `feature/widget/src/main/AndroidManifest.xml`, inside `<application>`:

```xml
        <receiver
            android:name=".AmbioWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_info" />
        </receiver>
```

- [ ] **Step 4: Confirm the only remaining error is still the missing updater**

```bash
./gradlew :feature:widget:compileDebugKotlin 2>&1 | grep -E "error:|BUILD" | head -5
```

Expected: FAILURE, and every error should name `WidgetUpdater`. Anything else — a Glance API that does not exist at 1.1.1, a missing import — is real and must be fixed here.

- [ ] **Step 5: Commit**

Still nothing to commit; Task 4 closes the module and commits all three together.

---

## Task 4: Wiring the updates

**Files:**
- Create: `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/WidgetUpdater.kt`
- Modify: `media/src/main/java/com/jbgsoft/ambio/media/AudioService.kt`
- Modify: the composable that observes the mix in `app` (`AmbioApp.kt`)
- Modify: `app/build.gradle.kts` if the dependency from Task 2 is not already there

**Interfaces:**
- Consumes: `widgetDisplay(...)` (Task 1), `SoundRepository.getActiveMix(): Flow<List<ActiveSound>>`, `StringProvider`.
- Produces:
  - `suspend fun WidgetUpdater.setPlaying(context: Context, playing: Boolean)`
  - `fun WidgetUpdater.isPlaying(context: Context): Boolean`
  - `suspend fun WidgetUpdater.currentDisplay(context: Context): WidgetDisplay`
  - `suspend fun WidgetUpdater.refresh(context: Context)`

- [ ] **Step 1: Write the updater**

Create `feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/WidgetUpdater.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.jbgsoft.ambio.core.common.resources.StringProvider
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * The single place anything says "the widget is out of date".
 *
 * Two callers, each covering what only it knows: AudioService when playback starts or stops,
 * and the app when the mix changes. The service cannot own the second — pushMix() does not
 * reach it when the controller is null, so a mix edited while the service is dead would never
 * arrive. The mix is only ever written by the app's UI, so the app is always alive when it
 * changes.
 */
object WidgetUpdater {

    private const val PREFS = "ambio_widget"
    private const val KEY_PLAYING = "is_playing"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun soundRepository(): SoundRepository
        fun stringProvider(): StringProvider
    }

    // GlanceAppWidget is not an Android component, so Hilt cannot inject it. This is the
    // documented way in: reach the singleton graph through the Context it is handed.
    private fun entryPoint(context: Context): WidgetEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Defaults to false, which is what a fresh boot with no service should show. */
    fun isPlaying(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PLAYING, false)

    suspend fun setPlaying(context: Context, playing: Boolean) {
        prefs(context).edit().putBoolean(KEY_PLAYING, playing).apply()
        refresh(context)
    }

    suspend fun currentDisplay(context: Context): WidgetDisplay {
        val ep = entryPoint(context)
        val mix = ep.soundRepository().getActiveMix().first()
        val strings = ep.stringProvider()
        return widgetDisplay(
            mix = mix,
            isPlaying = isPlaying(context),
            names = { strings.get(it) },
            countLabel = { strings.get(R.string.widget_sound_count, it) }
        )
    }

    suspend fun refresh(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(AmbioWidget::class.java).forEach { id ->
            AmbioWidget.update(context, id)
        }
    }
}
```

> `SharedPreferences` rather than DataStore, deliberately: `isPlaying` is read synchronously from `TileService.onStartListening`, which cannot suspend. Do not "improve" this into DataStore — it would force a coroutine into a callback that has none, and the value is transient process state, not a user preference.

- [ ] **Step 2: Push playback state from the service, without inverting the layering**

`AudioService` is the only thing that knows playback stopped once the app's UI is dead, so it
has to emit something. **It must not gain a dependency on `feature:widget` to do it.** The
`media` module today declares *no project dependencies at all*, and `feature:widget` pulls
`core:domain` and `core:common` — adding it would give `media` a transitive dependency on
`core:domain`, which three branches of this project have enforced against.

The dependency goes the other way, which is the direction already in use: `feature:home`
depends on `:media`, so `feature:widget` doing the same is nothing new.

In `media/src/main/java/com/jbgsoft/ambio/media/AudioService.kt`, add a constant the service
owns and a listener that broadcasts:

```kotlin
    companion object {
        /**
         * Broadcast when playback starts or stops, and once more as the service dies.
         *
         * A broadcast rather than a direct call because media must not depend on any
         * feature module: it declares no project dependencies at all today, and the widget
         * module pulls core:domain, which this module is not allowed to reach.
         */
        const val ACTION_PLAYBACK_CHANGED = "com.jbgsoft.ambio.PLAYBACK_CHANGED"
        const val EXTRA_IS_PLAYING = "is_playing"
    }

    private fun broadcastPlayback(isPlaying: Boolean) {
        sendBroadcast(
            Intent(ACTION_PLAYBACK_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
        )
    }

    private val playbackBroadcaster = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = broadcastPlayback(isPlaying)
    }
```

`setPackage(packageName)` keeps it inside the app; an implicit broadcast without it would be
both a leak and, on modern Android, silently undelivered.

Register the listener in `onCreate`, after the player is built:

```kotlin
        player.addListener(playbackBroadcaster)
```

and in `onDestroy`, **before** releasing anything:

```kotlin
        // The last thing this service says. Without it the widget keeps showing a pause
        // button over a service that no longer exists, and keeps showing it forever.
        broadcastPlayback(false)
```

Then, in `feature/widget`, add `implementation(project(":media"))` to its `build.gradle.kts`
and create the receiver at
`feature/widget/src/main/java/com/jbgsoft/ambio/feature/widget/PlaybackStateReceiver.kt`:

```kotlin
package com.jbgsoft.ambio.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jbgsoft.ambio.media.AudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Turns the service's playback broadcast into a widget refresh. */
class PlaybackStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AudioService.ACTION_PLAYBACK_CHANGED) return
        val playing = intent.getBooleanExtra(AudioService.EXTRA_IS_PLAYING, false)
        // goAsync() because updating a Glance widget suspends, and a receiver that returns
        // before its work finishes has its process eligible for death mid-update.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.setPlaying(context.applicationContext, playing)
            } finally {
                pending.finish()
            }
        }
    }
}
```

and declare it in `feature/widget/src/main/AndroidManifest.xml`, inside `<application>`:

```xml
        <receiver
            android:name=".PlaybackStateReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.jbgsoft.ambio.PLAYBACK_CHANGED" />
            </intent-filter>
        </receiver>
```

> The action appears twice — as a constant in `AudioService` and as a literal in this
> manifest — because a manifest cannot reference a Kotlin constant. Keep them identical; if
> you change one, change the other. There is no test that will catch a mismatch, only a
> widget that silently stops updating.

- [ ] **Step 3: Refresh on mix change from the app**

In `app`, wherever the mix is already observed for the theme (`AmbioAppViewModel` exposes it), add a collector that refreshes the widget. In `AmbioApp.kt`, beside the existing palette collection:

```kotlin
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.activeMix.collect { WidgetUpdater.refresh(context) }
    }
```

If `AmbioAppViewModel` exposes only the palette and not the mix, add a `activeMix: StateFlow<List<ActiveSound>>` beside it from the same `getActiveMix()` use case rather than deriving it from the palette — two sounds can share a palette, and the title must still update.

- [ ] **Step 4: Build the whole project**

```bash
./gradlew test lint assembleDebug --rerun-tasks --no-build-cache 2>&1 | grep -E "^w:|BUILD|FAILED|error:" | sort -u
```

Expected: BUILD SUCCESSFUL, lint 0 errors, exactly 2 distinct `w:` lines, both in `ui/theme/Theme.kt`.

- [ ] **Step 5: Check no string was hardcoded**

```bash
grep -rnE 'Text\("|text = "|contentDescription = "' feature/widget/src/main
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add feature/widget/ media/ app/
git commit -m "feat: add the home-screen widget and Quick Settings tile

Neither builds a MediaController. Both send an ACTION_MEDIA_BUTTON intent to
Media3's MediaButtonReceiver, which routes it to AudioService and starts the
service if it is not running — so play works hours after the app was last
opened, with no special cold-start path.

Two things update the widget, each covering what only it knows: the service
on play state, the app on mix changes. The service pushes 'not playing' as it
dies, or the widget would keep showing a pause button over nothing.

Tasks 2, 3 and 4 land together: the module does not compile until all three
are present."
```

---

## Task 5: Verify it on a device, because nothing else will

This project has no instrumented tests and will not get any. Everything except `widgetDisplay()` is unverified until this task runs. **It is a task, not a formality** — the branch is not done without it.

**Files:** none. This task changes nothing unless it finds something.

- [ ] **Step 1: Start an emulator and install**

```bash
$ANDROID_HOME/emulator/emulator -list-avds
nohup $ANDROID_HOME/emulator/emulator -avd <name> -no-snapshot-load -no-boot-anim > /dev/null 2>&1 &
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do sleep 5; done
./gradlew installDebug
```

- [ ] **Step 2: Work through the checklist, recording each result**

1. **Add the widget** to the home screen. It appears at 4×1 and shows the stored mix's name.
2. **With the app closed and the service dead** (`adb shell am force-stop com.jbgsoft.ambio`), press play on the widget. Audio starts, and the widget flips to showing "Pause". This is the criterion the whole design exists for.
3. Press it again. Audio stops and the label returns to "Play".
4. **Add the Quick Settings tile** and tap it. Playback toggles, and the tile lights up and dims with it.
5. **Change the mix in the app** — add a sound. The widget's title updates without touching it.
6. **Kill the service while playing** (`adb shell am force-stop com.jbgsoft.ambio`). The widget must stop showing "Pause". This is the one the spec warns about: without the on-destroy push it will keep lying.
7. With three or more sounds active, the widget shows the count, not a truncated list of names.
8. The widget's colours change with the mix, and the text stays readable on every mix you try.

Record the actual result of each, including anything that only half-worked. **A checklist reported as "all fine" without per-item results is not evidence** — this branch exists because a spec's device checks were listed in a PR and never run.

- [ ] **Step 3: Report, and fix or record**

If everything passes, say so item by item. If something fails, fix it and re-run the affected items — except if the failure is in the design rather than the code, in which case stop and report rather than patching around it.

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: <what the device check found>"
```

If nothing needed fixing, there is nothing to commit and that is a valid outcome — say so.

---

## Self-Review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| Play/pause without a `MediaController` | 2 (`playPauseIntent`) |
| Works with the service stopped, starting it | 2 + verified in 5, step 2 |
| Widget shows the mix and whether it is playing | 1 + 3 |
| Mix from DataStore, play state pushed by the service | 4 |
| Service pushes "not playing" on destroy | 4, step 2 + verified in 5, step 6 |
| App pushes on mix change (service may be dead) | 4, step 3 |
| `feature:widget` module with its own manifest | 1 + 2 |
| Glance for the widget, plain `TileService` for the tile | 2 + 3 |
| Palette from `mixPalettes`, not reimplemented | 1 |
| No sound icons (Glance cannot render `ImageVector`) | 1 + 3 |
| Title rule matches the app and the notification | 1 |
| Widget is 4×1, `minWidth` 250dp / `minHeight` 50dp | 3 |
| Tile labelled with the app name, `STATE_ACTIVE`/`INACTIVE` | 2 |
| `widgetDisplay()` covered by JVM tests | 1 |
| Lint 0, warnings 2, no hardcoded strings | 1 step 6, 4 steps 4-5 |
| Manual device verification | 5 |

**Placeholder scan.** One deliberate near-miss: Task 5 step 4's commit message contains `<what the device check found>`. That is a value only the run can supply, and the step says explicitly that "nothing to commit" is a valid outcome. Everything else is concrete.

**Type consistency.** `widgetDisplay(mix, isPlaying, names, countLabel)` is used with exactly that shape in Task 1's tests and in Task 4's `currentDisplay`. `WidgetDisplay(title, palette, isPlaying)` is constructed in Task 1 and read in Task 3. `WidgetUpdater.isPlaying/setPlaying/currentDisplay/refresh` are declared in Task 4 and called from Tasks 2, 3 and 4.

**Three risks this plan names rather than hides**

1. **Glance 1.1.1 is verified to resolve, not to compile.** It is the newest stable, and this project runs Kotlin 2.3.21 with a much newer Compose. Task 1 step 5 is the real check, with an ordered fallback.
2. **The manifest references `@string/app_name` and `@mipmap/ic_launcher` from `app`.** Manifest references resolve against the merged resource set, unlike code references under `nonTransitiveRClass`. If the merger disagrees, Task 2 says to add a local string instead.
3. **The playback action string exists twice** — as a constant in `AudioService` and as a literal in `feature:widget`'s manifest, because a manifest cannot reference a Kotlin constant. Nothing will catch a mismatch; the symptom would be a widget that silently stops updating. This was the alternative to letting `media` depend on `feature:widget`, which would have given `media` a transitive dependency on `core:domain` and broken a constraint three branches have held.

**Deliberately not covered.** Nothing verifies that Glance paints correctly, that the `PendingIntent` arrives, or that the tile responds, except a person following Task 5. That is a consequence of this project having no instrumented tests, stated here so the gap is visible rather than assumed away.
