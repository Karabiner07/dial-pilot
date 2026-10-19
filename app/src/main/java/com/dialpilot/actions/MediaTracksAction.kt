package com.dialpilot.actions

import android.os.SystemClock
import android.view.KeyEvent
import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.ActionSetting
import com.dialpilot.core.action.DialAction
import javax.inject.Inject

/** Next / previous track in whatever is playing. */
class MediaTracksAction @Inject constructor() : DialAction {
    override val id = ActionIds.MEDIA_TRACKS
    override val titleRes = R.string.action_media_tracks
    override val descriptionRes = R.string.action_media_tracks_desc

    override val settings = listOf(
        ActionSetting.Slider(KEY_COOLDOWN, R.string.setting_cooldown, 0..2000, default = 600, step = 50, unit = " ms"),
    )

    private var lastSkipAt = 0L

    override suspend fun perform(steps: Int, context: ActionContext) {
        val now = SystemClock.uptimeMillis()
        if (now - lastSkipAt < context.settings.int(KEY_COOLDOWN)) return
        lastSkipAt = now
        context.dispatchMediaKey(if (steps > 0) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private companion object {
        const val KEY_COOLDOWN = "cooldown"
    }
}
