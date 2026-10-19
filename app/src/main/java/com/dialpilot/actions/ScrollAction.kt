package com.dialpilot.actions

import android.graphics.PointF
import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.ActionSetting
import com.dialpilot.core.action.DialAction
import javax.inject.Inject

/**
 * Smooth scrolling by injected swipes. Works in any app that scrolls by touch: browsers, PDF
 * readers, feeds, chats. One class serves both axes.
 */
abstract class ScrollAction(private val vertical: Boolean) : DialAction {

    override val settings = listOf(
        ActionSetting.Slider(KEY_DISTANCE, R.string.setting_scroll_distance, 5..100, default = 50, step = 5, unit = "%"),
        ActionSetting.Slider(KEY_SPEED, R.string.setting_scroll_duration, 60..600, default = 220, step = 20, unit = " ms"),
        ActionSetting.Toggle(KEY_PRECISE, R.string.setting_scroll_precise, default = true),
    )

    override suspend fun perform(steps: Int, context: ActionContext) {
        val axisLength = if (vertical) context.screenHeight else context.screenWidth
        val distance = Gestures.percentOf(axisLength, context.settings.int(KEY_DISTANCE)) * steps
        Gestures.scrollBy(
            context = context,
            center = PointF(context.screenWidth / 2f, context.screenHeight / 2f),
            totalDistance = distance,
            maxSpan = axisLength * MAX_SPAN_FRACTION,
            vertical = vertical,
            durationMs = context.settings.int(KEY_SPEED).toLong(),
            precise = context.settings.boolean(KEY_PRECISE),
        )
    }

    private companion object {
        const val KEY_DISTANCE = "distance"
        const val KEY_SPEED = "duration"
        const val KEY_PRECISE = "precise"

        /** Keeps swipes clear of the status bar, navigation bar and edge-back gesture zones. */
        const val MAX_SPAN_FRACTION = 0.6f
    }
}

class VerticalScrollAction @Inject constructor() : ScrollAction(vertical = true) {
    override val id = ActionIds.SCROLL_VERTICAL
    override val titleRes = R.string.action_scroll_vertical
    override val descriptionRes = R.string.action_scroll_vertical_desc
}

class HorizontalScrollAction @Inject constructor() : ScrollAction(vertical = false) {
    override val id = ActionIds.SCROLL_HORIZONTAL
    override val titleRes = R.string.action_scroll_horizontal
    override val descriptionRes = R.string.action_scroll_horizontal_desc
}
