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
 * Reads the Smart Dial through the media volume.
 *
 * The CMF Buds Pro 2 dial is configured in the Nothing X app to control volume. Turning it makes
 * the buds report a new volume over Bluetooth (AVRCP), which Android applies to STREAM_MUSIC.
 * There is no public API that exposes the dial itself, so this source:
 *  1. pins the music volume at an "anchor" level while armed (never at 0 or max, so both
 *     directions stay detectable),
 *  2. treats every change away from the anchor as dial steps, and
 *  3. immediately restores the anchor, so the listening volume does not drift.
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
            onMusicVolumeChanged(intent.getIntExtra(EXTRA_STREAM_VALUE, -1))
        }
    }

    override fun start(sink: (steps: Int) -> Unit) {
        this.sink = sink
        // VOLUME_CHANGED_ACTION is a system broadcast that has no public constant. It must be
        // exported to be delivered; only the system can send it.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_VOLUME_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun stop() {
        sink = null
        anchor = NOT_ARMED
        runCatching { context.unregisterReceiver(receiver) }
    }

    override fun setArmed(armed: Boolean, config: DialConfig) {
        phoneKeysAsDial = config.phoneKeysAsDial
        when {
            !armed -> anchor = NOT_ARMED
            anchor == NOT_ARMED -> anchor = pinAnchor(currentVolume())
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        if (anchor != NOT_ARMED && phoneKeysAsDial) {
            if (event.action == KeyEvent.ACTION_DOWN) sink?.invoke(direction)
            return true
        }
        // Let the key change the volume normally, and remember not to read it as a dial turn.
        phoneKeyGraceUntil = SystemClock.uptimeMillis() + PHONE_KEY_GRACE_MS
        return false
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

    private fun currentVolume() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private companion object {
        const val NOT_ARMED = -1
        const val PHONE_KEY_GRACE_MS = 600L
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    }
}
