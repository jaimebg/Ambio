package com.jbgsoft.ambio.core.common.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages haptic feedback for user interactions throughout the app.
 *
 * Provides various haptic patterns for different interaction types:
 * - click: Standard button tap feedback
 * - tick: Subtle feedback for slider adjustments
 * - heavyClick: Emphasized feedback for important actions (sound selection)
 * - timerComplete: Custom 3-pulse pattern for timer completion
 * - doubleClick: Double-tap feedback pattern
 *
 * Note: VIBRATE permission is declared in AndroidManifest.xml and required for all vibration methods.
 */
@Singleton
@SuppressLint("MissingPermission") // VIBRATE permission declared in AndroidManifest.xml
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // minSdk 31 guarantees VibratorManager is available
    private val vibrator: Vibrator = run {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    private val hasVibrator: Boolean = vibrator.hasVibrator()

    fun click() {
        if (!hasVibrator) return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    fun tick() {
        if (!hasVibrator) return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    fun heavyClick() {
        if (!hasVibrator) return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    fun timerComplete() {
        if (!hasVibrator) return
        // Custom waveform: 3 pulses for timer completion per spec
        val timings = longArrayOf(0, 100, 100, 100, 100, 100)
        val amplitudes = intArrayOf(0, 150, 0, 150, 0, 150)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator.vibrate(effect)
    }

    fun doubleClick() {
        if (!hasVibrator) return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }
}
