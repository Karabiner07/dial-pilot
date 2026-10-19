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
import com.dialpilot.core.engine.EngineStatus
import com.dialpilot.core.settings.DialConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads Smart Dial *presses* set to "Skip forward" / "Skip back" in the Nothing X app.
 *
 * Some phones never learn about dial rotation: the buds apply it to their own volume. Presses
 * mapped to skip, however, reach the phone as AVRCP media keys, which Android hands to the
 * "media button session": the session of the app that most recently started playing audio.
 * While armed, this source claims that role with its own [MediaSession] and a looping silent
 * [AudioTrack], and re-claims it whenever another app starts playing.
 *
 * Next/fast-forward become +1 step, previous/rewind -1. Play/pause is swallowed while armed.
 */
class MediaKeyDialSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AudioManager,
    private val status: EngineStatus,
) : DialInputSource {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sink: ((Int) -> Unit)? = null
    private var session: MediaSession? = null
    private var silence: AudioTrack? = null
    private var lastClaimAt = 0L

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            // Another app started playing and may now receive the media keys. Our own re-claim
            // also lands here, so ignore changes right after claiming.
            if (SystemClock.uptimeMillis() - lastClaimAt > RECLAIM_DEBOUNCE_MS) reclaim()
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
            setCallback(callback, mainHandler)
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
        status.update { it.copy(listeningForPresses = true) }
    }

    private fun release() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        mainHandler.removeCallbacksAndMessages(null)
        silence?.run {
            runCatching { stop() }
            release()
        }
        silence = null
        session?.release()
        session = null
        status.update { it.copy(listeningForPresses = false) }
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
        mainHandler.postDelayed(::claim, RECLAIM_GAP_MS)
    }

    private val callback = object : MediaSession.Callback() {
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val event = mediaButtonIntent.keyEvent() ?: return false
            status.update { it.copy(pressEvents = it.pressEvents + if (event.action == KeyEvent.ACTION_DOWN) 1 else 0) }
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> sink?.invoke(1)
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> sink?.invoke(-1)
            }
            return true
        }

        // Some stacks call the transport callbacks directly instead of onMediaButtonEvent.
        override fun onSkipToNext() {
            sink?.invoke(1)
        }

        override fun onSkipToPrevious() {
            sink?.invoke(-1)
        }
    }

    private fun createSilentTrack(): AudioTrack {
        val frames = SAMPLE_RATE / 2
        val track = AudioTrack.Builder()
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
        track.write(ShortArray(frames), 0, frames)
        track.setLoopPoints(0, frames, -1)
        return track
    }

    private fun Intent.keyEvent(): KeyEvent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_KEY_EVENT)
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
    }
}
