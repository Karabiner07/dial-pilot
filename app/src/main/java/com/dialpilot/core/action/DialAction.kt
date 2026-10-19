package com.dialpilot.core.action

import androidx.annotation.StringRes

/**
 * A behaviour that dial rotation can be mapped to.
 *
 * This is the main extension point of the app. To add a feature:
 *  1. Implement this interface (see the `actions` package for examples).
 *  2. Bind it into the set in [com.dialpilot.actions.ActionsModule].
 *
 * The settings screen, per-app rules and persistence pick the new action up automatically,
 * including any [settings] it declares.
 */
interface DialAction {
    /** Stable identifier. It is persisted, so never rename it once released. */
    val id: String

    @get:StringRes
    val titleRes: Int

    @get:StringRes
    val descriptionRes: Int

    /**
     * Whether the dial should be captured while this action is active. When false the dial keeps
     * its normal behaviour (volume) and [perform] is never called.
     */
    val interceptsDial: Boolean get() = true

    /** User-tunable options. They are rendered and stored generically. */
    val settings: List<ActionSetting> get() = emptyList()

    /**
     * Runs the action for a burst of dial detents.
     *
     * @param steps signed number of detents since the previous call; positive means forward
     *   (volume-up direction unless the user inverted it). Fast turns are coalesced into one call.
     */
    suspend fun perform(steps: Int, context: ActionContext)
}
