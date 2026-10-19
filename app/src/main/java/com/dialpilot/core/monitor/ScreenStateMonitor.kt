package com.dialpilot.core.monitor

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Whether the screen is on and unlocked. While it is not, the dial is left as a volume control,
 * so turning it with the phone in a pocket behaves normally.
 */
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val powerManager: PowerManager,
    private val keyguardManager: KeyguardManager,
) {
    val isUsable: Flow<Boolean> = callbackFlow {
        fun publish() = trySend(powerManager.isInteractive && !keyguardManager.isKeyguardLocked)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                publish()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        publish()
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
