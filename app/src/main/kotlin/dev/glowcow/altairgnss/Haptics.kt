package dev.glowcow.altairgnss

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/** What the phone gives the hand when something happens away from the screen: a recording that begins or ends. */
object Haptics {
    fun started(context: Context) = play(context, VibrationEffect.EFFECT_DOUBLE_CLICK)

    fun stopped(context: Context) = play(context, VibrationEffect.EFFECT_HEAVY_CLICK)

    // Feedback to the user's own tap, even when it comes later: the touch feedback setting applies, silent mode does not.
    private fun play(context: Context, effect: Int) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createPredefined(effect), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    }
}
