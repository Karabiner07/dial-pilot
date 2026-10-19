package com.dialpilot.core.input

import android.view.KeyEvent
import com.dialpilot.core.settings.DialConfig

/**
 * Something that turns a physical control into signed rotation steps.
 *
 * Sources are bound into a Hilt set (see [com.dialpilot.di.InputModule]), so a new way of
 * reading the dial (for example a vendor protocol, if one is ever documented) can be added
 * without touching the engine.
 */
interface DialInputSource {

    /** Starts listening. Steps are delivered on the main thread. */
    fun start(sink: (steps: Int) -> Unit)

    fun stop()

    /**
     * Called whenever the engine starts or stops claiming the dial, or the config changes.
     * While not armed a source must leave the underlying control alone.
     */
    fun setArmed(armed: Boolean, config: DialConfig)

    /**
     * Key events seen by the accessibility service before apps get them.
     *
     * @return true to consume the event.
     */
    fun onKeyEvent(event: KeyEvent): Boolean = false
}
