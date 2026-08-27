package com.lagradost.cloudstream3.shared.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchResponse
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SectionHeader
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SubtitleText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.TitleText
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Adaptive search results grid and grouped presentation view.
 * Supports Unified adaptive grid (minSize = 140.dp) and Grouped by provider rows,
 * with infinite scroll pagination, empty states, loading feedback, and error handling.
 */
@Composable
fun SearchResultsGrid(
    results: List<SearchResponse>,
    groupedResults: Map<String, List<SearchResponse>>,
    displayMode: SearchDisplayMode,
    isLoading: Boolean,
    isPaginating: Boolean,
    hasNextPage: Boolean,
    error: String?,
    query: String,
    onLoadNextPage: () -> Unit,
    onExpandProvider: (String) -> Unit,
    onItemClick: (SearchResponse) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    searchHistory: List<String> = emptyList(),
    onSelectHistoryQuery: ((String) -> Unit)? = null,
    onRemoveHistoryQuery: ((String) -> Unit)? = null,
    onClearHistory: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Initial Loading State
            isLoading && results.isEmpty() -> {
                SearchLoadingView(message = stringResource(Res.string.searching))
            }

            // 2. Initial Empty State (With Search History or Default Welcome View)
            query.isBlank() && results.isEmpty() -> {
                if (searchHistory.isNotEmpty() && onSelectHistoryQuery != null && onRemoveHistoryQuery != null && onClearHistory != null) {
                    SearchHistoryView(
                        searchHistory = searchHistory,
                        onSelectQuery = onSelectHistoryQuery,
                        onRemoveQuery = onRemoveHistoryQuery,
                        onClearHistory = onClearHistory
                    )
                } else {
                    SearchInitialEmptyView()
                }
            }

            // 3. No Results Found State (with non-blank query)
            !isLoading && results.isEmpty() && error != null -> {
                SearchErrorView(
                    errorMessage = error,
                    onRetry = onRetry
                )
            }

            !isLoading && results.isEmpty() -> {
                SearchNoResultsView(query = query)
            }

            // 4. Content Presentation (Unified vs Grouped)
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Non-blocking Error Banner (if partial results exist but an error occurred)
                    if (error != null) {
                        SearchErrorBanner(
                            errorMessage = error,
                            onDismiss = onDismissError,
                            onRetry = onRetry
                        )
                    }

                    when (displayMode) {
                        SearchDisplayMode.Unified -> {
                            UnifiedResultsGrid(
                                results = results,
                                isPaginating = isPaginating,
                                hasNextPage = hasNextPage,
                                isLoading = isLoading,
                                onLoadNextPage = onLoadNextPage,
                                onItemClick = onItemClick
                            )
                        }

                        SearchDisplayMode.Grouped -> {
                            GroupedResultsList(
                                groupedResults = groupedResults,
                                isPaginating = isPaginating,
                                onExpandProvider = onExpandProvider,
                                onItemClick = onItemClick
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Unified adaptive grid presenting all interleaved results.
 */
@Composable
private fun UnifiedResultsGrid(
    results: List<SearchResponse>,
    isPaginating: Boolean,
    hasNextPage: Boolean,
    isLoading: Boolean,
    onLoadNextPage: () -> Unit,
    onItemClick: (SearchResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    // Detect scroll near end to trigger next page pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore, hasNextPage, isPaginating, isLoading) {
        if (shouldLoadMore && hasNextPage && !isPaginating && !isLoading) {
            onLoadNextPage()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = results,
            key = { item -> item.url }
        ) { item ->
            SearchResultCard(
                item = item,
                onClick = { onItemClick(item) },
                modifier = Modifier.fillMaxWidth().focusable()
            )
        }

        // Pagination loading footer
        if (isPaginating) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = CloudStreamColors.Primary
                        )
                        BodyMutedText(textRes = Res.string.loading)
                    }
                }
            }
        }
    }
}

/**
 * Grouped presentation showing results sectioned by provider.
 */
@Composable
private fun GroupedResultsList(
    groupedResults: Map<String, List<SearchResponse>>,
    isPaginating: Boolean,
    onExpandProvider: (String) -> Unit,
    onItemClick: (SearchResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        groupedResults.forEach { (providerName, items) ->
            item(key = providerName) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Provider Section Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = providerName,
                                style = MaterialTheme.typography.subtitle1.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextPrimary
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CloudStreamColors.Primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${items.size}",
                                    style = MaterialTheme.typography.caption.copy(
                                        color = CloudStreamColors.Primary,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Expand / Load more from this provider button
                        SecondaryButton(
                            textRes = Res.string.load_more,
                            onClick = { onExpandProvider(providerName) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Horizontal row of provider items
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = items,
                            key = { item -> "${providerName}_${item.url}" }
                        ) { item ->
                            SearchResultCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                modifier = Modifier.width(135.dp).focusable()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Local search history chips view rendered with FlowRow below search bar when query is empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryView(
    searchHistory: List<String>,
    onSelectQuery: (String) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(Res.string.history),
                    style = MaterialTheme.typography.subtitle2.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary
                    )
                )
            }

            GhostButton(
                textRes = Res.string.clear_history,
                onClick = onClearHistory,
                contentColor = CloudStreamColors.Primary,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val deleteString = stringResource(Res.string.delete)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            searchHistory.forEach { queryText ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CloudStreamColors.SurfaceVariant,
                    border = BorderStroke(1.dp, CloudStreamColors.Divider),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .focusable()
                        .clickable { onSelectQuery(queryText) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = queryText,
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 13.sp,
                                color = CloudStreamColors.TextPrimary
                            )
                        )
                        IconButton(
                            onClick = { onRemoveQuery(queryText) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = deleteString,
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SearchInitialEmptyView()
    }
}

/**
 * Initial empty state when user has not entered a query yet.
 */
@Composable
private fun SearchInitialEmptyView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CloudStreamColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CloudStreamColors.TextMuted,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TitleText(
            textRes = Res.string.discover_movies_title,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        BodyMutedText(
            textRes = Res.string.discover_movies_desc,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp)
        )
    }
}

/**
 * Empty state when a query returns no results.
 */
@Composable
private fun SearchNoResultsView(
    query: String,
    modifier: Modifier = Modifier
) {
    val noResultsText = stringResource(Res.string.noSearchResults)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CloudStreamColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CloudStreamColors.TextMuted,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TitleText(
            text = if (query.isNotBlank()) "$noResultsText (\"$query\")" else noResultsText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        BodyMutedText(
            textRes = Res.string.noSearchResultsDesc,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        )
    }
}

/**
 * Centered Loading View.
 */
@Composable
private fun SearchLoadingView(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = CloudStreamColors.Primary,
            modifier = Modifier.size(42.dp),
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        SubtitleText(
            text = message,
            color = CloudStreamColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Centered Error View with retry action.
 */
@Composable
private fun SearchErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = stringResource(Res.string.error),
            tint = CloudStreamColors.Error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.subtitle1.copy(
                fontWeight = FontWeight.Medium,
                color = CloudStreamColors.TextPrimary
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryButton(
            textRes = Res.string.retry_search,
            icon = Icons.Default.Refresh,
            onClick = onRetry
        )
    }
}

/**
 * Non-blocking Error Banner displayed on top of partial results.
 */
@Composable
private fun SearchErrorBanner(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        backgroundColor = CloudStreamColors.Error.copy(alpha = 0.15f),
        elevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CloudStreamColors.Error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.caption.copy(
                        color = CloudStreamColors.Error,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .size(28.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.retry),
                        tint = CloudStreamColors.Error,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = CloudStreamColors.Error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

