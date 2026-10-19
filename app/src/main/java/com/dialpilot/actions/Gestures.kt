package com.dialpilot.actions

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.dialpilot.core.action.ActionContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Helpers for building and running injected touch gestures. */
internal object Gestures {

    /** How long the finger rests at the end of a precise swipe before lifting. */
    private const val HOLD_MS = 80L

    /**
     * Swipes from [from] to [to].
     *
     * @param precise rest before lifting so content stops where the finger stops; otherwise the
     *   release keeps its speed and the content flings.
     */
    suspend fun swipe(
        context: ActionContext,
        from: PointF,
        to: PointF,
        durationMs: Long,
        precise: Boolean,
    ): Boolean {
        val move = Path().apply {
            moveTo(from.x, from.y)
            lineTo(to.x, to.y)
        }
        if (!precise) {
            return context.dispatchGesture(single(GestureDescription.StrokeDescription(move, 0, durationMs)))
        }
        val moveStroke = GestureDescription.StrokeDescription(move, 0, durationMs, true)
        // Continued strokes must start where the previous one ended and cannot be empty, so the
        // rest is a one pixel nudge.
        val rest = Path().apply {
            moveTo(to.x, to.y)
            lineTo(to.x, to.y + if (to.y >= from.y) 1f else -1f)
        }
        val restStroke = moveStroke.continueStroke(rest, 0, HOLD_MS, false)
        return context.dispatchGestureSequence(listOf(single(moveStroke), single(restStroke)))
    }

    /**
     * Swipes along one axis by [totalDistance] pixels (sign gives direction), split into as many
     * swipes as needed so each fits inside [maxSpan] around [center].
     */
    suspend fun scrollBy(
        context: ActionContext,
        center: PointF,
        totalDistance: Float,
        maxSpan: Float,
        vertical: Boolean,
        durationMs: Long,
        precise: Boolean,
    ) {
        if (totalDistance == 0f) return
        val chunks = ceil(abs(totalDistance) / maxSpan).toInt().coerceAtLeast(1)
        val chunk = totalDistance / chunks
        repeat(chunks) {
            val half = chunk / 2
            // Finger moves opposite to the content: to scroll forward, drag towards the start.
            val from = if (vertical) PointF(center.x, center.y + half) else PointF(center.x + half, center.y)
            val to = if (vertical) PointF(center.x, center.y - half) else PointF(center.x - half, center.y)
            if (!swipe(context, from, to, durationMs, precise)) return
        }
    }

    /** Two-finger pinch around [center], changing the finger distance from [startSpan] to [endSpan]. */
    suspend fun pinch(
        context: ActionContext,
        center: PointF,
        startSpan: Float,
        endSpan: Float,
        durationMs: Long,
    ): Boolean {
        fun finger(sign: Int) = Path().apply {
            moveTo(center.x + sign * startSpan / 2, center.y)
            lineTo(center.x + sign * endSpan / 2, center.y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(finger(-1), 0, durationMs))
            .addStroke(GestureDescription.StrokeDescription(finger(1), 0, durationMs))
            .build()
        return context.dispatchGesture(gesture)
    }

    fun percentOf(total: Int, percent: Int): Float = (total * percent / 100f).roundToInt().toFloat()

    private fun single(stroke: GestureDescription.StrokeDescription) =
        GestureDescription.Builder().addStroke(stroke).build()
}
