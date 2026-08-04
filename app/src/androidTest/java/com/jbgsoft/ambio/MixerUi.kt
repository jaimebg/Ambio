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
