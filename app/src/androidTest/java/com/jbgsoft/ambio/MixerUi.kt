package com.jbgsoft.ambio

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Drives the real app the way a person would, through UiAutomator. */
object MixerUi {

    // Not BuildConfig: see the note on AudioState.targetPackage.
    private val PACKAGE: String get() = AudioState.targetPackage
    private const val TIMEOUT = 10_000L

    // Generous relative to TIMEOUT: this is waiting on a cold DataStore disk read plus
    // Hilt/Compose startup, not a click response, and a cold CI emulator is slower than
    // a warm local one.
    private const val HYDRATION_TIMEOUT = 20_000L

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    val soundNames = listOf("Rain", "Fireplace", "Forest", "Ocean", "Cave")

    // Matches any "Remove <sound> from the mix" card, whichever sound(s) happen to be
    // active - see the note on awaitMixHydration() for why the identity of the sound
    // doesn't matter here, only that one exists.
    private val anyRemoveCard: Pattern = Pattern.compile("^Remove .+ from the mix$")

    // Matches any picker card at all, active or not - evidence the sheet is genuinely
    // showing its grid, as opposed to a tap having merely been dispatched at it.
    private val anyMixCard: Pattern = Pattern.compile("^(Add|Remove) .+ (to|from) the mix$")

    fun clearAppData() {
        AudioState.shell("pm clear $PACKAGE")
    }

    /**
     * Launches the app and waits until its mix has actually hydrated, not just until its
     * window exists.
     *
     * `SoundRepositoryImpl.getActiveMix()` is a `combine` over a `MutableStateFlow` and
     * the DataStore-backed preferences flow; `combine` produces nothing until *both* have
     * emitted at least once, and the DataStore side needs an async disk read to do that.
     * Until then `HomeViewModel` holds `activeMix = emptyList()` (its seed value), so a
     * test that starts interacting the moment the root window appears can land clicks on
     * a UI that is still showing that empty seed - on a fast run this never shows up, on
     * a slow one it produces a `was: 0` failure several calls away from the real cause.
     *
     * The wait can't be a fixed sleep - there's no duration that's both fast on a warm
     * local emulator and safe on a slower, colder CI one - so instead this waits for
     * direct evidence the real mix arrived: the picker sheet showing at least one
     * "Remove <sound> from the mix" card. The repository guarantees the mix is never
     * empty (see its own comment), so once hydration completes there is always at least
     * one such card, regardless of which sound(s) ended up active. Neither the root
     * window nor the "Change" button work as that signal - both exist unconditionally,
     * before and after hydration.
     */
    fun launchApp() {
        AudioState.shell("am start -n $PACKAGE/.MainActivity")
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT)
        awaitMixHydration()
    }

    private fun awaitMixHydration() {
        openSoundPicker()

        val hydrated = device.wait(Until.hasObject(By.desc(anyRemoveCard)), HYDRATION_TIMEOUT)
        check(hydrated) {
            "MixerUi.launchApp(): the mix never hydrated - no 'Remove ... from the mix' " +
                "card appeared within ${HYDRATION_TIMEOUT}ms of opening the picker"
        }

        closeSoundPicker()
    }

    /**
     * Taps "Change" and confirms the sheet actually opened before returning, retrying
     * the tap if it didn't.
     *
     * A tap on "Change" can be dispatched successfully by UiAutomator's standards and
     * still not open anything: it can land on the scrim of this same sheet still
     * finishing a previous dismissal, or race the sheet's own opening animation. Either
     * way the click "succeeds" with nothing to show for it, and every caller downstream
     * — scrolling a grid that was never there, searching for a card that will never
     * appear — pays for that several seconds later with no clue the tap was the actual
     * problem. Confirming a card is visible, and retrying when it isn't, catches it here
     * instead.
     */
    fun openSoundPicker(attempts: Int = 3) {
        repeat(attempts) {
            device.wait(Until.findObject(By.text("Change")), TIMEOUT)?.click()
            if (device.wait(Until.hasObject(By.desc(anyMixCard)), 3_000)) return
        }
        error("MixerUi.openSoundPicker(): the sheet never showed a card after $attempts attempts")
    }

    /**
     * Dismisses the sheet and confirms the home screen is actually reachable again
     * before returning.
     *
     * The counterpart to [openSoundPicker] for the same reason: a caller that presses
     * on right after `pressBack()` can find every affordance it looks for missing, not
     * because the app broke but because the dismissal hadn't actually finished landing.
     * "Change" only being findable once this window is active again is what's confirmed
     * here, the same signal [openSoundPicker] waits for on the way in.
     */
    fun closeSoundPicker() {
        device.pressBack()
        val closed = device.wait(Until.hasObject(By.text("Change")), TIMEOUT)
        check(closed) {
            "MixerUi.closeSoundPicker(): 'Change' never reappeared within ${TIMEOUT}ms"
        }
        device.waitForIdle()
    }

    fun forceStop() {
        AudioState.shell("am force-stop $PACKAGE")
    }

    fun isRunning(): Boolean =
        AudioState.shell("pidof $PACKAGE").trim().isNotEmpty()

    fun pressPlay() {
        device.wait(Until.findObject(By.desc("Play")), TIMEOUT)?.click()
    }

    /**
     * Scrolls the sheet's grid a swipe at a time until an element matching [desc] is found,
     * or attempts run out.
     *
     * The grid grows taller as sounds activate (each card gains a "Remove" row), which pushes
     * later cards down and, by the fifth, off the bottom of the screen entirely on some
     * devices/nav modes/font scales. There is no fixed number of cards guaranteed to be on
     * screen, so this re-scrolls fresh before every lookup rather than assuming any layout.
     */
    private fun findInSheet(desc: String, maxScrolls: Int = 8): Boolean {
        repeat(maxScrolls) {
            if (device.findObject(By.desc(desc)) != null) return true
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.8).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.3).toInt(),
                15
            )
            device.waitForIdle()
        }
        return device.findObject(By.desc(desc)) != null
    }

    /**
     * Clicks "Add [name] to the mix" and waits for it to actually flip to "Remove ... from the
     * mix", retrying a few times if it doesn't.
     *
     * A click can land while the grid is still mid-animation (each card grows 120dp -> 160dp as
     * it activates, and `waitForIdle()` does not reliably wait that out - Compose's own
     * animations don't necessarily produce the accessibility events UiAutomator watches for
     * "idle"), so a click that UiAutomator dispatched can still silently miss. Confirming the
     * state actually changed, per sound, catches that at the point of action instead of leaving
     * it to be inferred from a wrong track count several files away.
     */
    private fun activateOne(name: String, attempts: Int = 3) {
        val addDesc = "Add $name to the mix"
        val removeDesc = "Remove $name from the mix"
        repeat(attempts) {
            if (device.findObject(By.desc(removeDesc)) != null) return
            if (findInSheet(addDesc)) {
                device.findObject(By.desc(addDesc))?.click()
            }
            device.wait(Until.findObject(By.desc(removeDesc)), 1_000)
        }
    }

    /**
     * Opens the picker, switches every sound on, confirms all five actually switched
     * (not just that they were clicked), and closes it.
     *
     * A click on a card UiAutomator never found - or one that landed mid-animation and missed -
     * is a silent no-op, not an error. Without this final check, a sound that fails to activate
     * surfaces three files later as a wrong audio-track count, with no clue which sound was
     * missing or why. Failing here, by name, keeps that diagnosis local to this helper.
     */
    fun activateAllSounds() {
        openSoundPicker()

        soundNames.forEach { name -> activateOne(name) }

        val notActivated = soundNames.filterNot { name -> findInSheet("Remove $name from the mix") }
        check(notActivated.isEmpty()) {
            "MixerUi.activateAllSounds(): failed to activate: ${notActivated.joinToString(", ")}"
        }

        closeSoundPicker()
    }

    /**
     * Counts every "Remove X from the mix" card, scrolling to look for each one the same
     * way [findInSheet] does — not a bare, unscrolled [device.findObject] read.
     *
     * `SoundBottomSheet` lays its cards out in a `LazyVerticalGrid(columns =
     * GridCells.Fixed(2))`. Lazy grids do not compose off-screen items at all - they are
     * absent from the accessibility tree, not merely slow to appear. With five active
     * cards (each grown to 160dp) plus the "Coming Soon" tile, that is three rows, and the
     * last row can be scrolled out of view whenever the sheet is freshly reopened. A bare
     * findObject-per-name silently undercounts whatever fell below the fold; reusing
     * findInSheet's scroll-until-found loop per name fixes that by scrolling each card into
     * view before counting it, exactly as activateAllSounds() already does to find and
     * click it.
     */
    fun activeSoundCount(): Int =
        soundNames.count { name -> findInSheet("Remove $name from the mix") }
}
