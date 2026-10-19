package com.dialpilot.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ActionRegistryTest {

    private class FakeAction(override val id: String) : DialAction {
        override val titleRes = 0
        override val descriptionRes = 0
        override suspend fun perform(steps: Int, context: ActionContext) = Unit
    }

    @Test
    fun `known actions come first in display order, unknown ones after`() {
        val custom = FakeAction("custom_feature")
        val registry = ActionRegistry(
            setOf(FakeAction(ActionIds.PASS_THROUGH), custom, FakeAction(ActionIds.SCROLL_VERTICAL)),
        )
        assertEquals(
            listOf(ActionIds.SCROLL_VERTICAL, ActionIds.PASS_THROUGH, "custom_feature"),
            registry.all.map { it.id },
        )
        assertSame(custom, registry["custom_feature"])
        assertNull(registry["missing"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate ids are rejected`() {
        ActionRegistry(setOf(FakeAction("a"), FakeAction("a")))
    }
}
