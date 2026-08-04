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
