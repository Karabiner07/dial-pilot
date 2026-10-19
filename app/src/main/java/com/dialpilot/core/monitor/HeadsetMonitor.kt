package com.dialpilot.core.monitor

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Names of the Bluetooth audio outputs currently connected. Needs no runtime permission. */
class HeadsetMonitor @Inject constructor(
    private val audioManager: AudioManager,
) {
    private val bluetoothTypes = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    val connectedHeadsets: Flow<List<String>> = callbackFlow {
        fun publish() {
            val names = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in bluetoothTypes }
                .map { it.productName.toString() }
                .distinct()
            trySend(names)
        }

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = publish()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = publish()
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        publish()
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    companion object {
        fun matches(names: List<String>, filter: String): Boolean =
            names.any { filter.isBlank() || it.contains(filter, ignoreCase = true) }
    }
}
