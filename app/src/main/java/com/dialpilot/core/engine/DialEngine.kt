package com.dialpilot.core.engine

import android.util.Log
import android.view.KeyEvent
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionRegistry
import com.dialpilot.core.action.ActionSettingsValues
import com.dialpilot.core.action.DialAction
import com.dialpilot.core.input.DialInputSource
import com.dialpilot.core.monitor.HeadsetMonitor
import com.dialpilot.core.monitor.ScreenStateMonitor
import com.dialpilot.core.settings.DialConfig
import com.dialpilot.core.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides, at every moment, whether the dial is captured and by which action, and runs that
 * action for incoming steps.
 *
 * Owned by [com.dialpilot.service.DialAccessibilityService]; one instance per service connection.
 */
class DialEngine @Inject constructor(
    private val settings: SettingsRepository,
    private val registry: ActionRegistry,
    private val inputSources: Set<@JvmSuppressWildcards DialInputSource>,
    private val headsetMonitor: HeadsetMonitor,
    private val screenMonitor: ScreenStateMonitor,
    private val haptics: Haptics,
    private val status: EngineStatus,
) {
    private val foregroundPackage = MutableStateFlow<String?>(null)
    private val steps = Channel<Int>(Channel.UNLIMITED)

    /**
     * @param scope main-thread scope that lives as long as the accessibility service.
     * @param contextFactory builds the [ActionContext] an action runs with.
     */
    fun start(scope: CoroutineScope, contextFactory: (ActionSettingsValues) -> ActionContext) {
        status.update { it.copy(serviceRunning = true) }
        inputSources.forEach { source -> source.start { steps.trySend(it) } }

        val config: StateFlow<DialConfig?> = settings.config.stateIn(scope, SharingStarted.Eagerly, null)
        val headsets = headsetMonitor.connectedHeadsets.stateIn(scope, SharingStarted.Eagerly, emptyList())

        val capture: StateFlow<CaptureRules.Result> = combine(
            config.filterNotNull(),
            foregroundPackage,
            headsets,
            screenMonitor.state,
        ) { cfg, packageName, names, screen ->
            CaptureRules.resolve(cfg, packageName, names, screen, registry::get)
        }.stateIn(scope, SharingStarted.Eagerly, CaptureRules.Result(action = null, block = null))

        combine(capture, config.filterNotNull()) { result, cfg ->
            inputSources.forEach { it.setArmed(result.action != null, cfg) }
            status.update { it.copy(activeActionId = result.action?.id, block = result.block) }
        }.launchIn(scope)

        headsets
            .onEach { names -> status.update { it.copy(connectedHeadsets = names) } }
            .launchIn(scope)

        scope.launch {
            for (first in steps) {
                // Coalesce steps that arrived while the previous action was still running.
                var total = first
                while (true) total += steps.tryReceive().getOrNull() ?: break

                val action = capture.value.action ?: continue
                val cfg = config.value ?: continue
                val signed = if (cfg.invertDirection) -total else total
                if (signed != 0) run(action, signed, cfg, contextFactory)
            }
        }
    }

    fun stop() {
        inputSources.forEach { it.stop() }
        status.update { it.copy(serviceRunning = false, activeActionId = null) }
    }

    fun onForegroundAppChanged(packageName: String) {
        // Re-read lock state on every window change: some OEM builds (e.g. HyperOS) do not
        // reliably deliver unlock broadcasts.
        screenMonitor.refresh()
        if (foregroundPackage.value == packageName) return
        foregroundPackage.value = packageName
        status.update { it.copy(foregroundPackage = packageName) }
    }

    /** @return true if the event was consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean = inputSources.fold(false) { consumed, source ->
        source.onKeyEvent(event) || consumed
    }

    private suspend fun run(
        action: DialAction,
        steps: Int,
        config: DialConfig,
        contextFactory: (ActionSettingsValues) -> ActionContext,
    ) {
        if (config.hapticFeedback) haptics.tick()
        status.update { it.copy(lastEvent = EngineStatus.DialActivity(steps, action.id)) }
        val values = ActionSettingsValues(action.settings, config.actionValues[action.id].orEmpty())
        try {
            action.perform(steps, contextFactory(values))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Action ${action.id} failed", e)
        }
    }

    private companion object {
        const val TAG = "DialEngine"
    }
}

/** Pure decision of whether, and by which action, the dial is captured. */
internal object CaptureRules {

    data class Result(val action: DialAction?, val block: EngineStatus.Block?)

    fun resolve(
        config: DialConfig,
        packageName: String?,
        headsets: List<String>,
        screen: ScreenStateMonitor.State,
        actionFor: (String) -> DialAction?,
    ): Result {
        val block = when {
            !config.enabled -> EngineStatus.Block.DISABLED
            !screen.interactive -> EngineStatus.Block.SCREEN_OFF
            screen.locked -> EngineStatus.Block.LOCKED
            config.requireHeadset && !HeadsetMonitor.matches(headsets, config.headsetNameFilter) ->
                EngineStatus.Block.NO_HEADSET
            else -> null
        }
        if (block != null) return Result(action = null, block = block)
        val action = actionFor(config.actionIdFor(packageName))?.takeIf { it.interceptsDial }
            ?: return Result(action = null, block = EngineStatus.Block.APP_PASSES_THROUGH)
        return Result(action = action, block = null)
    }
}
