package com.dialpilot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
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
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> focusedAppPackage()?.let(engine::onForegroundAppChanged)
        }
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
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val window = appWindows.firstOrNull { it.isFocused } ?: appWindows.firstOrNull { it.isActive }
        return window?.root?.packageName?.toString()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, DialAccessibilityService::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { ComponentName.unflattenFromString(it.id) == component }
        }
    }
}
