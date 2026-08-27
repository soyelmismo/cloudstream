package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState

/**
 * Season & Dub/Sub selector component for anime and TV series.
 * Delegates directly to the canonical [ResultEpisodesSelectorHeader].
 */
@Composable
fun ResultSeasonAndDubSelector(
    state: ResultState,
    onEvent: (ResultEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ResultEpisodesSelectorHeader(
        state = state,
        onEvent = onEvent,
        modifier = modifier
    )
}
