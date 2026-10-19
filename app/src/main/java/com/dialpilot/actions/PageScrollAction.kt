package com.dialpilot.actions

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.DialAction
import com.dialpilot.core.action.recycleCompat
import javax.inject.Inject
import kotlin.math.abs

/**
 * Asks the app itself to scroll by a page through the accessibility API instead of faking
 * touches. Exact page jumps in apps that support it (Chrome, most lists); does nothing in apps
 * that draw their own content.
 */
class PageScrollAction @Inject constructor() : DialAction {
    override val id = ActionIds.PAGE_SCROLL
    override val titleRes = R.string.action_page_scroll
    override val descriptionRes = R.string.action_page_scroll_desc

    override suspend fun perform(steps: Int, context: ActionContext) {
        val node = context.findPrimaryScrollable() ?: return
        try {
            val forward = steps > 0
            val action = preferredAction(forward).takeIf { it in node.actionList }
                ?: if (forward) AccessibilityAction.ACTION_SCROLL_FORWARD else AccessibilityAction.ACTION_SCROLL_BACKWARD
            repeat(abs(steps)) {
                if (!node.performAction(action.id)) return
            }
        } finally {
            node.recycleCompat()
        }
    }

    private fun preferredAction(forward: Boolean): AccessibilityAction? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (forward) AccessibilityAction.ACTION_SCROLL_DOWN else AccessibilityAction.ACTION_SCROLL_UP
        } else {
            null
        }
}
