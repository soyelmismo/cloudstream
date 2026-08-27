package com.lagradost.cloudstream3.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamActionChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDropdownFilter
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.SearchFilters
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Scrollable bar of filter chips for selecting providers, media types, qualities,
 * NSFW filter toggle, and clearing all active filters.
 */
@Composable
fun SearchFiltersBar(
    filters: SearchFilters,
    availableProviders: List<MainAPI>,
    availableTypes: Set<TvType>,
    availableQualities: Set<SearchQuality>,
    onToggleProvider: (String) -> Unit,
    onToggleType: (TvType) -> Unit,
    onToggleQuality: (SearchQuality) -> Unit,
    onToggleNsfw: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hasActiveFilters = filters.selectedProviders.isNotEmpty() ||
            filters.selectedTypes.isNotEmpty() ||
            filters.selectedQualities.isNotEmpty() ||
            filters.hideNsfw

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clear all active filters chip
        if (hasActiveFilters) {
            CloudStreamActionChip(
                label = stringResource(Res.string.clear_filters),
                icon = Icons.Default.Close,
                onClick = onClearFilters,
                containerColor = CloudStreamColors.Error.copy(alpha = 0.15f),
                contentColor = CloudStreamColors.Error
            )
        }

        // Provider Selector Multi-select Dropdown Chip
        if (availableProviders.isNotEmpty()) {
            ProvidersDropdownChip(
                availableProviders = availableProviders,
                selectedProviders = filters.selectedProviders,
                onToggleProvider = onToggleProvider
            )
        }

        // Divider
        FilterBarDivider()

        // Media Type Chips (Movies, TV Series, Anime, Live Stream)
        val defaultTypes = listOf(
            TvType.Movie to stringResource(Res.string.movies),
            TvType.TvSeries to stringResource(Res.string.tv_series),
            TvType.Anime to stringResource(Res.string.anime),
            TvType.Live to stringResource(Res.string.typeLive)
        )
        for ((type, label) in defaultTypes) {
            val isSelected = filters.selectedTypes.contains(type)
            CloudStreamFilterChip(
                label = label,
                isSelected = isSelected,
                onClick = { onToggleType(type) }
            )
        }

        // Additional types if available in state
        for (extraType in availableTypes) {
            if (defaultTypes.none { it.first == extraType } && extraType != TvType.NSFW) {
                val isSelected = filters.selectedTypes.contains(extraType)
                CloudStreamFilterChip(
                    label = extraType.name,
                    isSelected = isSelected,
                    onClick = { onToggleType(extraType) }
                )
            }
        }

        // Divider
        FilterBarDivider()

        // Quality Filter Dropdown / Chips
        QualityDropdownChip(
            availableQualities = availableQualities,
            selectedQualities = filters.selectedQualities,
            onToggleQuality = onToggleQuality
        )

        // Divider
        FilterBarDivider()

        // NSFW Switch Chip
        CloudStreamFilterChip(
            label = stringResource(Res.string.hideNsfw),
            isSelected = filters.hideNsfw,
            onClick = { onToggleNsfw(!filters.hideNsfw) },
            activeContainerColor = CloudStreamColors.NsfwContainer,
            activeContentColor = CloudStreamColors.NsfwContent
        )
    }
}

/**
 * Dropdown chip for selecting multiple providers using centralized [CloudStreamDropdownFilter].
 */
@Composable
private fun ProvidersDropdownChip(
    availableProviders: List<MainAPI>,
    selectedProviders: Set<String>,
    onToggleProvider: (String) -> Unit
) {
    val isFiltered = selectedProviders.isNotEmpty()
    val label = if (isFiltered) {
        stringResource(Res.string.filter_providers_count_format, selectedProviders.size, availableProviders.size)
    } else {
        stringResource(Res.string.filter_all_providers)
    }

    val selectedSet = availableProviders.filter { selectedProviders.contains(it.name) }.toSet()

    CloudStreamDropdownFilter(
        label = label,
        items = availableProviders,
        selectedItems = selectedSet,
        onToggleItem = { onToggleProvider(it.name) },
        itemLabel = { it.name },
        menuTitleRes = Res.string.select_providers,
        minMenuWidth = 200.dp,
        maxMenuWidth = 280.dp
    )
}

/**
 * Dropdown chip for selecting media qualities using centralized [CloudStreamDropdownFilter].
 */
@Composable
private fun QualityDropdownChip(
    availableQualities: Set<SearchQuality>,
    selectedQualities: Set<SearchQuality>,
    onToggleQuality: (SearchQuality) -> Unit
) {
    val isFiltered = selectedQualities.isNotEmpty()
    val label = if (isFiltered) {
        stringResource(Res.string.filter_quality_count_format, selectedQualities.size)
    } else {
        stringResource(Res.string.filter_qualities)
    }

    val qualitiesList = if (availableQualities.isNotEmpty()) {
        availableQualities.toList()
    } else {
        listOf(
            SearchQuality.FourK,
            SearchQuality.HD,
            SearchQuality.HQ,
            SearchQuality.BlueRay,
            SearchQuality.Cam
        )
    }

    CloudStreamDropdownFilter(
        label = label,
        items = qualitiesList,
        selectedItems = selectedQualities,
        onToggleItem = onToggleQuality,
        itemLabel = { it.name },
        menuTitleRes = Res.string.filter_media_quality,
        minMenuWidth = 160.dp,
        maxMenuWidth = 220.dp
    )
}


@Composable
private fun FilterBarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.15f))
    )
}
