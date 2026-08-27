package com.lagradost.cloudstream3.ui

import cloudstream.shared_ui.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class WatchType(val internalId: Int, val stringRes: StringResource, val iconRes: DrawableResource) {
    WATCHING(0, Res.string.type_watching, Res.drawable.ic_baseline_bookmark_24),
    COMPLETED(1, Res.string.type_completed, Res.drawable.ic_baseline_bookmark_24),
    ONHOLD(2, Res.string.type_on_hold, Res.drawable.ic_baseline_bookmark_24),
    DROPPED(3, Res.string.type_dropped, Res.drawable.ic_baseline_bookmark_24),
    PLANTOWATCH(4, Res.string.type_plan_to_watch, Res.drawable.ic_baseline_bookmark_24),
    NONE(5, Res.string.type_none, Res.drawable.ic_baseline_add_24);

    companion object {
        fun fromInternalId(id: Int?) = entries.find { value -> value.internalId == id } ?: NONE
    }
}

enum class SyncWatchType(val internalId: Int, val stringRes: StringResource, val iconRes: DrawableResource) {
    NONE(-1, Res.string.type_none, Res.drawable.ic_baseline_add_24),
    WATCHING(0, Res.string.type_watching, Res.drawable.ic_baseline_bookmark_24),
    COMPLETED(1, Res.string.type_completed, Res.drawable.ic_baseline_bookmark_24),
    ONHOLD(2, Res.string.type_on_hold, Res.drawable.ic_baseline_bookmark_24),
    DROPPED(3, Res.string.type_dropped, Res.drawable.ic_baseline_bookmark_24),
    PLANTOWATCH(4, Res.string.type_plan_to_watch, Res.drawable.ic_baseline_bookmark_24),
    REWATCHING(5, Res.string.type_re_watching, Res.drawable.ic_baseline_bookmark_24);

    companion object {
        fun fromInternalId(id: Int?) = entries.find { value -> value.internalId == id } ?: NONE
    }
}
