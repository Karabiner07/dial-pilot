package com.dialpilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dialpilot.core.action.ActionRegistry
import com.dialpilot.core.action.DialAction
import com.dialpilot.core.engine.EngineStatus
import com.dialpilot.core.settings.DialConfig
import com.dialpilot.core.settings.SettingsRepository
import com.dialpilot.service.DialAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val config: DialConfig = DialConfig(),
    val engine: EngineStatus.State = EngineStatus.State(),
    val serviceEnabled: Boolean = false,
    /** Labels for packages that have rules or are in the foreground. */
    val appLabels: Map<String, String> = emptyMap(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val settings: SettingsRepository,
    private val installedApps: InstalledApps,
    registry: ActionRegistry,
    private val engineStatus: EngineStatus,
) : AndroidViewModel(application) {

    val actions: List<DialAction> = registry.all
    private val actionsById = actions.associateBy { it.id }

    private val serviceEnabled = MutableStateFlow(false)
    private val labelCache = mutableMapOf<String, String>()

    val uiState: StateFlow<MainUiState> = combine(
        settings.config,
        engineStatus.state,
        serviceEnabled,
    ) { config, engine, enabled ->
        val packages = config.appRules.keys + listOfNotNull(engine.foregroundPackage)
        MainUiState(
            config = config,
            engine = engine,
            serviceEnabled = enabled,
            appLabels = packages.associateWith { labelCache.getOrPut(it) { installedApps.label(it) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    /** Launchable apps; null until [loadApps] finishes. */
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    fun action(id: String?): DialAction? = id?.let(actionsById::get)

    /** Re-reads state the system owns; call when the screen resumes. */
    fun refresh() {
        serviceEnabled.value = DialAccessibilityService.isEnabled(getApplication())
    }

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch { _apps.value = installedApps.launchable() }
    }

    fun testScroll() = engineStatus.requestTest()

    fun setEnabled(value: Boolean) = launch { settings.setEnabled(value) }
    fun setDefaultAction(id: String) = launch { settings.setDefaultAction(id) }
    fun setAppRule(packageName: String, actionId: String) = launch { settings.setAppRule(packageName, actionId) }
    fun removeAppRule(packageName: String) = launch { settings.removeAppRule(packageName) }
    fun setInvertDirection(value: Boolean) = launch { settings.setInvertDirection(value) }
    fun setPhoneKeysAsDial(value: Boolean) = launch { settings.setPhoneKeysAsDial(value) }
    fun setHapticFeedback(value: Boolean) = launch { settings.setHapticFeedback(value) }
    fun setRequireHeadset(value: Boolean) = launch { settings.setRequireHeadset(value) }
    fun setHeadsetNameFilter(value: String) = launch { settings.setHeadsetNameFilter(value) }
    fun setActionValue(actionId: String, key: String, value: Any) =
        launch { settings.setActionValue(actionId, key, value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
