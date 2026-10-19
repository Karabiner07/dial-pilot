package com.dialpilot.core.engine

import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.DialAction
import com.dialpilot.core.monitor.ScreenStateMonitor
import com.dialpilot.core.settings.DialConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CaptureRulesTest {

    private class FakeAction(override val id: String, override val interceptsDial: Boolean = true) : DialAction {
        override val titleRes = 0
        override val descriptionRes = 0
        override suspend fun perform(steps: Int, context: ActionContext) = Unit
    }

    private val scroll = FakeAction(ActionIds.SCROLL_VERTICAL)
    private val swipe = FakeAction(ActionIds.SWIPE_ITEMS)
    private val passThrough = FakeAction(ActionIds.PASS_THROUGH, interceptsDial = false)
    private val actions = listOf(scroll, swipe, passThrough).associateBy { it.id }

    private val unlocked = ScreenStateMonitor.State(interactive = true, locked = false)
    private val buds = listOf("CMF Buds Pro 2")
    private val config = DialConfig(
        defaultActionId = ActionIds.SCROLL_VERTICAL,
        appRules = mapOf(
            "com.instagram.android" to ActionIds.SWIPE_ITEMS,
            "com.spotify.music" to ActionIds.PASS_THROUGH,
        ),
    )

    private fun resolve(
        config: DialConfig = this.config,
        packageName: String? = "com.android.chrome",
        headsets: List<String> = buds,
        screen: ScreenStateMonitor.State = unlocked,
    ) = CaptureRules.resolve(config, packageName, headsets, screen, actions::get)

    @Test
    fun `default action applies to apps without a rule`() {
        val result = resolve()
        assertSame(scroll, result.action)
        assertNull(result.block)
    }

    @Test
    fun `app rule overrides the default`() {
        assertSame(swipe, resolve(packageName = "com.instagram.android").action)
    }

    @Test
    fun `pass-through rule leaves the dial alone`() {
        val result = resolve(packageName = "com.spotify.music")
        assertNull(result.action)
        assertEquals(EngineStatus.Block.APP_PASSES_THROUGH, result.block)
    }

    @Test
    fun `blocks are reported in priority order`() {
        assertEquals(EngineStatus.Block.DISABLED, resolve(config = config.copy(enabled = false), headsets = emptyList()).block)
        assertEquals(EngineStatus.Block.SCREEN_OFF, resolve(screen = unlocked.copy(interactive = false)).block)
        assertEquals(EngineStatus.Block.LOCKED, resolve(screen = unlocked.copy(locked = true)).block)
        assertEquals(EngineStatus.Block.NO_HEADSET, resolve(headsets = listOf("Pixel Buds")).block)
    }

    @Test
    fun `headset is not required when the filter is switched off`() {
        assertSame(scroll, resolve(config = config.copy(requireHeadset = false), headsets = emptyList()).action)
    }
}
