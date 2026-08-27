package com.lagradost.cloudstream3.shared.ui.search
 
import androidx.compose.runtime.Immutable

/**
 * Display modes for search results in the Search UI.
 */
@Immutable
enum class SearchDisplayMode {
    /**
     * Interleaved/bundled results displayed in a responsive adaptive grid.
     */
    Unified,

    /**
     * Results organized into distinct sections grouped by content provider.
     */
    Grouped
}
