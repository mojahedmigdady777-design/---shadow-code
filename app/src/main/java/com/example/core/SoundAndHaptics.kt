package com.example.core

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class TacticalFeedback(private val context: Context) {

    var soundEnabled: Boolean = true
    var hapticsEnabled: Boolean = true

    fun playAlertChime() {
        if (soundEnabled) {
            playTone(ToneGenerator.TONE_PROP_BEEP2, 160)
        }
        vibrate(60)
    }

    fun playSuccessChime() {
        if (soundEnabled) {
            playTone(ToneGenerator.TONE_PROP_ACK, 180)
        }
        vibrate(45)
    }

    fun playFailBuzz() {
        if (soundEnabled) {
            playTone(ToneGenerator.TONE_PROP_NACK, 240)
        }
        vibrate(140)
    }

    fun tapHaptic() {
        vibrate(20)
    }

    private fun playTone(tone: Int, durationMs: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            tg.startTone(tone, durationMs)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        if (!hapticsEnabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator?.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }
}
