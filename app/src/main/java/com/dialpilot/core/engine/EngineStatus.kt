package com.dialpilot.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Live engine state, shared with the UI. */
@Singleton
class EngineStatus @Inject constructor() {

    /** Why the dial is not being captured right now. */
    enum class Block { DISABLED, SCREEN_OFF, LOCKED, NO_HEADSET, APP_PASSES_THROUGH }

    data class State(
        val serviceRunning: Boolean = false,
        val foregroundPackage: String? = null,
        /** Action currently claiming the dial, or null when the dial behaves normally. */
        val activeActionId: String? = null,
        /** Set when [activeActionId] is null while the service is running. */
        val block: Block? = null,
        val connectedHeadsets: List<String> = emptyList(),
        val lastEvent: DialActivity? = null,
    )

    data class DialActivity(val steps: Int, val actionId: String)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: (State) -> State) = _state.update(transform)
}
