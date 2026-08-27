package com.lagradost.cloudstream3.actions

import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText

/**
 * Multiplatform abstraction representing an external playback or click action.
 * Exposes core metadata: identifier, localized display name, player capability, and supported source link types.
 */
interface ExternalPlayerAction {
    /** Unique identifier for the action (defaults to simple class name). */
    val id: String
        get() = this::class.simpleName ?: "unknown"

    /** Localized or formatted display name for presentation in UI. */
    val name: UiText

    /** Indicates whether this action is a video player capable of being configured as the default player. */
    val isPlayer: Boolean
        get() = false

    /** Set of source stream/link types supported by this action (e.g., M3U8, DASH, Torrent, Magnet). */
    val sourceTypes: Set<ExtractorLinkType>
        get() = ExtractorLinkType.entries.toSet()
}
