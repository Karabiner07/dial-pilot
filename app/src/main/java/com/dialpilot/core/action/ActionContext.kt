package com.dialpilot.core.action

import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityNodeInfo

/** Capabilities available to a [DialAction] while it runs. */
interface ActionContext {
    /** Current screen size in pixels. */
    val screenWidth: Int
    val screenHeight: Int

    /** The action's own settings. */
    val settings: ActionSettingsValues

    /**
     * Injects a gesture and suspends until it completes.
     *
     * @return false if the system cancelled the gesture.
     */
    suspend fun dispatchGesture(gesture: GestureDescription): Boolean

    /**
     * Injects gestures back to back, suspending until all complete. Earlier gestures may end with
     * strokes marked `willContinue`, so the finger can be held still before lifting; ending a
     * swipe that way avoids a fling.
     */
    suspend fun dispatchGestureSequence(gestures: List<GestureDescription>): Boolean

    /**
     * The largest visible scrollable view in the active window, if any. The caller owns the
     * returned node and should call [recycleCompat] when done.
     */
    fun findPrimaryScrollable(): AccessibilityNodeInfo?

    /** Sends a media key (e.g. KEYCODE_MEDIA_NEXT) to the active media session. */
    fun dispatchMediaKey(keyCode: Int)
}
