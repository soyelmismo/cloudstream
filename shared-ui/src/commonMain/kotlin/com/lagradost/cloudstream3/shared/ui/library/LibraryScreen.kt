package com.lagradost.cloudstream3.shared.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.DubSubBadges
import com.lagradost.cloudstream3.shared.ui.components.QualityBadge
import com.lagradost.cloudstream3.shared.ui.components.TypeBadge
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamActionChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamDropdownFilter
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamEmptyState
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryEffect
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryEvent
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryItem
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryProvider
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryState
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryTab
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryViewModel
import com.lagradost.cloudstream3.shared.viewmodels.library.SortOrder
import com.lagradost.cloudstream3.shared.viewmodels.library.UnifiedLibraryItem
import com.lagradost.cloudstream3.shared.viewmodels.library.WatchStatus
import com.lagradost.cloudstream3.shared.viewmodels.library.toUnifiedItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful Library & Bookmarks Screen observing [LibraryViewModel].
 *
 * @param viewModel LibraryViewModel providing MVI state and repository streams.
 * @param onNavigateToDetails Callback to navigate to details view for a media item.
 * @param onNavigateToHome Callback to navigate to Home screen when empty.
 * @param onSearchMedia Callback to navigate to global search with pre-filled query.
 * @param modifier Optional root modifier.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToDetails: (url: String, apiName: String) -> Unit,
    onNavigateToHome: () -> Unit,
    onSearchMedia: ((query: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is LibraryEffect.NavigateToDetails -> {
                    onNavigateToDetails(effect.url, effect.apiName)
                }

                is LibraryEffect.SearchMedia -> {
                    onSearchMedia?.invoke(effect.query)
                }

                is LibraryEffect.NavigateToHome -> {
                    onNavigateToHome()
                }

                is LibraryEffect.ShowToast -> {
                    // Handled if toast presenter is available
                }
            }
        }
    }

    LibraryScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onNavigateToDetails = onNavigateToDetails,
        onNavigateToHome = onNavigateToHome,
        onSearchMedia = onSearchMedia,
        modifier = modifier
    )
}

/**
 * Stateless Library & Bookmarks Screen Content composable.
 */
@Composable
fun LibraryScreenContent(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    onNavigateToDetails: (url: String, apiName: String) -> Unit,
    onNavigateToHome: () -> Unit,
    onSearchMedia: ((query: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedItemIds by remember { mutableStateOf(setOf<Int>()) }
    var isBatchMoveDialogOpen by remember { mutableStateOf(false) }
    var isBatchDeleteDialogOpen by remember { mutableStateOf(false) }

    val isLocal = state.selectedProviderId == "local"
    val isSelectionMode = isLocal && selectedItemIds.isNotEmpty()
    val displayedItems = if (state.displayedItems.isNotEmpty()) {
        state.displayedItems
    } else {
        state.filteredItems.map { it.toUnifiedItem() }
    }

    Scaffold(
        backgroundColor = CloudStreamColors.Background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // -------------------------------------------------------------
            // Top Section: Header with Title, Provider Selector, Search Bar, and Filter Bar
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CloudStreamColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isSelectionMode) {
                    LibraryBatchActionBar(
                        selectedCount = selectedItemIds.size,
                        totalCount = displayedItems.size,
                        onSelectAll = {
                            selectedItemIds = if (selectedItemIds.size == displayedItems.size) {
                                emptySet()
                            } else {
                                displayedItems.mapNotNull { it.id.toIntOrNull() }.toSet()
                            }
                        },
                        onClearSelection = { selectedItemIds = emptySet() },
                        onOpenMoveDialog = { isBatchMoveDialogOpen = true },
                        onOpenDeleteDialog = { isBatchDeleteDialogOpen = true }
                    )
                } else {
                    // Header Row: Title, Provider Selector (if > 1), Search Bar, and Refresh Button
                    LibraryTopHeader(
                        searchQuery = state.searchQuery,
                        onSearchQueryChange = { query ->
                            onEvent(LibraryEvent.Search(query))
                        },
                        onClearSearch = {
                            onEvent(LibraryEvent.ClearSearch)
                        },
                        availableProviders = state.availableProviders,
                        selectedProviderId = state.selectedProviderId,
                        onSelectProvider = { providerId ->
                            selectedItemIds = emptySet()
                            onEvent(LibraryEvent.SelectProvider(providerId))
                        },
                        isRefreshing = state.isRefreshing,
                        onRefresh = {
                            onEvent(LibraryEvent.RefreshLibrary)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media Type & Sort Order Filter Chips Row
                    LibraryFiltersBar(
                        selectedType = state.selectedType,
                        sortOrder = state.sortOrder,
                        onSelectType = { type ->
                            onEvent(LibraryEvent.SetFilterType(type))
                        },
                        onSelectSortOrder = { sort ->
                            onEvent(LibraryEvent.SetSortOrder(sort))
                        },
                        onClearFilters = {
                            onEvent(LibraryEvent.ClearFilters)
                        }
                    )
                }
            }

            // Subtle Refresh Progress Indicator
            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = CloudStreamColors.Primary,
                    backgroundColor = CloudStreamColors.SurfaceVariant
                )
            }

            // -------------------------------------------------------------
            // Scrollable Dynamic Tab Bar with Counters
            // -------------------------------------------------------------
            if (!isSelectionMode) {
                LibraryTabBar(
                    tabs = state.currentTabs,
                    selectedTabIndex = state.selectedTabIndex,
                    selectedTab = state.selectedTab,
                    tabCounts = state.tabCounts,
                    isLocal = isLocal,
                    onSelectTabIndex = { index ->
                        onEvent(LibraryEvent.SelectTabIndex(index))
                    },
                    onSelectTab = { status ->
                        onEvent(LibraryEvent.SelectTab(status))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // -------------------------------------------------------------
            // Content Area: Loading, Empty States, or Responsive Grid
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CloudStreamColors.Primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    state.isLibraryEmpty -> {
                        LibraryTotalEmptyView(
                            onExplore = onNavigateToHome,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    state.isFilteredEmpty -> {
                        LibraryFilteredEmptyView(
                            searchQuery = state.searchQuery,
                            onClearFilters = {
                                onEvent(LibraryEvent.ClearFilters)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        LibraryGrid(
                            items = displayedItems,
                            selectedItemIds = selectedItemIds,
                            isSelectionMode = isSelectionMode,
                            isLocal = isLocal,
                            onItemClick = { item ->
                                val intId = item.id.toIntOrNull()
                                if (isSelectionMode && intId != null) {
                                    selectedItemIds = if (selectedItemIds.contains(intId)) {
                                        selectedItemIds - intId
                                    } else {
                                        selectedItemIds + intId
                                    }
                                } else {
                                    onEvent(LibraryEvent.SelectItem(item))
                                }
                            },
                            onItemLongClick = { item ->
                                val intId = item.id.toIntOrNull()
                                if (isLocal && intId != null) {
                                    selectedItemIds = if (selectedItemIds.contains(intId)) {
                                        selectedItemIds - intId
                                    } else {
                                        selectedItemIds + intId
                                    }
                                }
                            },
                            onRemoveItem = { item ->
                                item.id.toIntOrNull()?.let { id ->
                                    onEvent(LibraryEvent.RemoveBookmark(id))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Modal: Batch Move / Change Watch Status Dialog
    if (isBatchMoveDialogOpen) {
        LibraryBatchMoveDialog(
            onSelectStatus = { newStatus ->
                onEvent(LibraryEvent.BatchSetWatchStatus(selectedItemIds, newStatus))
                selectedItemIds = emptySet()
                isBatchMoveDialogOpen = false
            },
            onDismiss = { isBatchMoveDialogOpen = false }
        )
    }

    // Modal: Batch Delete Confirmation Dialog
    if (isBatchDeleteDialogOpen) {
        ConfirmDeleteDialog(
            onConfirm = {
                onEvent(LibraryEvent.BatchRemoveBookmarks(selectedItemIds))
                selectedItemIds = emptySet()
                isBatchDeleteDialogOpen = false
            },
            onDismiss = { isBatchDeleteDialogOpen = false },
            titleRes = Res.string.action_remove_from_bookmarks,
            message = "${stringResource(Res.string.delete)} (${selectedItemIds.size})",
            confirmTextRes = Res.string.delete
        )
    }
}

/**
 * Top Header bar with screen title, provider selector, quick search bar, and refresh button.
 */
@Composable
private fun LibraryTopHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    availableProviders: List<LibraryProvider>,
    selectedProviderId: String,
    onSelectProvider: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localQuery by remember(searchQuery) { mutableStateOf(searchQuery) }
    var isProviderDropdownOpen by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    LaunchedEffect(localQuery) {
        if (localQuery == searchQuery) return@LaunchedEffect
        delay(300L)
        onSearchQueryChange(localQuery)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Screen Title & Provider Dropdown Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.libraryTitle),
                style = MaterialTheme.typography.h5.copy(
                    fontWeight = FontWeight.Bold,
                    color = CloudStreamColors.TextPrimary
                )
            )

            // Provider Selector Dropdown (if multiple providers are available)
            if (availableProviders.size > 1) {
                val currentProvider = availableProviders.find { it.id == selectedProviderId }
                    ?: availableProviders.firstOrNull()

                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CloudStreamColors.SurfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isProviderDropdownOpen = true }
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val providerIcon = getProviderIcon(currentProvider?.id ?: "local")
                            Icon(
                                painter = painterResource(providerIcon),
                                contentDescription = currentProvider?.let { getProviderDisplayName(it) },
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(16.dp)
                            )

                            Text(
                                text = currentProvider?.let { getProviderDisplayName(it) } ?: "",
                                style = MaterialTheme.typography.body2.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = CloudStreamColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            )

                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isProviderDropdownOpen,
                        onDismissRequest = { isProviderDropdownOpen = false },
                        modifier = Modifier
                            .background(CloudStreamColors.SurfaceElevated)
                            .widthIn(min = 180.dp)
                    ) {
                        availableProviders.forEach { provider ->
                            val isSelected = provider.id == selectedProviderId
                            DropdownMenuItem(
                                onClick = {
                                    isProviderDropdownOpen = false
                                    onSelectProvider(provider.id)
                                },
                                modifier = Modifier.background(
                                    if (isSelected) CloudStreamColors.Primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(getProviderIcon(provider.id)),
                                        contentDescription = getProviderDisplayName(provider),
                                        tint = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Text(
                                        text = getProviderDisplayName(provider),
                                        style = MaterialTheme.typography.body2.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.TextPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = CloudStreamColors.Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Search Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.background,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search),
                    tint = if (localQuery.isNotBlank()) MaterialTheme.colors.primary else CloudStreamColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (localQuery.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.searchLibraryPlaceholder),
                            style = TextStyle(
                                color = CloudStreamColors.TextMuted.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        )
                    }

                    BasicTextField(
                        value = localQuery,
                        onValueChange = { localQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = CloudStreamColors.TextPrimary,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (localQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            localQuery = ""
                            onClearSearch()
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.clear),
                            tint = CloudStreamColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Refresh Button
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CloudStreamColors.SurfaceVariant)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(Res.string.reload_provider),
                tint = if (isRefreshing) CloudStreamColors.Primary else CloudStreamColors.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .then(if (isRefreshing) Modifier.rotate(rotation) else Modifier)
            )
        }
    }
}

/**
 * Filter and Sorting Chips bar for media types and ordering.
 */
@Composable
private fun LibraryFiltersBar(
    selectedType: TvType?,
    sortOrder: SortOrder,
    onSelectType: (TvType?) -> Unit,
    onSelectSortOrder: (SortOrder) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val typeOptions = listOf(
        null to stringResource(Res.string.all),
        TvType.Movie to stringResource(Res.string.typeMovie),
        TvType.TvSeries to stringResource(Res.string.typeTvSeries),
        TvType.Anime to stringResource(Res.string.typeAnime)
    )

    val sortLabel = when (sortOrder) {
        SortOrder.LAST_WATCHED -> stringResource(Res.string.sortLastWatched)
        SortOrder.RECENTLY_ADDED -> stringResource(Res.string.sortRecentlyAdded)
        SortOrder.ALPHABETICAL -> stringResource(Res.string.sortAlphabetical)
    }

    val sortOptions = listOf(
        SortOrder.LAST_WATCHED to stringResource(Res.string.sortLastWatched),
        SortOrder.RECENTLY_ADDED to stringResource(Res.string.sortRecentlyAdded),
        SortOrder.ALPHABETICAL to stringResource(Res.string.sortAlphabetical)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Media Type Chips
        typeOptions.forEach { (type, label) ->
            val isSelected = selectedType == type
            CloudStreamFilterChip(
                label = label,
                isSelected = isSelected,
                onClick = { onSelectType(type) }
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.15f))
        )

        // Sort Order Dropdown Chip
        CloudStreamDropdownFilter(
            label = sortLabel,
            items = sortOptions.map { it.first },
            selectedItem = sortOrder,
            onSelectItem = onSelectSortOrder,
            itemLabel = { order -> sortOptions.firstOrNull { it.first == order }?.second ?: order.name },
            leadingIcon = Icons.AutoMirrored.Filled.Sort,
            menuTitleRes = Res.string.sortBy,
            minMenuWidth = 180.dp,
            isFiltered = true
        )

        // Clear active filters chip if filtered
        if (selectedType != null) {
            CloudStreamActionChip(
                label = stringResource(Res.string.clear),
                icon = Icons.Default.Close,
                onClick = onClearFilters,
                containerColor = CloudStreamColors.Error.copy(alpha = 0.12f),
                contentColor = CloudStreamColors.Error
            )
        }
    }
}

/**
 * Scrollable Tab Bar with dynamic tabs or WatchStatus categories and item count pills.
 */
@Composable
private fun LibraryTabBar(
    tabs: List<LibraryTab>,
    selectedTabIndex: Int,
    selectedTab: WatchStatus,
    tabCounts: Map<WatchStatus, Int>,
    isLocal: Boolean,
    onSelectTabIndex: (Int) -> Unit,
    onSelectTab: (WatchStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colors.surface,
        elevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tabs.isNotEmpty()) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedTabIndex
                    val title = getTabLabel(tab, isLocal)
                    val accentColor = getTabAccentColor(tab.name, index)

                    LibraryTabItem(
                        title = title,
                        count = tab.count,
                        isSelected = isSelected,
                        accentColor = accentColor,
                        onClick = { onSelectTabIndex(index) }
                    )
                }
            } else {
                // Fallback for default local WatchStatus tabs
                val defaultStatusTabs = listOf(
                    WatchStatus.ALL to stringResource(Res.string.tabAll),
                    WatchStatus.WATCHING to stringResource(Res.string.statusWatching),
                    WatchStatus.COMPLETED to stringResource(Res.string.statusCompleted),
                    WatchStatus.PLANNED to stringResource(Res.string.statusPlanToWatch),
                    WatchStatus.ON_HOLD to stringResource(Res.string.statusOnHold),
                    WatchStatus.DROPPED to stringResource(Res.string.statusDropped)
                )

                defaultStatusTabs.forEach { (status, title) ->
                    val isSelected = selectedTab == status
                    val count = tabCounts[status] ?: 0
                    val tabColor = when (status) {
                        WatchStatus.ALL -> MaterialTheme.colors.primary
                        WatchStatus.WATCHING -> CloudStreamColors.Secondary
                        WatchStatus.COMPLETED -> CloudStreamColors.Success
                        WatchStatus.PLANNED -> CloudStreamColors.Primary
                        WatchStatus.ON_HOLD -> CloudStreamColors.Warning
                        WatchStatus.DROPPED -> CloudStreamColors.Error
                    }

                    LibraryTabItem(
                        title = title,
                        count = count,
                        isSelected = isSelected,
                        accentColor = tabColor,
                        onClick = { onSelectTab(status) }
                    )
                }
            }
        }
    }
}

/**
 * Individual Tab item with animated active pill and item count badge.
 */
@Composable
private fun LibraryTabItem(
    title: String,
    count: Int,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isSelected || isFocused

    val backgroundColor by animateColorAsState(
        targetValue = if (isHighlighted) accentColor.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(180)
    )

    val textColor by animateColorAsState(
        targetValue = if (isHighlighted) accentColor else CloudStreamColors.TextSecondary,
        animationSpec = tween(180)
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = if (isHighlighted) BorderStroke(1.5.dp, accentColor.copy(alpha = 0.8f)) else null,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )

            // Item count pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isHighlighted) accentColor.copy(alpha = 0.25f) else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "$count",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted) accentColor else CloudStreamColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Adaptive Responsive Grid presenting the library items.
 */
@Composable
private fun LibraryGrid(
    items: List<UnifiedLibraryItem>,
    selectedItemIds: Set<Int>,
    isSelectionMode: Boolean,
    isLocal: Boolean,
    onItemClick: (UnifiedLibraryItem) -> Unit,
    onItemLongClick: (UnifiedLibraryItem) -> Unit,
    onRemoveItem: (UnifiedLibraryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        items(
            items = items,
            key = { item -> "${item.id}_${item.url}" }
        ) { item ->
            val intId = item.id.toIntOrNull()
            val isSelected = intId != null && selectedItemIds.contains(intId)

            LibraryCard(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                isLocal = isLocal,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                onRemove = { onRemoveItem(item) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Media Card for Library items displaying poster, badges, watch status badge,
 * episode progress (episodesText), watch progress bar, title, year, and hover/context actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: UnifiedLibraryItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isLocal: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHighlighted = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isHighlighted -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    val elevation = if (isHighlighted) 12.dp else 2.dp
    val borderColor = when {
        isSelected -> CloudStreamColors.Primary
        isHighlighted -> CloudStreamColors.Primary.copy(alpha = 0.85f)
        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
    }

    val localItem = item.originalItem as? LibraryItem
    val searchResponse = localItem?.toSearchResponse()

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            onClick()
                            true
                        }
                        Key.Menu -> {
                            onLongClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Poster Card Surface with 2:3 Cinematic Aspect Ratio
        Card(
            shape = RoundedCornerShape(12.dp),
            backgroundColor = CloudStreamColors.Surface,
            elevation = elevation,
            border = BorderStroke(if (isSelected) 2.dp else if (isHovered) 1.5.dp else 0.5.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster Image
                AsyncImage(
                    url = item.posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Gradient for Badge Legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CloudStreamColors.Background.copy(alpha = 0.75f),
                                    CloudStreamColors.Background.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Badges Row (Top)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Quality & Type & Episode Progress Badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (localItem?.quality != null) {
                            QualityBadge(quality = localItem.quality)
                        }
                        TypeBadge(type = item.type)

                        if (item.episodesText != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CloudStreamColors.Background.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = item.episodesText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.Secondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Right: Selection Checkbox Badge or Favorite Icon or Score or Dub/Sub
                    if (isSelectionMode) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.Background.copy(alpha = 0.6f),
                            border = if (!isSelected) BorderStroke(1.5.dp, MaterialTheme.colors.onPrimary.copy(alpha = 0.8f)) else null,
                            modifier = Modifier.size(22.dp)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(Res.string.select_all),
                                        tint = MaterialTheme.colors.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else if (item.isFavorite) {
                        Surface(
                            shape = CircleShape,
                            color = CloudStreamColors.Error.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = stringResource(Res.string.action_add_to_favorites),
                                    tint = MaterialTheme.colors.onError,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else if (searchResponse != null) {
                        DubSubBadges(searchResponse = searchResponse)
                    } else if (item.score != null && item.score > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CloudStreamColors.Background.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = CloudStreamColors.Warning,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = formatScore(item.score),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                // Bottom Overlay: Watch Status Pill & Progress Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    // Status Badge & Progress Percentage Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, CloudStreamColors.Background.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Watch Status Pill (if local item has non-ALL status)
                            if (localItem != null && localItem.watchStatus != WatchStatus.ALL) {
                                WatchStatusBadge(status = localItem.watchStatus)
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            // Progress Percentage if available
                            if (item.progressPercentage > 0.02f) {
                                val percent = (item.progressPercentage * 100).toInt()
                                Text(
                                    text = "$percent%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isWatched) CloudStreamColors.Success else CloudStreamColors.Secondary
                                )
                            }
                        }
                    }

                    // Linear Watch Progress Bar
                    if (item.progressPercentage > 0.02f) {
                        LinearProgressIndicator(
                            progress = item.progressPercentage,
                            color = if (item.isWatched) CloudStreamColors.Success else CloudStreamColors.Secondary,
                            backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                        )
                    }
                }

                // Hover Actions Overlay (Desktop / TV focus: Delete action for local items)
                if (isLocal) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CloudStreamColors.Background.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colors.error.copy(alpha = 0.9f),
                                elevation = 6.dp,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(onClick = onRemove)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(Res.string.delete),
                                        tint = MaterialTheme.colors.onError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.name,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (isHovered) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isHovered) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
                fontSize = 13.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )

        // Metadata: Year, Episode progress or Provider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.year != null && item.year > 0) {
                Text(
                    text = item.year.toString(),
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        color = CloudStreamColors.TextMuted,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            } else if (item.episodesText != null) {
                Text(
                    text = item.episodesText,
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        color = CloudStreamColors.Secondary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }

            if (item.apiName.isNotBlank()) {
                Text(
                    text = item.apiName,
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        color = CloudStreamColors.TextMuted.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp)
                )
            }
        }
    }
}

/**
 * Watch status badge pill rendered over poster cards.
 */
@Composable
private fun WatchStatusBadge(
    status: WatchStatus,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (status) {
        WatchStatus.ALL -> stringResource(Res.string.tabAll) to CloudStreamColors.TextMuted
        WatchStatus.WATCHING -> stringResource(Res.string.statusWatching) to CloudStreamColors.Secondary
        WatchStatus.COMPLETED -> stringResource(Res.string.statusCompleted) to CloudStreamColors.Success
        WatchStatus.PLANNED -> stringResource(Res.string.statusPlanToWatch) to CloudStreamColors.Primary
        WatchStatus.ON_HOLD -> stringResource(Res.string.statusOnHold) to CloudStreamColors.Warning
        WatchStatus.DROPPED -> stringResource(Res.string.statusDropped) to CloudStreamColors.Error
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

/**
 * Stylized Empty State when the Library has zero total bookmarks using centralized [CloudStreamEmptyState].
 */
@Composable
private fun LibraryTotalEmptyView(
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    CloudStreamEmptyState(
        iconPainter = painterResource(Res.drawable.ic_baseline_bookmark_border_24),
        iconContainerSize = 88.dp,
        iconSize = 42.dp,
        titleRes = Res.string.libraryEmptyTitle,
        subtitleRes = Res.string.libraryEmptyDesc,
        actionTextRes = Res.string.exploreCatalog,
        actionIcon = Icons.Default.Explore,
        onActionClick = onExplore,
        modifier = modifier
    )
}

/**
 * Filtered Empty State when search/filter returns no items using centralized [CloudStreamEmptyState].
 */
@Composable
private fun LibraryFilteredEmptyView(
    searchQuery: String,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val noLibraryMatchesText = stringResource(Res.string.noLibraryMatches)

    CloudStreamEmptyState(
        icon = Icons.Default.FilterList,
        iconTint = CloudStreamColors.TextMuted,
        iconBackgroundColor = CloudStreamColors.SurfaceVariant,
        iconBorderColor = null,
        iconContainerSize = 72.dp,
        iconSize = 36.dp,
        title = if (searchQuery.isNotBlank()) "$noLibraryMatchesText (\"$searchQuery\")" else noLibraryMatchesText,
        subtitleRes = Res.string.noLibraryMatchesDesc,
        actionButtonContent = {
            SecondaryButton(
                textRes = Res.string.clear,
                icon = Icons.Default.Close,
                onClick = onClearFilters
            )
        },
        modifier = modifier
    )
}

/**
 * Top action bar displayed when one or more library items are selected for batch operations.
 */
@Composable
private fun LibraryBatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenMoveDialog: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CloudStreamColors.SurfaceElevated,
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onClearSelection, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cancel),
                        tint = CloudStreamColors.TextPrimary
                    )
                }
                Text(
                    text = "$selectedCount / $totalCount",
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Select All Button
                IconButton(onClick = onSelectAll, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.select_all),
                        tint = CloudStreamColors.Primary
                    )
                }

                // Move to Category Button
                IconButton(onClick = onOpenMoveDialog, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_baseline_bookmark_24),
                        contentDescription = stringResource(Res.string.action_add_to_bookmarks),
                        tint = CloudStreamColors.Secondary
                    )
                }

                // Delete Button
                IconButton(onClick = onOpenDeleteDialog, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.action_remove_from_bookmarks),
                        tint = CloudStreamColors.Error
                    )
                }
            }
        }
    }
}

/**
 * Modal dialog to batch move selected library items to a different watch status category using ActionDialog.
 */
@Composable
private fun LibraryBatchMoveDialog(
    onSelectStatus: (WatchStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val statuses = listOf(
        WatchStatus.WATCHING to stringResource(Res.string.statusWatching),
        WatchStatus.COMPLETED to stringResource(Res.string.statusCompleted),
        WatchStatus.PLANNED to stringResource(Res.string.statusPlanToWatch),
        WatchStatus.ON_HOLD to stringResource(Res.string.statusOnHold),
        WatchStatus.DROPPED to stringResource(Res.string.statusDropped)
    )

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.action_add_to_bookmarks,
        cancelTextRes = Res.string.cancel,
        onCancel = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { (status, label) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CloudStreamColors.SurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStatus(status) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.body1.copy(
                                fontWeight = FontWeight.Medium,
                                color = CloudStreamColors.TextPrimary
                            ),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    )
}

// -----------------------------------------------------------------------------
// Helper UI Formatters and Provider / Tab Resolvers
// -----------------------------------------------------------------------------

@Composable
private fun getProviderDisplayName(provider: LibraryProvider): String {
    return when (provider.id.lowercase()) {
        "local" -> stringResource(Res.string.error_bookmarks_text)
        "anilist" -> stringResource(Res.string.sync_anilist)
        "mal" -> stringResource(Res.string.sync_mal)
        "simkl" -> stringResource(Res.string.sync_simkl)
        "kitsu" -> stringResource(Res.string.sync_kitsu)
        else -> provider.name
    }
}

private fun getProviderIcon(providerId: String): DrawableResource {
    return when (providerId.lowercase()) {
        "anilist" -> Res.drawable.ic_anilist_icon
        "mal" -> Res.drawable.mal_logo
        "simkl" -> Res.drawable.simkl_logo
        "kitsu" -> Res.drawable.kitsu_icon
        else -> Res.drawable.ic_baseline_bookmark_24
    }
}

private fun getTabStringRes(name: String): StringResource? {
    return when (name.lowercase().replace(" ", "").replace("-", "")) {
        "all" -> Res.string.tabAll
        "watching", "currentlywatching" -> Res.string.statusWatching
        "completed", "finished" -> Res.string.statusCompleted
        "plantowatch", "planning" -> Res.string.statusPlanToWatch
        "onhold" -> Res.string.statusOnHold
        "dropped" -> Res.string.statusDropped
        else -> null
    }
}

@Composable
private fun getTabLabel(tab: LibraryTab, isLocal: Boolean): String {
    val res = getTabStringRes(tab.name)
    return if (res != null) stringResource(res) else tab.name
}

@Composable
private fun getTabAccentColor(tabName: String, index: Int): Color {
    return when (tabName.lowercase().replace(" ", "").replace("-", "")) {
        "watching", "currentlywatching" -> CloudStreamColors.Secondary
        "completed", "finished" -> CloudStreamColors.Success
        "plantowatch", "planning" -> CloudStreamColors.Primary
        "onhold" -> CloudStreamColors.Warning
        "dropped" -> CloudStreamColors.Error
        else -> when (index % 4) {
            0 -> MaterialTheme.colors.primary
            1 -> CloudStreamColors.Secondary
            2 -> CloudStreamColors.Success
            else -> CloudStreamColors.Primary
        }
    }
}

private fun formatScore(score: Double): String {
    return if (score % 1.0 == 0.0) {
        score.toInt().toString()
    } else {
        ((score * 10).toInt() / 10.0).toString()
    }
}
