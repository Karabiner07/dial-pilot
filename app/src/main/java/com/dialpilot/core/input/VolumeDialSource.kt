package com.dialpilot.core.input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.dialpilot.core.settings.DialConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads Smart Dial *rotation* through the media volume. Opt-in ([DialConfig.useDialRotation]).
 *
 * With the dial set to Volume in Nothing X, buds that support Bluetooth absolute volume report
 * each click to the phone, which applies it to STREAM_MUSIC. Many phones never receive these
 * reports (the buds change their own volume instead); there this source sees nothing.
 *
 * While armed it:
 *  1. pins the music volume at an "anchor" level, never at 0 or max, so both directions are
 *     detectable,
 *  2. treats every change away from the anchor as dial steps, and
 *  3. restores the anchor immediately, so the listening volume does not drift.
 *
 * Changes caused by the phone's own volume keys are recognised through [onKeyEvent] and either
 * become steps too ([DialConfig.phoneKeysAsDial]) or are accepted as a new anchor.
 */
class VolumeDialSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AudioManager,
) : DialInputSource {

    private var sink: ((Int) -> Unit)? = null
    private var anchor = NOT_ARMED
    private var phoneKeysAsDial = false
    private var phoneKeyGraceUntil = 0L
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
            onMusicVolumeChanged(intent.getIntExtra(EXTRA_STREAM_VALUE, -1))
        }
    }

    override fun start(sink: (steps: Int) -> Unit) {
        this.sink = sink
    }

    override fun stop() {
        disarm()
        sink = null
    }

    override fun setArmed(armed: Boolean, config: DialConfig) {
        phoneKeysAsDial = config.phoneKeysAsDial
        when {
            !armed || !config.useDialRotation -> disarm()
            anchor == NOT_ARMED -> arm()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        if (anchor == NOT_ARMED) return false
        if (phoneKeysAsDial) {
            if (event.action == KeyEvent.ACTION_DOWN) sink?.invoke(direction)
            return true
        }
        // Let the key change the volume normally, and remember not to read it as a dial turn.
        phoneKeyGraceUntil = SystemClock.uptimeMillis() + PHONE_KEY_GRACE_MS
        return false
    }

    private fun arm() {
        if (!registered) {
            // VOLUME_CHANGED_ACTION is a system broadcast without a public constant. It must be
            // registered as exported to be delivered; only the system can send it.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_VOLUME_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        }
        anchor = pinAnchor(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    private fun disarm() {
        anchor = NOT_ARMED
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun onMusicVolumeChanged(volume: Int) {
        if (anchor == NOT_ARMED || volume < 0 || volume == anchor) return
        if (SystemClock.uptimeMillis() < phoneKeyGraceUntil) {
            anchor = pinAnchor(volume)
            return
        }
        val steps = volume - anchor
        // No FLAG_SHOW_UI: restoring should be invisible.
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, anchor, 0)
        sink?.invoke(steps)
    }

    /** Clamps [volume] away from both ends and applies it if needed. */
    private fun pinAnchor(volume: Int): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pinned = volume.coerceIn(1, (max - 1).coerceAtLeast(1))
        if (pinned != volume) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, pinned, 0)
        return pinned
    }

    private companion object {
        const val NOT_ARMED = -1
        const val PHONE_KEY_GRACE_MS = 600L
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    }
}
