package com.dialpilot.actions

import android.graphics.PointF
import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.ActionSetting
import com.dialpilot.core.action.DialAction
import javax.inject.Inject
import kotlin.math.abs

/** Pinch zoom around the screen centre, for PDFs, maps and photos. */
class ZoomAction @Inject constructor() : DialAction {
    override val id = ActionIds.ZOOM
    override val titleRes = R.string.action_zoom
    override val descriptionRes = R.string.action_zoom_desc

    override val settings = listOf(
        ActionSetting.Slider(KEY_STRENGTH, R.string.setting_zoom_strength, 5..40, default = 15, unit = "%"),
    )

    override suspend fun perform(steps: Int, context: ActionContext) {
        val width = context.screenWidth.toFloat()
        val small = width * MIN_SPAN_FRACTION
        val delta = (width * context.settings.int(KEY_STRENGTH) / 100f * abs(steps))
            .coerceAtMost(width * MAX_SPAN_FRACTION - small)
        val (start, end) = if (steps > 0) small to small + delta else small + delta to small
        Gestures.pinch(
            context = context,
            center = PointF(width / 2, context.screenHeight / 2f),
            startSpan = start,
            endSpan = end,
            durationMs = PINCH_MS,
        )
    }

    private companion object {
        const val KEY_STRENGTH = "strength"
        const val MIN_SPAN_FRACTION = 0.15f
        const val MAX_SPAN_FRACTION = 0.85f
        const val PINCH_MS = 250L
    }
}
