package com.dialpilot.core.action

import androidx.annotation.StringRes

/** Declarative description of an option exposed by a [DialAction]. */
sealed interface ActionSetting {
    val key: String

    @get:StringRes
    val titleRes: Int

    data class Slider(
        override val key: String,
        @StringRes override val titleRes: Int,
        val range: IntRange,
        val default: Int,
        val step: Int = 1,
        val unit: String = "",
    ) : ActionSetting

    data class Toggle(
        override val key: String,
        @StringRes override val titleRes: Int,
        val default: Boolean,
    ) : ActionSetting

    data class Choice(
        override val key: String,
        @StringRes override val titleRes: Int,
        val options: List<Option>,
        val default: String,
    ) : ActionSetting {
        data class Option(val value: String, @StringRes val labelRes: Int)
    }
}

/** Resolved values for an action's [ActionSetting]s, falling back to declared defaults. */
class ActionSettingsValues(
    private val schema: List<ActionSetting>,
    private val stored: Map<String, Any>,
) {
    fun int(key: String): Int =
        (stored[key] as? Int) ?: (find<ActionSetting.Slider>(key)).default

    fun boolean(key: String): Boolean =
        (stored[key] as? Boolean) ?: (find<ActionSetting.Toggle>(key)).default

    fun string(key: String): String =
        (stored[key] as? String) ?: (find<ActionSetting.Choice>(key)).default

    private inline fun <reified T : ActionSetting> find(key: String): T =
        schema.filterIsInstance<T>().firstOrNull { it.key == key }
            ?: error("No ${T::class.simpleName} setting named '$key'")
}
