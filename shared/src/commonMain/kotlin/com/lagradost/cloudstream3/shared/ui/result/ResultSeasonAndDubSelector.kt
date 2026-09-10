package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultSeason
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.ui.tooling.preview.Preview

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

@Preview
@Composable
private fun ResultSeasonAndDubSelectorPreview() {
    MaterialTheme {
        ResultSeasonAndDubSelector(
            state = ResultState(
                isEpisodeBased = true,
                availableDubStatuses = persistentListOf(DubStatus.Subbed, DubStatus.Dubbed),
                availableSeasons = persistentListOf(
                    ResultSeason(season = 1, name = "Season 1", episodeCount = 12),
                    ResultSeason(season = 2, name = "Season 2", episodeCount = 24)
                ),
                selectedSeason = 1
            ),
            onEvent = {}
        )
    }
}
