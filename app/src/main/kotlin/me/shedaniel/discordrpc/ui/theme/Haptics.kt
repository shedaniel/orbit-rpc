package me.shedaniel.discordrpc.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Semantic haptics. Each call maps to the richest constant the device supports.
 */
@Stable
class Haptics(private val view: View, private val enabled: Boolean) {
    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    /** Light tick for selection changes (chips, segments, list picks). */
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    /** Very light tick for continuous changes (slider steps, drag over items). */
    fun frequentTick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    fun toggle(on: Boolean) = perform(
        if (Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        else HapticFeedbackConstants.CONTEXT_CLICK
    )
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)
    fun reject() = perform(HapticFeedbackConstants.REJECT)
    fun press() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun dragStart() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.LONG_PRESS)
    fun gestureEnd() = perform(HapticFeedbackConstants.GESTURE_END)
}

val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
