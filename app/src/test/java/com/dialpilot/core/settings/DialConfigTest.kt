package com.dialpilot.core.settings

import com.dialpilot.core.action.ActionIds
import org.junit.Assert.assertEquals
import org.junit.Test

class DialConfigTest {

    private val config = DialConfig(
        defaultActionId = ActionIds.SCROLL_VERTICAL,
        appRules = mapOf("com.instagram.android" to ActionIds.SWIPE_ITEMS),
    )

    @Test
    fun `app rule overrides the default`() {
        assertEquals(ActionIds.SWIPE_ITEMS, config.actionIdFor("com.instagram.android"))
    }

    @Test
    fun `apps without a rule and unknown foreground use the default`() {
        assertEquals(ActionIds.SCROLL_VERTICAL, config.actionIdFor("com.android.chrome"))
        assertEquals(ActionIds.SCROLL_VERTICAL, config.actionIdFor(null))
    }
}
