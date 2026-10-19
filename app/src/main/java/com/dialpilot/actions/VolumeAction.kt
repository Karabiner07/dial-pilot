package com.dialpilot.actions

import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.DialAction
import javax.inject.Inject

/** Leaves the dial alone so it keeps controlling volume. Useful as a per-app rule. */
class VolumeAction @Inject constructor() : DialAction {
    override val id = ActionIds.VOLUME
    override val titleRes = R.string.action_volume
    override val descriptionRes = R.string.action_volume_desc
    override val interceptsDial = false

    override suspend fun perform(steps: Int, context: ActionContext) = Unit
}
