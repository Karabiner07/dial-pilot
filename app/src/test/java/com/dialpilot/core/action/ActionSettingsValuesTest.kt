package com.dialpilot.core.action

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSettingsValuesTest {

    private val schema = listOf(
        ActionSetting.Slider("distance", 0, 0..100, default = 20),
        ActionSetting.Toggle("precise", 0, default = true),
        ActionSetting.Choice("axis", 0, emptyList(), default = "vertical"),
    )

    @Test
    fun `falls back to declared defaults`() {
        val values = ActionSettingsValues(schema, emptyMap())
        assertEquals(20, values.int("distance"))
        assertEquals(true, values.boolean("precise"))
        assertEquals("vertical", values.string("axis"))
    }

    @Test
    fun `stored values win`() {
        val values = ActionSettingsValues(schema, mapOf("distance" to 45, "precise" to false, "axis" to "horizontal"))
        assertEquals(45, values.int("distance"))
        assertEquals(false, values.boolean("precise"))
        assertEquals("horizontal", values.string("axis"))
    }

    @Test
    fun `stored value of the wrong type is ignored`() {
        val values = ActionSettingsValues(schema, mapOf("distance" to "oops"))
        assertEquals(20, values.int("distance"))
    }

    @Test(expected = IllegalStateException::class)
    fun `undeclared key fails loudly`() {
        ActionSettingsValues(schema, emptyMap()).int("nope")
    }
}
