package com.dialpilot.actions

import com.dialpilot.R
import com.dialpilot.core.action.ActionContext
import com.dialpilot.core.action.ActionIds
import com.dialpilot.core.action.DialAction
import javax.inject.Inject

/** Leaves the dial alone so it keeps its normal Nothing X behaviour. Useful as a per-app rule. */
class PassThroughAction @Inject constructor() : DialAction {
    override val id = ActionIds.PASS_THROUGH
    override val titleRes = R.string.action_pass_through
    override val descriptionRes = R.string.action_pass_through_desc
    override val interceptsDial = false

    override suspend fun perform(steps: Int, context: ActionContext) = Unit
}
