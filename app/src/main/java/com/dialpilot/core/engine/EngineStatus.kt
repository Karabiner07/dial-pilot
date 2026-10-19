package com.dialpilot.core.engine

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Live engine state, shared with the UI for status and calibration. */
@Singleton
class EngineStatus @Inject constructor() {

    /** Why the dial is not being captured right now. */
    enum class Block { DISABLED, SCREEN_OFF, LOCKED, NO_HEADSET, APP_USES_VOLUME }

    data class State(
        val serviceRunning: Boolean = false,
        val foregroundPackage: String? = null,
        /** Action currently claiming the dial, or null when the dial behaves normally. */
        val activeActionId: String? = null,
        /** Set when [activeActionId] is null and the service is running. */
        val block: Block? = null,
        val connectedHeadsets: List<String> = emptyList(),
        val lastEvent: DialActivity? = null,
        /** Media volume changes seen by the app, whether or not the dial is captured. */
        val volumeEvents: Int = 0,
        val musicVolume: Int = -1,
        val musicMaxVolume: Int = -1,
    )

    data class DialActivity(val steps: Int, val actionId: String, val atMillis: Long)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _testRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Requests from the UI to run the default action once, bypassing the dial. */
    val testRequests: SharedFlow<Unit> = _testRequests.asSharedFlow()

    fun update(transform: (State) -> State) = _state.update(transform)

    fun requestTest() {
        _testRequests.tryEmit(Unit)
    }
}
