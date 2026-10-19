package com.dialpilot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionSettingsValues
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** [ActionContext] backed by the running accessibility service. */
internal class ServiceActionContext(
    private val service: AccessibilityService,
    private val audioManager: AudioManager,
    override val settings: ActionSettingsValues,
) : ActionContext {

    private val screenBounds: Rect = currentScreenBounds()
    override val screenWidth: Int get() = screenBounds.width()
    override val screenHeight: Int get() = screenBounds.height()

    override suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (!service.dispatchGesture(gesture, callback, null)) cont.resume(false)
        }

    override suspend fun dispatchGestureSequence(gestures: List<GestureDescription>): Boolean =
        gestures.all { dispatchGesture(it) }

    override fun findPrimaryScrollable(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val bounds = Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()
                if (area > bestArea) {
                    best = node
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return best
    }

    override fun dispatchMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun currentScreenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        } else {
            val metrics = service.resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }

    private companion object {
        /** Bounds the tree walk on very large pages. */
        const val MAX_NODES = 2_000
    }
}
