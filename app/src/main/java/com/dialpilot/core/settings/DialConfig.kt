package com.dialpilot.core.settings

import com.dialpilot.core.action.ActionIds

/** Everything the user configures, as one immutable snapshot. */
data class DialConfig(
    /** Master switch, also toggled from the Quick Settings tile. */
    val enabled: Boolean = true,
    /** Action used in apps without a rule. */
    val defaultActionId: String = ActionIds.SCROLL_VERTICAL,
    /** Package name to action id. */
    val appRules: Map<String, String> = DEFAULT_APP_RULES,
    val invertDirection: Boolean = false,
    /**
     * Claim media keys while the dial is captured, so dial presses set to "Skip forward" /
     * "Skip back" in Nothing X act as forward / back steps.
     */
    val captureDialPresses: Boolean = true,
    /**
     * Read dial rotation through media volume changes. Off by default: it only works where the
     * buds report rotation to the phone, and it pins the media volume while active.
     */
    val useDialRotation: Boolean = false,
    /** Treat the phone's own volume keys like the dial while rotation is captured. */
    val phoneKeysAsDial: Boolean = false,
    /** Only capture the dial while a matching Bluetooth headset is connected. */
    val requireHeadset: Boolean = true,
    /** Case-insensitive substring of the headset's Bluetooth name. */
    val headsetNameFilter: String = "CMF",
    val hapticFeedback: Boolean = true,
    /** Stored values per action id, then per setting key. */
    val actionValues: Map<String, Map<String, Any>> = emptyMap(),
) {
    fun actionIdFor(packageName: String?): String =
        packageName?.let(appRules::get) ?: defaultActionId

    companion object {
        val DEFAULT_APP_RULES = mapOf(
            "com.instagram.android" to ActionIds.SWIPE_ITEMS,
        )
    }
}
