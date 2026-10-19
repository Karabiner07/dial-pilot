package com.dialpilot.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val config: Flow<DialConfig> = dataStore.data
        .map(::toConfig)
        .distinctUntilChanged()

    suspend fun setEnabled(value: Boolean) = edit { it[Keys.ENABLED] = value }

    suspend fun toggleEnabled() = edit { it[Keys.ENABLED] = !(it[Keys.ENABLED] ?: DEFAULTS.enabled) }

    suspend fun setDefaultAction(actionId: String) = edit { it[Keys.DEFAULT_ACTION] = actionId }

    suspend fun setInvertDirection(value: Boolean) = edit { it[Keys.INVERT] = value }

    suspend fun setPhoneKeysAsDial(value: Boolean) = edit { it[Keys.PHONE_KEYS] = value }

    suspend fun setRequireHeadset(value: Boolean) = edit { it[Keys.REQUIRE_HEADSET] = value }

    suspend fun setHeadsetNameFilter(value: String) = edit { it[Keys.HEADSET_FILTER] = value.trim() }

    suspend fun setHapticFeedback(value: Boolean) = edit { it[Keys.HAPTICS] = value }

    suspend fun setAppRule(packageName: String, actionId: String) = edit {
        it[Keys.APP_RULES] = encodeRules(readRules(it) + (packageName to actionId))
    }

    suspend fun removeAppRule(packageName: String) = edit {
        it[Keys.APP_RULES] = encodeRules(readRules(it) - packageName)
    }

    suspend fun setActionValue(actionId: String, key: String, value: Any) = edit {
        val name = actionKeyName(actionId, key)
        when (value) {
            is Int -> it[intPreferencesKey(name)] = value
            is Boolean -> it[booleanPreferencesKey(name)] = value
            is String -> it[stringPreferencesKey(name)] = value
            else -> throw IllegalArgumentException("Unsupported setting type: ${value::class}")
        }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun toConfig(prefs: Preferences) = DialConfig(
        enabled = prefs[Keys.ENABLED] ?: DEFAULTS.enabled,
        defaultActionId = prefs[Keys.DEFAULT_ACTION] ?: DEFAULTS.defaultActionId,
        appRules = readRules(prefs),
        invertDirection = prefs[Keys.INVERT] ?: DEFAULTS.invertDirection,
        phoneKeysAsDial = prefs[Keys.PHONE_KEYS] ?: DEFAULTS.phoneKeysAsDial,
        requireHeadset = prefs[Keys.REQUIRE_HEADSET] ?: DEFAULTS.requireHeadset,
        headsetNameFilter = prefs[Keys.HEADSET_FILTER] ?: DEFAULTS.headsetNameFilter,
        hapticFeedback = prefs[Keys.HAPTICS] ?: DEFAULTS.hapticFeedback,
        actionValues = readActionValues(prefs),
    )

    private fun readRules(prefs: Preferences): Map<String, String> =
        prefs[Keys.APP_RULES]
            ?.mapNotNull { entry ->
                val (pkg, action) = entry.split(RULE_SEPARATOR, limit = 2).takeIf { it.size == 2 }
                    ?: return@mapNotNull null
                pkg to action
            }
            ?.toMap()
            ?: DEFAULTS.appRules

    private fun encodeRules(rules: Map<String, String>): Set<String> =
        rules.mapTo(mutableSetOf()) { (pkg, action) -> "$pkg$RULE_SEPARATOR$action" }

    private fun readActionValues(prefs: Preferences): Map<String, Map<String, Any>> =
        prefs.asMap().entries
            .mapNotNull { (key, value) ->
                val parts = key.name.split(ACTION_KEY_SEPARATOR)
                if (parts.size != 3 || parts[0] != ACTION_KEY_PREFIX) return@mapNotNull null
                Triple(parts[1], parts[2], value)
            }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, pairs) -> pairs.toMap() }

    private fun actionKeyName(actionId: String, key: String) =
        listOf(ACTION_KEY_PREFIX, actionId, key).joinToString(ACTION_KEY_SEPARATOR)

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val DEFAULT_ACTION = stringPreferencesKey("default_action")
        val APP_RULES = stringSetPreferencesKey("app_rules")
        val INVERT = booleanPreferencesKey("invert_direction")
        val PHONE_KEYS = booleanPreferencesKey("phone_keys_as_dial")
        val REQUIRE_HEADSET = booleanPreferencesKey("require_headset")
        val HEADSET_FILTER = stringPreferencesKey("headset_name_filter")
        val HAPTICS = booleanPreferencesKey("haptic_feedback")
    }

    private companion object {
        val DEFAULTS = DialConfig()
        const val RULE_SEPARATOR = "|"
        const val ACTION_KEY_PREFIX = "action"
        const val ACTION_KEY_SEPARATOR = ":"
    }
}
