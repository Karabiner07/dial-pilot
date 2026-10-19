package com.dialpilot.actions

import android.graphics.PointF
import android.os.SystemClock
import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.ActionSetting
import com.dialpilot.core.action.DialAction
import javax.inject.Inject
import kotlin.math.sign

/**
 * One detent = one item, for full-screen pagers: Instagram Reels, YouTube Shorts, TikTok,
 * stories, photo galleries.
 */
class SwipeItemsAction @Inject constructor() : DialAction {
    override val id = ActionIds.SWIPE_ITEMS
    override val titleRes = R.string.action_swipe_items
    override val descriptionRes = R.string.action_swipe_items_desc

    override val settings = listOf(
        ActionSetting.Choice(
            key = KEY_AXIS,
            titleRes = R.string.setting_axis,
            options = listOf(
                ActionSetting.Choice.Option(AXIS_VERTICAL, R.string.axis_vertical),
                ActionSetting.Choice.Option(AXIS_HORIZONTAL, R.string.axis_horizontal),
            ),
            default = AXIS_VERTICAL,
        ),
        ActionSetting.Slider(KEY_COOLDOWN, R.string.setting_cooldown, 0..1500, default = 450, step = 50, unit = " ms"),
    )

    private var lastSwipeAt = 0L

    override suspend fun perform(steps: Int, context: ActionContext) {
        val now = SystemClock.uptimeMillis()
        // A quick turn produces several detents; skipping five reels at once is never wanted.
        if (now - lastSwipeAt < context.settings.int(KEY_COOLDOWN)) return
        lastSwipeAt = now

        val vertical = context.settings.string(KEY_AXIS) == AXIS_VERTICAL
        val w = context.screenWidth.toFloat()
        val h = context.screenHeight.toFloat()
        val direction = steps.sign
        // Fast swipe across the middle half of the screen, released at speed so the pager snaps.
        val (from, to) = if (vertical) {
            PointF(w / 2, h / 2 + direction * h / 4) to PointF(w / 2, h / 2 - direction * h / 4)
        } else {
            PointF(w / 2 + direction * w / 4, h / 2) to PointF(w / 2 - direction * w / 4, h / 2)
        }
        Gestures.swipe(context, from, to, SWIPE_MS, precise = false)
    }

    private companion object {
        const val KEY_AXIS = "axis"
        const val KEY_COOLDOWN = "cooldown"
        const val AXIS_VERTICAL = "vertical"
        const val AXIS_HORIZONTAL = "horizontal"
        const val SWIPE_MS = 120L
    }
}
