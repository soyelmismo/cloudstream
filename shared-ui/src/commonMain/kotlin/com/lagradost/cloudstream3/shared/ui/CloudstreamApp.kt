package com.lagradost.cloudstream3.shared.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.SubscriptionRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepositoryImpl
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayer
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayerContent
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.ui.components.AppBottomNavigation

import com.lagradost.cloudstream3.shared.ui.downloads.DownloadsScreen
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadsViewModel
import com.lagradost.cloudstream3.shared.ui.home.HomeScreen
import com.lagradost.cloudstream3.shared.ui.components.ProvideAppLocale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*


import com.lagradost.cloudstream3.shared.ui.library.LibraryScreen
import com.lagradost.cloudstream3.shared.ui.player.PlayerControlsOverlay
import com.lagradost.cloudstream3.shared.ui.plugins.PluginDetailsScreen
import com.lagradost.cloudstream3.shared.ui.plugins.PluginsScreen
import com.lagradost.cloudstream3.shared.ui.result.ResultScreen
import com.lagradost.cloudstream3.shared.ui.search.SearchScreen
import com.lagradost.cloudstream3.shared.ui.settings.SettingsScreen
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme
import com.lagradost.cloudstream3.shared.ui.theme.rememberNativeSystemTheme
import com.lagradost.cloudstream3.shared.viewmodels.HomeViewModel
import com.lagradost.cloudstream3.shared.viewmodels.SearchEvent
import com.lagradost.cloudstream3.shared.viewmodels.SearchViewModel
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryViewModel
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerControllerViewModel
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEffect
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEffect
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultViewModel
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.ProviderRepositoryImpl
import com.lagradost.cloudstream3.shared.ui.account.AccountSelectScreen
import com.lagradost.cloudstream3.shared.ui.onboarding.OnboardingScreen
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.OnboardingViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.DefaultPluginsRepository
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Root Compose Multiplatform entry point for CloudStream.
 *
 * Provides:
 * - Persistent Room database repositories injection.
 * - Reactive cross-platform VideoPlayer integration.
 * - Responsive desktop navigation rail / mobile bottom navigation.
 * - Centralized backstack navigation routing.
 *
 * @param database The initialized Room [AppDatabase] instance.
 * @param player The platform-specific [VideoPlayer] implementation (e.g. DesktopVideoPlayer / AndroidVideoPlayer).
 * @param pluginLoader Optional platform [PluginLoader] for dynamic extension management.
 * @param videoPlayerContent Platform-specific surface composable to render the video canvas.
 * @param modifier Optional root modifier.
 */
@Composable
fun CloudstreamApp(
    database: AppDatabase,
    player: VideoPlayer,
    pluginLoader: PluginLoader? = null,
    onRegisterBackHandler: (((() -> Boolean) -> Unit))? = null,
    onPlayerStateChanged: ((isActive: Boolean) -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null,
    videoPlayerContent: @Composable (VideoPlayer, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    // -------------------------------------------------------------------------
    // Repositories
    // -------------------------------------------------------------------------
    val preferenceRepository = remember(database) {
        AppPreferenceRepositoryImpl(database.appPreferenceDao())
    }
    val bookmarkRepository = remember(database) {
        BookmarkRepositoryImpl(database.bookmarkDao())
    }
    val watchProgressRepository = remember(database) {
        WatchProgressRepositoryImpl(database.watchProgressDao())
    }
    val favoriteRepository = remember(database) {
        FavoriteRepositoryImpl(database.favoriteDao())
    }
    val resumeWatchingRepository = remember(database) {
        ResumeWatchingRepositoryImpl(database.resumeWatchingDao())
    }
    val subscriptionRepository = remember(database) {
        SubscriptionRepositoryImpl(database.subscriptionDao())
    }
    val accountRepository = remember(database) {
        AccountRepositoryImpl(database.accountDao())
    }
    val providerRepository = remember {
        ProviderRepositoryImpl()
    }

    // -------------------------------------------------------------------------
    // ViewModels
    // -------------------------------------------------------------------------
    val homeViewModelLazy = remember(providerRepository, bookmarkRepository, watchProgressRepository, resumeWatchingRepository, preferenceRepository) {
        lazy {
            HomeViewModel(
                providerRepository = providerRepository,
                bookmarkRepository = bookmarkRepository,
                watchProgressRepository = watchProgressRepository,
                resumeWatchingRepository = resumeWatchingRepository,
                preferenceRepository = preferenceRepository
            )
        }
    }
    val searchViewModelLazy = remember { lazy { SearchViewModel() } }
    val libraryViewModelLazy = remember(bookmarkRepository, watchProgressRepository, favoriteRepository) {
        lazy {
            LibraryViewModel(
            bookmarkRepository = bookmarkRepository,
            watchProgressRepository = watchProgressRepository,
            favoriteRepository = favoriteRepository
        )
        }
    }
    val appSettingsViewModel = remember(preferenceRepository) {
        AppSettingsViewModel(preferenceRepository = preferenceRepository)
    }
    val pluginsViewModelLazy = remember(preferenceRepository, pluginLoader) {
        lazy {
            PluginsSettingsViewModel(
                preferenceRepository = preferenceRepository,
                pluginLoader = pluginLoader,
                onPluginLoaded = {
                    homeViewModelLazy.value.initializeProviders()
                    searchViewModelLazy.value.initialize()
                    searchViewModelLazy.value.handleEvent(SearchEvent.ClearSearch)
                }
            )
        }
    }
    val playerControllerViewModel = remember(player, watchProgressRepository, resumeWatchingRepository, bookmarkRepository) {
        PlayerControllerViewModel(
            player = player,
            watchProgressRepository = watchProgressRepository,
            resumeWatchingRepository = resumeWatchingRepository,
            bookmarkRepository = bookmarkRepository
        )
    }
    val downloadsViewModelLazy = remember(database) {
        lazy {
            DownloadsViewModel(downloadCacheDao = database.downloadCacheDao())
        }
    }
    val accountViewModelLazy = remember(accountRepository, preferenceRepository) {
        lazy {
            AccountViewModel(
            accountRepository = accountRepository,
            preferenceRepository = preferenceRepository
        )
        }
    }
    val onboardingViewModelLazy = remember(preferenceRepository, accountRepository) {
        lazy {
            OnboardingViewModel(
            preferenceRepository = preferenceRepository,
            accountRepository = accountRepository,
            pluginsRepository = DefaultPluginsRepository(preferenceRepository)
        )
        }
    }

    val appSettingsState by appSettingsViewModel.state.collectAsState()
    

    // -------------------------------------------------------------------------
    // Navigation Stack
    // -------------------------------------------------------------------------
    val hasCompletedOnboarding = remember(preferenceRepository) {
        kotlinx.coroutines.runBlocking {
            preferenceRepository.getString(OnboardingViewModel.KEY_HAS_COMPLETED_ONBOARDING)?.toBooleanStrictOrNull() ?: false
        }
    }
    var backstack by remember {
        mutableStateOf(
            if (hasCompletedOnboarding) listOf<Screen>(Screen.Home) else listOf<Screen>(Screen.Onboarding)
        )
    }
    val currentScreen = backstack.lastOrNull() ?: Screen.Home
    val isPlayerActive = currentScreen is Screen.Player

    LaunchedEffect(isPlayerActive) {
        onPlayerStateChanged?.invoke(isPlayerActive)
    }

    // Retain active media details ViewModel across navigation transitions (e.g. from Details to Player)
    val activeDetailsScreen = backstack.filterIsInstance<Screen.Details>().lastOrNull()
    val activeResultViewModel = remember(activeDetailsScreen?.url, activeDetailsScreen?.apiName) {
        activeDetailsScreen?.let { details ->
            ResultViewModel(
                bookmarkRepository = bookmarkRepository,
                watchProgressRepository = watchProgressRepository,
                favoriteRepository = favoriteRepository,
                resumeWatchingRepository = resumeWatchingRepository,
                subscriptionRepository = subscriptionRepository
            ).apply {
                onEvent(ResultEvent.LoadResult(url = details.url, apiName = details.apiName, autoResume = details.autoResume))
            }
        }
    }

    val activeResultState = activeResultViewModel?.state?.collectAsState()?.value

    fun navigateTo(screen: Screen) {
        println("CloudStreamDebug: navigateTo -> $screen | backstack before=${backstack.map { it::class.simpleName }}")
        if (screen == Screen.Home) {
            backstack = listOf(Screen.Home)
        } else if (screen == Screen.Search || screen == Screen.Library || screen == Screen.Downloads || screen == Screen.Settings || screen == Screen.AccountSelect) {
            // Keep Home as root and replace section
            backstack = listOf(Screen.Home, screen)
        } else {
            backstack = backstack + screen
        }
    }

    fun navigateBack(): Boolean {
        println("CloudStreamDebug: navigateBack called! backstack before=${backstack.map { it::class.simpleName }}")
        if (backstack.size > 1) {
            backstack = backstack.dropLast(1)
            return true
        }
        return false
    }

    SideEffect {
        onRegisterBackHandler?.invoke { navigateBack() }
    }

    val clipboardManager = LocalClipboardManager.current
    val defaultAppTitle = stringResource(Res.string.app_name)
    var pendingPlayEpisode by remember { mutableStateOf<ResultEpisode?>(null) }

    LaunchedEffect(activeResultViewModel) {
        val vm = activeResultViewModel ?: return@LaunchedEffect
        vm.effects.collect { effect ->
            println("CloudStreamDebug: ResultEffect received: $effect")
            when (effect) {
                is ResultEffect.AutoPlayEpisode -> {
                    vm.onEvent(ResultEvent.SelectEpisode(effect.episode))
                    if (appSettingsViewModel.state.value.showSourcesOnPlay) {
                        pendingPlayEpisode = null
                    } else {
                        val currentLinks = activeResultState?.extractedLinks ?: emptyList()
                        val currentSubs = activeResultState?.extractedSubtitles ?: emptyList()
                        if (currentLinks.isNotEmpty()) {
                            pendingPlayEpisode = null
                            val bestLink = currentLinks.first()
                            val qualities = currentLinks.map { PlayerQuality.fromExtractorLink(it) }
                            val playerSubtitleTracks = currentSubs.map { PlayerSubtitleTrack.fromSubtitleFile(it) }
                            playerControllerViewModel.handleEvent(
                                PlayerUiEvent.LoadMedia(
                                    url = bestLink.url,
                                    mediaId = effect.episode.id,
                                    parentId = effect.parentId,
                                    qualities = qualities,
                                    subtitles = playerSubtitleTracks,
                                    autoPlay = true,
                                    resumePosition = effect.resumePosition
                                )
                            )
                            navigateTo(
                                Screen.Player(
                                    title = effect.episode.name ?: activeResultState?.title ?: defaultAppTitle,
                                    url = bestLink.url,
                                    subtitles = currentSubs,
                                    availableLinks = currentLinks
                                )
                            )
                        } else {
                            pendingPlayEpisode = effect.episode
                        }
                    }
                }
                is ResultEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                }
                is ResultEffect.ShowToast -> {}
            }
        }
    }

    LaunchedEffect(activeResultState?.extractedLinks, activeResultState?.extractedSubtitles, activeResultState?.linksLoadingError) {
        if (activeResultState?.linksLoadingError != null && pendingPlayEpisode != null) {
            pendingPlayEpisode = null
        }
        val links = activeResultState?.extractedLinks ?: emptyList()
        val subs = activeResultState?.extractedSubtitles ?: emptyList()
        println("CloudStreamDebug: extractedLinks changed: size=${links.size}, subs=${subs.size}, error=${activeResultState?.linksLoadingError}, pendingPlayEpisode=${pendingPlayEpisode?.name}")
        if (links.isNotEmpty()) {
            val qualities = links.map { PlayerQuality.fromExtractorLink(it) }
            val subTracks = subs.map { PlayerSubtitleTrack.fromSubtitleFile(it) }
            val pendingEp = pendingPlayEpisode

            if (pendingEp != null) {
                pendingPlayEpisode = null
                val bestLink = links.first()
                val currentEp = activeResultState?.selectedEpisode ?: pendingEp
                playerControllerViewModel.handleEvent(
                    PlayerUiEvent.LoadMedia(
                        url = bestLink.url,
                        mediaId = currentEp.id,
                        parentId = activeResultState?.mediaId ?: currentEp.parentId,
                        qualities = qualities,
                        subtitles = subTracks,
                        autoPlay = true,
                        resumePosition = currentEp.position.takeIf { it > 0 }
                    )
                )
                navigateTo(
                    Screen.Player(
                        title = currentEp.name ?: activeResultState?.title ?: defaultAppTitle,
                        url = bestLink.url,
                        subtitles = subs,
                        availableLinks = links
                    )
                )
            } else {
                // Only update secondary qualities/subtitles if player is already loaded
                playerControllerViewModel.handleEvent(
                    PlayerUiEvent.UpdateQualitiesAndSubtitles(
                        qualities = qualities,
                        subtitles = subTracks
                    )
                )
            }
        }
    }

    LaunchedEffect(playerControllerViewModel) {
        playerControllerViewModel.effects.collect { effect ->
            println("CloudStreamDebug: playerControllerViewModel effect received: $effect")
            when (effect) {
                is PlayerUiEffect.NavigateBack -> {
                    playerControllerViewModel.handleEvent(PlayerUiEvent.Pause)
                    navigateBack()
                }
                else -> {}
            }
        }
    }

    val currentLanguage = appSettingsState.appLanguage

    CompositionLocalProvider(
        LocalVideoPlayer provides player,
        LocalVideoPlayerContent provides videoPlayerContent
    ) {
        ProvideAppLocale(languageCode = currentLanguage) {
            val nativeTheme = rememberNativeSystemTheme()
            val effectiveDarkMode = if (appSettingsState.theme == AppTheme.SYSTEM) {
                nativeTheme.isDarkMode ?: appSettingsState.isDarkMode
            } else {
                appSettingsState.isDarkMode
            }
            CloudStreamTheme(
                theme = appSettingsState.theme,
                isDarkMode = effectiveDarkMode,
                systemAccentColor = nativeTheme.accentColor
            ) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = Color.Black
            ) {
                // Determine the high-level layout mode to avoid duplicate Scaffold recompositions
                val layoutMode = remember(currentScreen) {
                    when (currentScreen) {
                        is Screen.Player -> 0
                        is Screen.Onboarding -> 1
                        is Screen.AccountSelect -> 2
                        else -> 3
                    }
                }

                when (layoutMode) {
                    0 -> {
                        // -------------------------------------------------------------
                        // Immersive Fullscreen Video Player Mode (Hides All Navigation)
                        // -------------------------------------------------------------
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            videoPlayerContent(player, Modifier.fillMaxSize())
                            PlayerControlsOverlay(
                                viewModel = playerControllerViewModel,
                                onBackClick = {
                                    playerControllerViewModel.handleEvent(PlayerUiEvent.SaveProgressNow)
                                    playerControllerViewModel.handleEvent(PlayerUiEvent.Pause)
                                    navigateBack()
                                },
                                onToggleFullscreen = onToggleFullscreen,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    1 -> {
                        OnboardingScreen(
                            viewModel = onboardingViewModelLazy.value,
                            onComplete = { navigateTo(Screen.Home) },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                    2 -> {
                        AccountSelectScreen(
                            viewModel = accountViewModelLazy.value,
                            onProfileSelected = { navigateTo(Screen.Home) },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                    else -> {
                        // -------------------------------------------------------------
                        // Responsive Scaffold: Navigation Rail (>= 600dp) vs Bottom Navigation (< 600dp)
                        // -------------------------------------------------------------
                        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            val isExpanded = maxWidth >= 600.dp
                            val isMainTab = currentScreen is Screen.Home ||
                                    currentScreen is Screen.Search ||
                                    currentScreen is Screen.Library ||
                                    currentScreen is Screen.Downloads ||
                                    currentScreen is Screen.Settings

                            if (isExpanded) {
                                // Desktop / Tablet / Wide Landscape Layout: Left Navigation Rail
                                Row(modifier = Modifier.fillMaxSize()) {
                                    AppNavigationRail(
                                        currentScreen = currentScreen,
                                        canNavigateBack = backstack.size > 1,
                                        onNavigateBack = ::navigateBack,
                                        onNavigate = ::navigateTo,
                                        modifier = Modifier
                                            .width(88.dp)
                                            .fillMaxHeight()
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(CloudstreamTheme.extendedColors.divider)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .statusBarsPadding()
                                            .navigationBarsPadding()
                                    ) {
                                        AppContentRouter(
                                            currentScreen = currentScreen,
                                            homeViewModelLazy = homeViewModelLazy,
                                            searchViewModelLazy = searchViewModelLazy,
                                            libraryViewModelLazy = libraryViewModelLazy,
                                            downloadsViewModelLazy = downloadsViewModelLazy,
                                            appSettingsViewModel = appSettingsViewModel,
                                            pluginsViewModelLazy = pluginsViewModelLazy,
                                            accountViewModelLazy = accountViewModelLazy,
                                            onboardingViewModelLazy = onboardingViewModelLazy,
                                            playerControllerViewModel = playerControllerViewModel,
                                            bookmarkRepository = bookmarkRepository,
                                            watchProgressRepository = watchProgressRepository,
                                            favoriteRepository = favoriteRepository,
                                            resumeWatchingRepository = resumeWatchingRepository,
                                            subscriptionRepository = subscriptionRepository,
                                            activeResultViewModel = activeResultViewModel,
                                            activeResultState = activeResultState,
                                            navigateTo = ::navigateTo,
                                            navigateBack = ::navigateBack,
                                            onSetPendingPlayEpisode = { pendingPlayEpisode = it },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                // Mobile / Narrow Window (< 600dp): Content + Bottom Navigation Bar
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .then(if (!isMainTab) Modifier.navigationBarsPadding() else Modifier)
                                    ) {
                                        AppContentRouter(
                                            currentScreen = currentScreen,
                                            homeViewModelLazy = homeViewModelLazy,
                                            searchViewModelLazy = searchViewModelLazy,
                                            libraryViewModelLazy = libraryViewModelLazy,
                                            downloadsViewModelLazy = downloadsViewModelLazy,
                                            appSettingsViewModel = appSettingsViewModel,
                                            pluginsViewModelLazy = pluginsViewModelLazy,
                                            accountViewModelLazy = accountViewModelLazy,
                                            onboardingViewModelLazy = onboardingViewModelLazy,
                                            playerControllerViewModel = playerControllerViewModel,
                                            bookmarkRepository = bookmarkRepository,
                                            watchProgressRepository = watchProgressRepository,
                                            favoriteRepository = favoriteRepository,
                                            resumeWatchingRepository = resumeWatchingRepository,
                                            subscriptionRepository = subscriptionRepository,
                                            activeResultViewModel = activeResultViewModel,
                                            activeResultState = activeResultState,
                                            navigateTo = ::navigateTo,
                                            navigateBack = ::navigateBack,
                                            onSetPendingPlayEpisode = { pendingPlayEpisode = it },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    if (isMainTab) {
                                        AppBottomNavigation(
                                            currentScreen = currentScreen,
                                            onNavigate = ::navigateTo,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .navigationBarsPadding()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Shared screen router across desktop and mobile layouts.
 */
@Composable
private fun AppContentRouter(
    currentScreen: Screen,
    homeViewModelLazy: Lazy<HomeViewModel>,
    searchViewModelLazy: Lazy<SearchViewModel>,
    libraryViewModelLazy: Lazy<LibraryViewModel>,
    downloadsViewModelLazy: Lazy<DownloadsViewModel>,
    appSettingsViewModel: AppSettingsViewModel,
    pluginsViewModelLazy: Lazy<PluginsSettingsViewModel>,
    accountViewModelLazy: Lazy<AccountViewModel>,
    onboardingViewModelLazy: Lazy<OnboardingViewModel>,
    playerControllerViewModel: PlayerControllerViewModel,
    bookmarkRepository: BookmarkRepositoryImpl,
    watchProgressRepository: WatchProgressRepositoryImpl,
    favoriteRepository: FavoriteRepositoryImpl,
    resumeWatchingRepository: ResumeWatchingRepositoryImpl,
    subscriptionRepository: SubscriptionRepositoryImpl,
    activeResultViewModel: ResultViewModel?,
    activeResultState: ResultState?,
    navigateTo: (Screen) -> Unit,
    navigateBack: () -> Unit,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val defaultAppTitle = stringResource(Res.string.app_name)

    val isMainTab = currentScreen is Screen.Home ||
            currentScreen is Screen.Search ||
            currentScreen is Screen.Library ||
            currentScreen is Screen.Downloads ||
            currentScreen is Screen.Settings

    var visitedScreens by remember { mutableStateOf(setOf<Screen>(Screen.Home)) }

    LaunchedEffect(currentScreen) {
        if (isMainTab && !visitedScreens.contains(currentScreen)) {
            visitedScreens = visitedScreens + currentScreen
        }
    }

    val screenKey = remember(currentScreen) {
        when (currentScreen) {
            is Screen.Details -> "details_${currentScreen.url}_${currentScreen.apiName}"
            is Screen.PluginDetails -> "plugin_${currentScreen.plugin.internalName}"
            is Screen.Player -> "player"
            else -> currentScreen::class.simpleName ?: "screen"
        }
    }

    Box(modifier = modifier) {
        saveableStateHolder.SaveableStateProvider(key = screenKey) {
            when (currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = homeViewModelLazy.value,
                        onNavigateToDetails = { item, autoResume ->
                            navigateTo(Screen.Details(url = item.url, apiName = item.apiName, autoResume = autoResume))
                        },
                        onSearchClick = { navigateTo(Screen.Search) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Search -> {
                    SearchScreen(
                        viewModel = searchViewModelLazy.value,
                        onNavigateToDetails = { item ->
                            navigateTo(Screen.Details(url = item.url, apiName = item.apiName))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Library -> {
                    LibraryScreen(
                        viewModel = libraryViewModelLazy.value,
                        onNavigateToDetails = { url, apiName ->
                            navigateTo(Screen.Details(url = url, apiName = apiName))
                        },
                        onNavigateToHome = {
                            navigateTo(Screen.Home)
                        },
                        onSearchMedia = { query ->
                            searchViewModelLazy.value.handleEvent(SearchEvent.Search(query))
                            navigateTo(Screen.Search)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Downloads -> {
                    DownloadsScreen(
                        viewModel = downloadsViewModelLazy.value,
                        onNavigateToExplore = {
                            navigateTo(Screen.Home)
                        },
                        onPlayOffline = { episode, header ->
                            val offlineTitle = episode.name ?: header?.name ?: defaultAppTitle
                            val offlineUrl = header?.url?.takeIf { it.isNotBlank() } ?: "offline://${episode.parentId}/${episode.id}"
                            if (offlineUrl.isNotBlank()) {
                                playerControllerViewModel.handleEvent(
                                    PlayerUiEvent.LoadMedia(
                                        url = offlineUrl,
                                        mediaId = episode.id,
                                        parentId = episode.parentId,
                                        qualities = emptyList(),
                                        subtitles = emptyList(),
                                        autoPlay = true
                                    )
                                )
                                navigateTo(
                                    Screen.Player(
                                        title = offlineTitle,
                                        url = offlineUrl,
                                        subtitles = emptyList(),
                                        availableLinks = emptyList()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Plugins -> {
                    PluginsScreen(
                        viewModel = pluginsViewModelLazy.value,
                        onNavigateToPluginDetails = { plugin ->
                            navigateTo(Screen.PluginDetails(plugin))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        appSettingsViewModel = appSettingsViewModel,
                        pluginsViewModel = pluginsViewModelLazy.value,
                        onBackClick = { navigateBack() },
                        onNavigateToAccountSelect = { navigateTo(Screen.AccountSelect) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Details -> {
                    val detailsScreen = currentScreen
                    val appSettingsState by appSettingsViewModel.state.collectAsState()
                    val resultViewModel = activeResultViewModel ?: remember(detailsScreen.url, detailsScreen.apiName) {
                        ResultViewModel(
                            bookmarkRepository = bookmarkRepository,
                            watchProgressRepository = watchProgressRepository,
                            favoriteRepository = favoriteRepository,
                            resumeWatchingRepository = resumeWatchingRepository,
                            subscriptionRepository = subscriptionRepository
                        ).apply {
                            onEvent(ResultEvent.LoadResult(url = detailsScreen.url, apiName = detailsScreen.apiName, autoResume = detailsScreen.autoResume))
                        }
                    }

                    ResultScreen(
                        viewModel = resultViewModel,
                        showSourcesOnPlay = appSettingsState.showSourcesOnPlay,
                        onBack = { navigateBack() },
                        onPlayEpisode = { episode ->
                            println("CloudStreamDebug: onPlayEpisode invoked for episode id=${episode.id} name=${episode.name}")
                            resultViewModel.onEvent(ResultEvent.SelectEpisode(episode))
                            resultViewModel.onEvent(ResultEvent.ReloadLinks(episode))
                            onSetPendingPlayEpisode(episode)
                        },
                        onPlayLink = { selectedLink, allLinks, subs, initialSubtitle ->
                            println("CloudStreamDebug: onPlayLink invoked: url=${selectedLink.url} source=${selectedLink.source}")
                            val currentEp = activeResultState?.selectedEpisode
                            val playerSubtitleTracks = subs.map { PlayerSubtitleTrack.fromSubtitleFile(it) }
                            val initialSubtitleTrack = initialSubtitle?.let { PlayerSubtitleTrack.fromSubtitleFile(it) }
                            playerControllerViewModel.handleEvent(
                                PlayerUiEvent.LoadMedia(
                                    url = selectedLink.url,
                                    mediaId = currentEp?.id ?: activeResultState?.mediaId,
                                    parentId = activeResultState?.mediaId,
                                    qualities = allLinks.map { PlayerQuality.fromExtractorLink(it) },
                                    subtitles = playerSubtitleTracks,
                                    initialSubtitle = initialSubtitleTrack,
                                    autoPlay = true,
                                    resumePosition = currentEp?.position?.takeIf { it > 0 }
                                )
                            )
                            navigateTo(
                                Screen.Player(
                                    title = activeResultState?.title ?: defaultAppTitle,
                                    url = selectedLink.url,
                                    subtitles = subs,
                                    availableLinks = allLinks
                                )
                            )
                        },
                        onNavigateToRecommendation = { recUrl, recApi ->
                            navigateTo(Screen.Details(url = recUrl, apiName = recApi))
                        },
                        onDownloadEpisode = { episode ->
                            resultViewModel.onEvent(ResultEvent.SelectEpisode(episode))
                            resultViewModel.onEvent(ResultEvent.ReloadLinks(episode))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.PluginDetails -> {
                    val pluginScreen = currentScreen
                    PluginDetailsScreen(
                        plugin = pluginScreen.plugin,
                        viewModel = pluginsViewModelLazy.value,
                        onBackClick = { navigateBack() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Screen.Onboarding -> {
                    OnboardingScreen(
                        viewModel = onboardingViewModelLazy.value,
                        onComplete = { navigateTo(Screen.Home) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Screen.AccountSelect -> {
                    AccountSelectScreen(
                        viewModel = accountViewModelLazy.value,
                        onProfileSelected = { navigateTo(Screen.Home) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {}
            }
        }
    }
}

/**
 * Responsive Navigation Rail sidebar for Desktop and Tablet screens.
 */
@Composable
private fun AppNavigationRail(
    currentScreen: Screen,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Back Button / App Brand & Primary Navigation
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (canNavigateBack) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CloudStreamColors.SurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // App Brand Logo
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(Screen.Home) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.cloud_2_gradient),
                            contentDescription = stringResource(Res.string.app_name),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Items
            NavigationRailItem(
                icon = Icons.Default.Home,
                label = stringResource(Res.string.navHome),
                isSelected = currentScreen is Screen.Home,
                onClick = { onNavigate(Screen.Home) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NavigationRailItem(
                icon = Icons.Default.Search,
                label = stringResource(Res.string.navSearch),
                isSelected = currentScreen is Screen.Search,
                onClick = { onNavigate(Screen.Search) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NavigationRailItem(
                icon = Icons.Default.Bookmark,
                label = stringResource(Res.string.navLibrary),
                isSelected = currentScreen is Screen.Library,
                onClick = { onNavigate(Screen.Library) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NavigationRailItem(
                icon = Icons.Default.Download,
                label = stringResource(Res.string.navDownloads),
                isSelected = currentScreen is Screen.Downloads,
                onClick = { onNavigate(Screen.Downloads) }
            )
        }

        // Bottom Section: Settings
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            NavigationRailItem(
                icon = Icons.Default.Settings,
                label = stringResource(Res.string.navSettings),
                isSelected = currentScreen is Screen.Settings,
                onClick = { onNavigate(Screen.Settings) }
            )
        }
    }
}

/**
 * Individual Navigation Rail Item with animated state, hover elevation, and ripple feedback.
 */
@Composable
private fun NavigationRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.06f else 1.0f,
        animationSpec = tween(150)
    )

    val backgroundColor = when {
        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.16f)
        isHighlighted -> CloudstreamTheme.extendedColors.hoverBackground
        else -> Color.Transparent
    }

    val contentColor = when {
        isSelected -> MaterialTheme.colors.primary
        isHighlighted -> CloudstreamTheme.extendedColors.textPrimary
        else -> CloudstreamTheme.extendedColors.textSecondary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected || isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor
        )
    }
}
