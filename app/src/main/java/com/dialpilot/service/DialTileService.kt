package com.dialpilot.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dialpilot.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick Settings tile that toggles dial control on and off. */
@AndroidEntryPoint
class DialTileService : TileService() {

    @Inject lateinit var settings: SettingsRepository

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = scope.launch {
            settings.config.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                qsTile?.apply {
                    state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch { settings.toggleEnabled() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
