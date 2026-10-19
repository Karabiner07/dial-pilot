package com.dialpilot.core.action

import javax.inject.Inject
import javax.inject.Singleton

/** All actions bound into the Hilt set, in a stable display order. */
@Singleton
class ActionRegistry @Inject constructor(actions: Set<@JvmSuppressWildcards DialAction>) {

    val all: List<DialAction> = actions.sortedBy { ORDER.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }

    private val byId = all.associateBy { it.id }

    init {
        require(byId.size == all.size) { "Duplicate DialAction ids: ${all.map { it.id }}" }
    }

    operator fun get(id: String): DialAction? = byId[id]

    private companion object {
        /** Actions not listed here are shown after these, in arbitrary order. */
        val ORDER = listOf(
            ActionIds.SCROLL_VERTICAL,
            ActionIds.SWIPE_ITEMS,
            ActionIds.PAGE_SCROLL,
            ActionIds.SCROLL_HORIZONTAL,
            ActionIds.ZOOM,
            ActionIds.MEDIA_TRACKS,
            ActionIds.PASS_THROUGH,
        )
    }
}

object ActionIds {
    /** Persisted as "volume" from when this action was named after the dial's default. */
    const val PASS_THROUGH = "volume"
    const val SCROLL_VERTICAL = "scroll_vertical"
    const val SCROLL_HORIZONTAL = "scroll_horizontal"
    const val SWIPE_ITEMS = "swipe_items"
    const val PAGE_SCROLL = "page_scroll"
    const val ZOOM = "zoom"
    const val MEDIA_TRACKS = "media_tracks"
}
