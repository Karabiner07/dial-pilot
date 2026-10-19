package com.dialpilot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
import com.dialpilot.core.action.recycleCompat
import com.dialpilot.core.engine.DialEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Host for the dial engine. An accessibility service is the only unprivileged way to inject
 * gestures into other apps, and the system keeps it running without a foreground notification.
 */
@AndroidEntryPoint
class DialAccessibilityService : AccessibilityService() {

    @Inject lateinit var engine: DialEngine
    @Inject lateinit var audioManager: AudioManager

    private val scope = MainScope()

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine.start(scope) { settings -> ServiceActionContext(this, audioManager, settings) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val relevant = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> changesFocus(event)
            else -> false
        }
        if (relevant) focusedAppPackage()?.let(engine::onForegroundAppChanged)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = engine.onKeyEvent(event)

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        engine.stop()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Package of the focused application window. System windows such as the volume panel and the
     * keyboard are skipped, so they do not switch per-app rules.
     */
    private fun focusedAppPackage(): String? {
        val allWindows = windows
        try {
            val appWindows = allWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val window = appWindows.firstOrNull { it.isFocused } ?: appWindows.firstOrNull { it.isActive }
            val root = window?.root ?: return null
            return root.packageName?.toString().also { root.recycleCompat() }
        } finally {
            allWindows.forEach { it.recycleCompat() }
        }
    }

    /** Window list changes fire constantly (e.g. on every scroll); only focus changes matter. */
    private fun changesFocus(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return event.windowChanges and FOCUS_CHANGES != 0
    }

    companion object {
        private const val FOCUS_CHANGES = AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or
            AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or
            AccessibilityEvent.WINDOWS_CHANGE_ADDED or
            AccessibilityEvent.WINDOWS_CHANGE_REMOVED

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, DialAccessibilityService::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { ComponentName.unflattenFromString(it.id) == component }
        }
    }
}
