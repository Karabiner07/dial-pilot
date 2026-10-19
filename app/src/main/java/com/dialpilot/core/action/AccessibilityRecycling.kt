package com.dialpilot.core.action

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

// Before Android 13, accessibility objects obtained from the system had to be returned to a
// shared pool; from Android 13 on, recycle() is a deprecated no-op.

@Suppress("DEPRECATION")
fun AccessibilityNodeInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}

@Suppress("DEPRECATION")
fun AccessibilityWindowInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}
