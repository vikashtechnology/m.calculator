package com.advanceai.calculator.ui.components

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The knock and click a key makes when it is pressed.
 *
 * This uses the view's own feedback calls rather than the Vibrator API: a
 * keyboard tap needs no permission, respects the phone's own haptic strength,
 * and the click is the very sound the system keyboard plays, so the keypad
 * sounds like the rest of Android. Both are switchable in Settings.
 */
@Composable
fun rememberKeyFeedback(haptics: Boolean, sound: Boolean): () -> Unit {
    val view = LocalView.current
    return remember(view, haptics, sound) {
        {
            if (haptics) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            if (sound) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
            }
        }
    }
}
