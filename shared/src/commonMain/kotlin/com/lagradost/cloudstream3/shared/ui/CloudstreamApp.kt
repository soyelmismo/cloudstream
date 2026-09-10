package com.lagradost.cloudstream3.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.repository.AccountRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.ProviderRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.SubscriptionRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepositoryImpl
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayer
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayerContent
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.ui.account.AccountSelectScreen
import com.lagradost.cloudstream3.shared.ui.components.AppBottomNavigation
import com.lagradost.cloudstream3.shared.ui.components.AppNavigationRail
import com.lagradost.cloudstream3.shared.ui.components.ProvideAppLocale
import com.lagradost.cloudstream3.shared.ui.downloads.DownloadsScreen
import com.lagradost.cloudstream3.shared.ui.home.HomeScreen
import com.lagradost.cloudstream3.shared.ui.layout.Layout
import com.lagradost.cloudstream3.shared.ui.layout.LocalLayout
import com.lagradost.cloudstream3.shared.ui.layout.isLayoutState
import com.lagradost.cloudstream3.shared.ui.library.LibraryScreen
import com.lagradost.cloudstream3.shared.ui.onboarding.OnboardingScreen
import com.lagradost.cloudstream3.shared.ui.player.PlayerControlsOverlay
import com.lagradost.cloudstream3.shared.ui.plugins.PluginDetailsScreen
import com.lagradost.cloudstream3.shared.ui.plugins.PluginsScreen
import com.lagradost.cloudstream3.shared.ui.result.ResultScreen
import com.lagradost.cloudstream3.shared.ui.search.SearchScreen
import com.lagradost.cloudstream3.shared.ui.settings.SettingsScreen
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme
import com.lagradost.cloudstream3.shared.ui.theme.rememberNativeSystemTheme
import com.lagradost.cloudstream3.shared.viewmodels.HomeViewModel
import com.lagradost.cloudstream3.shared.viewmodels.SearchEvent
import com.lagradost.cloudstream3.shared.viewmodels.SearchViewModel
import com.lagradost.cloudstream3.shared.viewmodels.account.AccountViewModel
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.library.LibraryViewModel
import com.lagradost.cloudstream3.shared.viewmodels.onboarding.OnboardingViewModel
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
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsState
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppSettingsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.DefaultPluginsRepository
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Stable
class AppNavigationState(
    initialBackstack: ImmutableList<Screen> = persistentListOf(Screen.Home)
) {
    var backstack: ImmutableList<Screen> by mutableStateOf(initialBackstack)
        private set

    val currentScreen: Screen
        get() = backstack.lastOrNull() ?: Screen.Home

    val canNavigateBack: Boolean
        get() = backstack.size > 1

    fun navigateTo(screen: Screen) {
        backstack = when {
            screen == Screen.Home -> persistentListOf(Screen.Home)
            screen in mainTabScreens -> persistentListOf(Screen.Home, screen)
            else -> (backstack + screen).toImmutableList()
        }
    }

    fun navigateBack(): Boolean {
        if (backstack.size > 1) {
            backstack = backstack.dropLast(1).toImmutableList()
            return true
        }
        return false
    }

    companion object {
        private val mainTabScreens = setOf(
            Screen.Home,
            Screen.Search,
            Screen.Library,
            Screen.Downloads,
            Screen.Settings,
            Screen.AccountSelect
        )
    }
}

val Screen.isMainTab: Boolean
    get() = this is Screen.Home ||
            this is Screen.Search ||
            this is Screen.Library ||
            this is Screen.Downloads ||
            this is Screen.Settings

@Composable
fun rememberAppNavigationState(hasCompletedOnboarding: Boolean): AppNavigationState {
    val initialScreen = if (hasCompletedOnboarding) Screen.Home else Screen.Onboarding
    return remember { AppNavigationState(persistentListOf(initialScreen)) }
}

@Immutable
private class AppRepositories(
    val preferenceRepository: AppPreferenceRepositoryImpl,
    val bookmarkRepository: BookmarkRepositoryImpl,
    val watchProgressRepository: WatchProgressRepositoryImpl,
    val favoriteRepository: FavoriteRepositoryImpl,
    val resumeWatchingRepository: ResumeWatchingRepositoryImpl,
    val subscriptionRepository: SubscriptionRepositoryImpl,
    val accountRepository: AccountRepositoryImpl,
    val providerRepository: ProviderRepositoryImpl,
    val hasCompletedOnboarding: Boolean
)

@Composable
private fun rememberAppRepositories(database: AppDatabase): AppRepositories {
    val preferenceRepository = remember(database) {
        AppPreferenceRepositoryImpl(database.appPreferenceDao())
    }
    val tombstoneDao = remember(database) { database.syncTombstoneDao() }
    val bookmarkRepository = remember(database, tombstoneDao) {
        BookmarkRepositoryImpl(database.bookmarkDao(), tombstoneDao)
    }
    val watchProgressRepository = remember(database, tombstoneDao) {
        WatchProgressRepositoryImpl(database.watchProgressDao(), tombstoneDao)
    }
    val favoriteRepository = remember(database, tombstoneDao) {
        FavoriteRepositoryImpl(database.favoriteDao(), tombstoneDao)
    }
    val resumeWatchingRepository = remember(database) {
        ResumeWatchingRepositoryImpl(database.resumeWatchingDao())
    }
    val subscriptionRepository = remember(database, tombstoneDao) {
        SubscriptionRepositoryImpl(database.subscriptionDao(), tombstoneDao)
    }
    val accountRepository = remember(database) {
        AccountRepositoryImpl(database.accountDao())
    }
    val providerRepository = remember {
        ProviderRepositoryImpl()
    }
    val hasCompletedOnboarding = remember(preferenceRepository) {
        kotlinx.coroutines.runBlocking {
            preferenceRepository.getString(OnboardingViewModel.KEY_HAS_COMPLETED_ONBOARDING)?.toBooleanStrictOrNull() ?: false
        }
    }
    return remember(database, hasCompletedOnboarding) {
        AppRepositories(
            preferenceRepository = preferenceRepository,
            bookmarkRepository = bookmarkRepository,
            watchProgressRepository = watchProgressRepository,
            favoriteRepository = favoriteRepository,
            resumeWatchingRepository = resumeWatchingRepository,
            subscriptionRepository = subscriptionRepository,
            accountRepository = accountRepository,
            providerRepository = providerRepository,
            hasCompletedOnboarding = hasCompletedOnboarding
        )
    }
}

@Immutable
private class AppViewModels(
    val homeViewModelLazy: Lazy<HomeViewModel>,
    val searchViewModelLazy: Lazy<SearchViewModel>,
    val libraryViewModelLazy: Lazy<LibraryViewModel>,
    val appSettingsViewModel: AppSettingsViewModel,
    val pluginsViewModelLazy: Lazy<PluginsSettingsViewModel>,
    val playerControllerViewModel: PlayerControllerViewModel,
    val downloadsViewModelLazy: Lazy<DownloadsViewModel>,
    val accountViewModelLazy: Lazy<AccountViewModel>,
    val onboardingViewModelLazy: Lazy<OnboardingViewModel>
)

@Composable
private fun rememberAppViewModels(
    repos: AppRepositories,
    player: VideoPlayer,
    pluginLoader: PluginLoader?,
    database: AppDatabase
): AppViewModels {
    val homeViewModelLazy = remember(repos) {
        lazy {
            HomeViewModel(
                providerRepository = repos.providerRepository,
                bookmarkRepository = repos.bookmarkRepository,
                watchProgressRepository = repos.watchProgressRepository,
                resumeWatchingRepository = repos.resumeWatchingRepository,
                preferenceRepository = repos.preferenceRepository
            )
        }
    }
    val searchViewModelLazy = remember { lazy { SearchViewModel() } }
    val libraryViewModelLazy = remember(repos) {
        lazy {
            LibraryViewModel(
                bookmarkRepository = repos.bookmarkRepository,
                watchProgressRepository = repos.watchProgressRepository,
                favoriteRepository = repos.favoriteRepository
            )
        }
    }
    val appSettingsViewModel = remember(repos.preferenceRepository) {
        AppSettingsViewModel(preferenceRepository = repos.preferenceRepository)
    }
    val pluginsViewModelLazy = remember(repos.preferenceRepository, pluginLoader) {
        lazy {
            PluginsSettingsViewModel(
                preferenceRepository = repos.preferenceRepository,
                pluginLoader = pluginLoader,
                onPluginLoaded = {
                    homeViewModelLazy.value.initializeProviders()
                    searchViewModelLazy.value.initialize()
                    searchViewModelLazy.value.handleEvent(SearchEvent.ClearSearch)
                }
            )
        }
    }
    val playerControllerViewModel = remember(player, repos) {
        PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repos.watchProgressRepository,
            resumeWatchingRepository = repos.resumeWatchingRepository,
            bookmarkRepository = repos.bookmarkRepository
        )
    }
    val downloadsViewModelLazy = remember(database) {
        lazy {
            DownloadsViewModel(downloadCacheDao = database.downloadCacheDao())
        }
    }
    val accountViewModelLazy = remember(repos) {
        lazy {
            AccountViewModel(
                accountRepository = repos.accountRepository,
                preferenceRepository = repos.preferenceRepository
            )
        }
    }
    val onboardingViewModelLazy = remember(repos) {
        lazy {
            OnboardingViewModel(
                preferenceRepository = repos.preferenceRepository,
                accountRepository = repos.accountRepository,
                pluginsRepository = DefaultPluginsRepository(repos.preferenceRepository)
            )
        }
    }

    return remember(repos, player, pluginLoader, database) {
        AppViewModels(
            homeViewModelLazy = homeViewModelLazy,
            searchViewModelLazy = searchViewModelLazy,
            libraryViewModelLazy = libraryViewModelLazy,
            appSettingsViewModel = appSettingsViewModel,
            pluginsViewModelLazy = pluginsViewModelLazy,
            playerControllerViewModel = playerControllerViewModel,
            downloadsViewModelLazy = downloadsViewModelLazy,
            accountViewModelLazy = accountViewModelLazy,
            onboardingViewModelLazy = onboardingViewModelLazy
        )
    }
}

@Composable
private fun rememberActiveResultViewModel(
    activeDetailsScreen: Screen.Details?,
    repos: AppRepositories
): ResultViewModel? {
    return remember(activeDetailsScreen?.url, activeDetailsScreen?.apiName) {
        activeDetailsScreen?.let { details ->
            ResultViewModel(
                bookmarkRepository = repos.bookmarkRepository,
                watchProgressRepository = repos.watchProgressRepository,
                favoriteRepository = repos.favoriteRepository,
                resumeWatchingRepository = repos.resumeWatchingRepository,
                subscriptionRepository = repos.subscriptionRepository
            ).apply {
                onEvent(ResultEvent.LoadResult(url = details.url, apiName = details.apiName, autoResume = details.autoResume))
            }
        }
    }
}

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
    val repos = rememberAppRepositories(database)
    val viewModels = rememberAppViewModels(repos, player, pluginLoader, database)
    val appSettingsState by viewModels.appSettingsViewModel.state.collectAsState()

    val navState = rememberAppNavigationState(repos.hasCompletedOnboarding)
    val currentScreen = navState.currentScreen
    val isPlayerActive = currentScreen is Screen.Player

    LaunchedEffect(isPlayerActive) {
        onPlayerStateChanged?.invoke(isPlayerActive)
    }

    SideEffect {
        onRegisterBackHandler?.invoke { navState.navigateBack() }
    }

    val activeDetailsScreen = navState.backstack.filterIsInstance<Screen.Details>().lastOrNull()
    val activeResultViewModel = rememberActiveResultViewModel(activeDetailsScreen, repos)
    val activeResultState = activeResultViewModel?.state?.collectAsState()?.value

    var pendingPlayEpisode by remember { mutableStateOf<ResultEpisode?>(null) }
    val defaultAppTitle = stringResource(Res.string.app_name)

    ResultPlaybackCoordinator(
        activeResultViewModel = activeResultViewModel,
        activeResultState = activeResultState,
        playerControllerViewModel = viewModels.playerControllerViewModel,
        showSourcesOnPlay = appSettingsState.showSourcesOnPlay,
        defaultAppTitle = defaultAppTitle,
        pendingPlayEpisode = pendingPlayEpisode,
        onSetPendingPlayEpisode = { pendingPlayEpisode = it },
        onNavigateToPlayer = { navState.navigateTo(it) }
    )

    PlayerEffectsHandler(
        playerControllerViewModel = viewModels.playerControllerViewModel,
        onNavigateBack = { navState.navigateBack() }
    )

    AppThemeWrapper(
        appSettingsState = appSettingsState,
        player = player,
        videoPlayerContent = videoPlayerContent
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val parentLayout = LocalLayout.current
            val deviceLayout = remember(maxWidth, parentLayout) {
                if (parentLayout != Layout.PHONE && parentLayout != Layout.NONE) {
                    parentLayout
                } else if (maxWidth >= 720.dp) {
                    Layout.COMPUTER or Layout.DESKTOP
                } else {
                    Layout.PHONE
                }
            }

            CompositionLocalProvider(LocalLayout provides deviceLayout) {
                AppLayoutShell(
                    currentScreen = currentScreen,
                    canNavigateBack = navState.canNavigateBack,
                    onNavigateBack = { navState.navigateBack() },
                    onNavigateTo = { navState.navigateTo(it) },
                    onToggleFullscreen = onToggleFullscreen,
                    player = player,
                    videoPlayerContent = videoPlayerContent,
                    playerControllerViewModel = viewModels.playerControllerViewModel,
                    onboardingViewModel = viewModels.onboardingViewModelLazy.value,
                    accountViewModel = viewModels.accountViewModelLazy.value,
                    contentRouter = { routerModifier ->
                        AppContentRouter(
                            currentScreen = currentScreen,
                            viewModels = viewModels,
                            repos = repos,
                            activeResultViewModel = activeResultViewModel,
                            activeResultState = activeResultState,
                            navigateTo = { navState.navigateTo(it) },
                            navigateBack = { navState.navigateBack() },
                            onSetPendingPlayEpisode = { pendingPlayEpisode = it },
                            modifier = routerModifier
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AppThemeWrapper(
    appSettingsState: AppSettingsState,
    player: VideoPlayer,
    videoPlayerContent: @Composable (VideoPlayer, Modifier) -> Unit,
    content: @Composable () -> Unit
) {
    val nativeTheme = rememberNativeSystemTheme()
    val effectiveDarkMode = if (appSettingsState.theme == AppTheme.SYSTEM) {
        nativeTheme.isDarkMode ?: appSettingsState.isDarkMode
    } else {
        appSettingsState.isDarkMode
    }

    CompositionLocalProvider(
        LocalVideoPlayer provides player,
        LocalVideoPlayerContent provides videoPlayerContent
    ) {
        ProvideAppLocale(languageCode = appSettingsState.appLanguage) {
            CloudStreamTheme(
                theme = appSettingsState.theme,
                isDarkMode = effectiveDarkMode,
                systemAccentColor = nativeTheme.accentColor,
                content = content
            )
        }
    }
}

@Composable
private fun ResultPlaybackCoordinator(
    activeResultViewModel: ResultViewModel?,
    activeResultState: ResultState?,
    playerControllerViewModel: PlayerControllerViewModel,
    showSourcesOnPlay: Boolean,
    defaultAppTitle: String,
    pendingPlayEpisode: ResultEpisode?,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(activeResultViewModel) {
        val vm = activeResultViewModel ?: return@LaunchedEffect
        vm.effects.collect { effect ->
            val resultEffect = effect as? ResultEffect ?: return@collect
            handleResultEffect(
                effect = resultEffect,
                viewModel = vm,
                activeResultState = activeResultState,
                playerControllerViewModel = playerControllerViewModel,
                showSourcesOnPlay = showSourcesOnPlay,
                defaultAppTitle = defaultAppTitle,
                clipboardManager = clipboardManager,
                onSetPendingPlayEpisode = onSetPendingPlayEpisode,
                onNavigateToPlayer = onNavigateToPlayer
            )
        }
    }

    LaunchedEffect(activeResultState?.extractedLinks, activeResultState?.extractedSubtitles, activeResultState?.linksLoadingError) {
        handleExtractedMediaState(
            activeResultState = activeResultState,
            pendingPlayEpisode = pendingPlayEpisode,
            playerControllerViewModel = playerControllerViewModel,
            defaultAppTitle = defaultAppTitle,
            onSetPendingPlayEpisode = onSetPendingPlayEpisode,
            onNavigateToPlayer = onNavigateToPlayer
        )
    }
}

private fun resolveMediaTitle(
    specificName: String?,
    fallbackName: String?,
    defaultTitle: String
): String = specificName ?: fallbackName ?: defaultTitle

private fun handleResultEffect(
    effect: ResultEffect,
    viewModel: ResultViewModel,
    activeResultState: ResultState?,
    playerControllerViewModel: PlayerControllerViewModel,
    showSourcesOnPlay: Boolean,
    defaultAppTitle: String,
    clipboardManager: ClipboardManager,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    when (effect) {
        is ResultEffect.AutoPlayEpisode -> handleAutoPlayEpisode(
            effect = effect,
            viewModel = viewModel,
            activeResultState = activeResultState,
            playerControllerViewModel = playerControllerViewModel,
            showSourcesOnPlay = showSourcesOnPlay,
            defaultAppTitle = defaultAppTitle,
            onSetPendingPlayEpisode = onSetPendingPlayEpisode,
            onNavigateToPlayer = onNavigateToPlayer
        )
        is ResultEffect.CopyToClipboard -> clipboardManager.setText(AnnotatedString(effect.text))
        is ResultEffect.ShowToast -> Unit
    }
}

private fun handleAutoPlayEpisode(
    effect: ResultEffect.AutoPlayEpisode,
    viewModel: ResultViewModel,
    activeResultState: ResultState?,
    playerControllerViewModel: PlayerControllerViewModel,
    showSourcesOnPlay: Boolean,
    defaultAppTitle: String,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    viewModel.onEvent(ResultEvent.SelectEpisode(effect.episode))
    if (showSourcesOnPlay) {
        onSetPendingPlayEpisode(null)
        return
    }

    val currentLinks = activeResultState?.extractedLinks.orEmpty()
    if (currentLinks.isEmpty()) {
        onSetPendingPlayEpisode(effect.episode)
        return
    }

    onSetPendingPlayEpisode(null)
    dispatchLoadAndNavigate(
        playerControllerViewModel = playerControllerViewModel,
        links = currentLinks,
        subtitles = activeResultState?.extractedSubtitles.orEmpty(),
        mediaId = effect.episode.id,
        parentId = effect.parentId,
        resumePosition = effect.resumePosition,
        title = resolveMediaTitle(effect.episode.name, activeResultState?.title, defaultAppTitle),
        onNavigateToPlayer = onNavigateToPlayer
    )
}

private fun handleExtractedMediaState(
    activeResultState: ResultState?,
    pendingPlayEpisode: ResultEpisode?,
    playerControllerViewModel: PlayerControllerViewModel,
    defaultAppTitle: String,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    if (activeResultState?.linksLoadingError != null) {
        onSetPendingPlayEpisode(null)
    }

    val links = activeResultState?.extractedLinks.orEmpty()
    if (links.isEmpty()) return

    val subs = activeResultState?.extractedSubtitles.orEmpty()
    if (pendingPlayEpisode != null) {
        handlePendingEpisodePlayback(
            pendingPlayEpisode = pendingPlayEpisode,
            activeResultState = activeResultState,
            links = links,
            subs = subs,
            playerControllerViewModel = playerControllerViewModel,
            defaultAppTitle = defaultAppTitle,
            onSetPendingPlayEpisode = onSetPendingPlayEpisode,
            onNavigateToPlayer = onNavigateToPlayer
        )
    } else {
        playerControllerViewModel.handleEvent(
            PlayerUiEvent.UpdateQualitiesAndSubtitles(
                qualities = links.map { PlayerQuality.fromExtractorLink(it) },
                subtitles = subs.map { PlayerSubtitleTrack.fromSubtitleFile(it) }
            )
        )
    }
}

private fun handlePendingEpisodePlayback(
    pendingPlayEpisode: ResultEpisode,
    activeResultState: ResultState?,
    links: List<ExtractorLink>,
    subs: List<SubtitleFile>,
    playerControllerViewModel: PlayerControllerViewModel,
    defaultAppTitle: String,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    onSetPendingPlayEpisode(null)
    val currentEp = activeResultState?.selectedEpisode ?: pendingPlayEpisode
    val parentId = activeResultState?.mediaId ?: currentEp.parentId
    val resumePos = currentEp.position.takeIf { it > 0 }
    val title = resolveMediaTitle(currentEp.name, activeResultState?.title, defaultAppTitle)

    dispatchLoadAndNavigate(
        playerControllerViewModel = playerControllerViewModel,
        links = links,
        subtitles = subs,
        mediaId = currentEp.id,
        parentId = parentId,
        resumePosition = resumePos,
        title = title,
        onNavigateToPlayer = onNavigateToPlayer
    )
}

private fun dispatchLoadAndNavigate(
    playerControllerViewModel: PlayerControllerViewModel,
    links: List<ExtractorLink>,
    subtitles: List<SubtitleFile>,
    mediaId: Int?,
    parentId: Int?,
    resumePosition: Long?,
    title: String,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    val bestLink = links.firstOrNull() ?: return
    val qualities = links.map { PlayerQuality.fromExtractorLink(it) }
    val playerSubtitleTracks = subtitles.map { PlayerSubtitleTrack.fromSubtitleFile(it) }

    playerControllerViewModel.handleEvent(
        PlayerUiEvent.LoadMedia(
            url = bestLink.url,
            mediaId = mediaId,
            parentId = parentId,
            qualities = qualities,
            subtitles = playerSubtitleTracks,
            autoPlay = true,
            resumePosition = resumePosition
        )
    )
    onNavigateToPlayer(
        Screen.Player(
            title = title,
            url = bestLink.url,
            subtitles = subtitles.toImmutableList(),
            availableLinks = links.toImmutableList()
        )
    )
}

@Composable
private fun PlayerEffectsHandler(
    playerControllerViewModel: PlayerControllerViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(playerControllerViewModel) {
        playerControllerViewModel.effects.collect { effect ->
            when (effect) {
                is PlayerUiEffect.NavigateBack -> {
                    playerControllerViewModel.handleEvent(PlayerUiEvent.Pause)
                    onNavigateBack()
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun AppLayoutShell(
    currentScreen: Screen,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Screen) -> Unit,
    onToggleFullscreen: (() -> Unit)?,
    player: VideoPlayer,
    videoPlayerContent: @Composable (VideoPlayer, Modifier) -> Unit,
    playerControllerViewModel: PlayerControllerViewModel,
    onboardingViewModel: OnboardingViewModel,
    accountViewModel: AccountViewModel,
    contentRouter: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        when (currentScreen) {
            is Screen.Player -> {
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
                            onNavigateBack()
                        },
                        onToggleFullscreen = onToggleFullscreen,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            is Screen.Onboarding -> {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onComplete = { onNavigateTo(Screen.Home) },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            is Screen.AccountSelect -> {
                AccountSelectScreen(
                    viewModel = accountViewModel,
                    onProfileSelected = { onNavigateTo(Screen.Home) },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }

            else -> {
                ResponsiveAppScaffold(
                    currentScreen = currentScreen,
                    canNavigateBack = canNavigateBack,
                    onNavigateBack = onNavigateBack,
                    onNavigateTo = onNavigateTo,
                    contentRouter = contentRouter
                )
            }
        }
    }
}

@Composable
private fun ResponsiveAppScaffold(
    currentScreen: Screen,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Screen) -> Unit,
    contentRouter: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded by isLayoutState(Layout.TV or Layout.COMPUTER)
    val isMainTab = currentScreen.isMainTab

    if (isExpanded) {
        Row(modifier = modifier.fillMaxSize().background(Color.Black)) {
            AppNavigationRail(
                currentScreen = currentScreen,
                canNavigateBack = canNavigateBack,
                onNavigateBack = onNavigateBack,
                onNavigate = onNavigateTo,
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
                contentRouter(Modifier.fillMaxSize())
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .then(if (!isMainTab) Modifier.navigationBarsPadding() else Modifier)
            ) {
                contentRouter(Modifier.fillMaxSize())
            }

            if (isMainTab) {
                AppBottomNavigation(
                    currentScreen = currentScreen,
                    onNavigate = onNavigateTo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    }
}

private fun screenKeyFor(screen: Screen): String = when (screen) {
    is Screen.Details -> "details_${screen.url}_${screen.apiName}"
    is Screen.PluginDetails -> "plugin_${screen.plugin.internalName}"
    is Screen.Player -> "player"
    else -> screen::class.simpleName ?: "screen"
}

@Composable
private fun AppContentRouter(
    currentScreen: Screen,
    viewModels: AppViewModels,
    repos: AppRepositories,
    activeResultViewModel: ResultViewModel?,
    activeResultState: ResultState?,
    navigateTo: (Screen) -> Unit,
    navigateBack: () -> Unit,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit,
    modifier: Modifier = Modifier
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val defaultAppTitle = stringResource(Res.string.app_name)
    val screenKey = remember(currentScreen) { screenKeyFor(currentScreen) }

    Box(modifier = modifier) {
        saveableStateHolder.SaveableStateProvider(key = screenKey) {
            when (currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = viewModels.homeViewModelLazy.value,
                        onNavigateToDetails = { item, autoResume ->
                            navigateTo(Screen.Details(url = item.url, apiName = item.apiName, autoResume = autoResume))
                        },
                        onSearchClick = { navigateTo(Screen.Search) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Search -> {
                    SearchScreen(
                        viewModel = viewModels.searchViewModelLazy.value,
                        onNavigateToDetails = { item ->
                            navigateTo(Screen.Details(url = item.url, apiName = item.apiName))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Library -> {
                    LibraryScreen(
                        viewModel = viewModels.libraryViewModelLazy.value,
                        onNavigateToDetails = { url, apiName ->
                            navigateTo(Screen.Details(url = url, apiName = apiName))
                        },
                        onNavigateToHome = { navigateTo(Screen.Home) },
                        onSearchMedia = { query ->
                            viewModels.searchViewModelLazy.value.handleEvent(SearchEvent.Search(query))
                            navigateTo(Screen.Search)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Downloads -> {
                    DownloadsScreenDestination(
                        viewModel = viewModels.downloadsViewModelLazy.value,
                        playerControllerViewModel = viewModels.playerControllerViewModel,
                        defaultAppTitle = defaultAppTitle,
                        onNavigateToHome = { navigateTo(Screen.Home) },
                        onNavigateToPlayer = { navigateTo(it) }
                    )
                }

                is Screen.Plugins -> {
                    PluginsScreen(
                        viewModel = viewModels.pluginsViewModelLazy.value,
                        onNavigateToPluginDetails = { plugin ->
                            navigateTo(Screen.PluginDetails(plugin))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        appSettingsViewModel = viewModels.appSettingsViewModel,
                        pluginsViewModel = viewModels.pluginsViewModelLazy.value,
                        onBackClick = { navigateBack() },
                        onNavigateToAccountSelect = { navigateTo(Screen.AccountSelect) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Screen.Details -> {
                    DetailsScreenDestination(
                        currentScreen = currentScreen,
                        activeResultViewModel = activeResultViewModel,
                        activeResultState = activeResultState,
                        repos = repos,
                        appSettingsViewModel = viewModels.appSettingsViewModel,
                        playerControllerViewModel = viewModels.playerControllerViewModel,
                        defaultAppTitle = defaultAppTitle,
                        navigateTo = navigateTo,
                        navigateBack = navigateBack,
                        onSetPendingPlayEpisode = onSetPendingPlayEpisode
                    )
                }

                is Screen.PluginDetails -> {
                    PluginDetailsScreen(
                        plugin = currentScreen.plugin,
                        viewModel = viewModels.pluginsViewModelLazy.value,
                        onBackClick = { navigateBack() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Screen.Onboarding -> {
                    OnboardingScreen(
                        viewModel = viewModels.onboardingViewModelLazy.value,
                        onComplete = { navigateTo(Screen.Home) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Screen.AccountSelect -> {
                    AccountSelectScreen(
                        viewModel = viewModels.accountViewModelLazy.value,
                        onProfileSelected = { navigateTo(Screen.Home) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun DownloadsScreenDestination(
    viewModel: DownloadsViewModel,
    playerControllerViewModel: PlayerControllerViewModel,
    defaultAppTitle: String,
    onNavigateToHome: () -> Unit,
    onNavigateToPlayer: (Screen.Player) -> Unit
) {
    DownloadsScreen(
        viewModel = viewModel,
        onNavigateToExplore = onNavigateToHome,
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
                onNavigateToPlayer(
                    Screen.Player(
                        title = offlineTitle,
                        url = offlineUrl,
                        subtitles = persistentListOf(),
                        availableLinks = persistentListOf()
                    )
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun DetailsScreenDestination(
    currentScreen: Screen.Details,
    activeResultViewModel: ResultViewModel?,
    activeResultState: ResultState?,
    repos: AppRepositories,
    appSettingsViewModel: AppSettingsViewModel,
    playerControllerViewModel: PlayerControllerViewModel,
    defaultAppTitle: String,
    navigateTo: (Screen) -> Unit,
    navigateBack: () -> Unit,
    onSetPendingPlayEpisode: (ResultEpisode?) -> Unit
) {
    val appSettingsState by appSettingsViewModel.state.collectAsState()
    val resultViewModel = activeResultViewModel ?: remember(currentScreen.url, currentScreen.apiName) {
        ResultViewModel(
            bookmarkRepository = repos.bookmarkRepository,
            watchProgressRepository = repos.watchProgressRepository,
            favoriteRepository = repos.favoriteRepository,
            resumeWatchingRepository = repos.resumeWatchingRepository,
            subscriptionRepository = repos.subscriptionRepository
        ).apply {
            onEvent(ResultEvent.LoadResult(url = currentScreen.url, apiName = currentScreen.apiName, autoResume = currentScreen.autoResume))
        }
    }

    ResultScreen(
        viewModel = resultViewModel,
        showSourcesOnPlay = appSettingsState.showSourcesOnPlay,
        onBack = { navigateBack() },
        onPlayEpisode = { episode ->
            resultViewModel.onEvent(ResultEvent.SelectEpisode(episode))
            resultViewModel.onEvent(ResultEvent.ReloadLinks(episode))
            onSetPendingPlayEpisode(episode)
        },
        onPlayLink = { selectedLink, allLinks, subs, initialSubtitle ->
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

@Preview
@Composable
private fun ResponsiveAppScaffoldPhonePreview() {
    CloudStreamTheme {
        ResponsiveAppScaffold(
            currentScreen = Screen.Home,
            canNavigateBack = false,
            onNavigateBack = {},
            onNavigateTo = {},
            contentRouter = { modifier ->
                Box(modifier = modifier.background(Color.DarkGray))
            }
        )
    }
}

@Preview
@Composable
private fun ResponsiveAppScaffoldExpandedPreview() {
    CloudStreamTheme {
        CompositionLocalProvider(LocalLayout provides Layout.TV) {
            ResponsiveAppScaffold(
                currentScreen = Screen.Home,
                canNavigateBack = false,
                onNavigateBack = {},
                onNavigateTo = {},
                contentRouter = { modifier ->
                    Box(modifier = modifier.background(Color.DarkGray))
                }
            )
        }
    }
}

