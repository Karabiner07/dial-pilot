package com.dialpilot.core.input

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.dialpilot.core.settings.DialConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads Smart Dial *presses* set to "Skip forward" / "Skip back" in the Nothing X app.
 *
 * Presses mapped to skip reach the phone as AVRCP media keys, which Android hands to the
 * "media button session": the session of the app that most recently started playing audio.
 * While armed, this source claims that role with its own [MediaSession] and a looping silent
 * [AudioTrack], and re-claims it whenever another app starts playing.
 *
 * Next / fast-forward become +1 step, previous / rewind -1. Other keys (play/pause) are swallowed
 * while armed, since there is no way to hand them on to the app that would normally get them.
 */
class MediaKeyDialSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AudioManager,
) : DialInputSource {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val claimRunnable = Runnable(::claim)
    private var sink: ((Int) -> Unit)? = null
    private var session: MediaSession? = null
    private var silence: AudioTrack? = null
    private var lastClaimAt = 0L

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            // Another app started playing and may now receive the media keys. Our own re-claim
            // lands here too, so ignore changes right after claiming.
            if (SystemClock.uptimeMillis() - lastClaimAt > RECLAIM_DEBOUNCE_MS) reclaim()
        }
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val event = mediaButtonIntent.keyEvent() ?: return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                stepFor(event.keyCode)?.let { sink?.invoke(it) }
            }
            return true
        }

        // Some Bluetooth stacks call the transport controls directly instead of sending keys.
        override fun onSkipToNext() {
            sink?.invoke(1)
        }

        override fun onSkipToPrevious() {
            sink?.invoke(-1)
        }
    }

    override fun start(sink: (steps: Int) -> Unit) {
        this.sink = sink
    }

    override fun stop() {
        release()
        sink = null
    }

    override fun setArmed(armed: Boolean, config: DialConfig) {
        val wanted = armed && config.captureDialPresses
        when {
            wanted && session == null -> acquire()
            !wanted && session != null -> release()
        }
    }

    private fun acquire() {
        session = MediaSession(context, TAG).apply {
            setCallback(sessionCallback, mainHandler)
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(HANDLED_ACTIONS)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            isActive = true
        }
        silence = createSilentTrack()
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        claim()
    }

    private fun release() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        mainHandler.removeCallbacks(claimRunnable)
        silence?.run {
            runCatching { stop() }
            release()
        }
        silence = null
        session?.release()
        session = null
    }

    /** Starts the silent track so this app is the most recent audio player. */
    private fun claim() {
        lastClaimAt = SystemClock.uptimeMillis()
        silence?.play()
    }

    /**
     * Pauses briefly, then plays again. Android orders media-key targets by when an app's
     * playback last went from inactive to active, so this moves this app back to the front.
     */
    private fun reclaim() {
        val track = silence ?: return
        lastClaimAt = SystemClock.uptimeMillis()
        track.pause()
        mainHandler.postDelayed(claimRunnable, RECLAIM_GAP_MS)
    }

    private fun createSilentTrack(): AudioTrack {
        val frames = SAMPLE_RATE / 2
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * BYTES_PER_FRAME)
            .build()
            .apply {
                write(ShortArray(frames), 0, frames)
                setLoopPoints(0, frames, -1)
            }
    }

    private companion object {
        const val TAG = "DialPilotPresses"
        const val SAMPLE_RATE = 8_000
        const val BYTES_PER_FRAME = 2
        const val RECLAIM_DEBOUNCE_MS = 1_500L
        const val RECLAIM_GAP_MS = 150L
        const val HANDLED_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS

        fun stepFor(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> 1
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> -1
            else -> null
        }

        fun Intent.keyEvent(): KeyEvent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
    }
}
