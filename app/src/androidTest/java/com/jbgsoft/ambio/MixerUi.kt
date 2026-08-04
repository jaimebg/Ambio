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
        device.wait(Until.findObject(By.text("Change")), TIMEOUT)?.click()

        soundNames.forEach { name -> activateOne(name) }

        val notActivated = soundNames.filterNot { name -> findInSheet("Remove $name from the mix") }
        check(notActivated.isEmpty()) {
            "MixerUi.activateAllSounds(): failed to activate: ${notActivated.joinToString(", ")}"
        }

        device.pressBack()
        device.waitForIdle()
    }

    fun activeSoundCount(): Int =
        soundNames.count { name ->
            device.findObject(By.desc("Remove $name from the mix")) != null
        }
}
