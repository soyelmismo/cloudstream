package com.lagradost.cloudstream3.shared.viewmodels

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ProviderRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ProviderRepositoryImpl
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import cloudstream.shared_ui.generated.resources.Res
import cloudstream.shared_ui.generated.resources.home_error_load_failed
import cloudstream.shared_ui.generated.resources.home_error_no_homepage
import cloudstream.shared_ui.generated.resources.home_error_no_provider
import com.lagradost.cloudstream3.utils.txt
import kotlin.coroutines.CoroutineContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@Suppress("DEPRECATION_ERROR")
private fun createResumeSearchResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType?,
    posterUrl: String?,
    year: Int?,
    id: Int?,
    quality: SearchQuality?
): SearchResponse {
    val targetType = type ?: TvType.Movie
    return when (targetType) {
        TvType.Anime, TvType.AnimeMovie, TvType.OVA -> AnimeSearchResponse(
            name = name,
            url = url,
            apiName = apiName,
            type = targetType,
            posterUrl = posterUrl,
            year = year,
            dubStatus = null,
            otherName = null,
            episodes = mutableMapOf(),
            id = id,
            quality = quality,
            posterHeaders = null,
            score = null
        )
        TvType.TvSeries -> TvSeriesSearchResponse(
            name = name,
            url = url,
            apiName = apiName,
            type = targetType,
            posterUrl = posterUrl,
            year = year,
            episodes = null,
            id = id,
            quality = quality,
            posterHeaders = null,
            score = null
        )
        TvType.Live -> LiveSearchResponse(
            name = name,
            url = url,
            apiName = apiName,
            type = targetType,
            posterUrl = posterUrl,
            id = id,
            quality = quality,
            posterHeaders = null,
            lang = null,
            score = null
        )
        else -> MovieSearchResponse(
            name = name,
            url = url,
            apiName = apiName,
            type = targetType,
            posterUrl = posterUrl,
            year = year,
            id = id,
            quality = quality,
            posterHeaders = null,
            score = null
        )
    }
}

class HomeViewModel(
    private val providerRepository: ProviderRepository = ProviderRepositoryImpl(),
    private val providersProvider: (() -> List<MainAPI>)? = null,
    private val bookmarkRepository: BookmarkRepository? = null,
    private val watchProgressRepository: WatchProgressRepository? = null,
    private val resumeWatchingRepository: ResumeWatchingRepository? = null,
    private val preferenceRepository: AppPreferenceRepository = AppPreferenceManager.currentRepository,
    private val accountId: Int = 0,
    initialState: HomeState = HomeState(),
    autoLoad: Boolean = true,
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<HomeState, HomeEvent>(initialState, coroutineContext) {

    private var unregisterProvidersListener: (() -> Unit)? = null

    init {
        initializeProviders(loadContent = autoLoad)
        unregisterProvidersListener = providerRepository.addOnProvidersChangedListener {
            initializeProviders(loadContent = false)
        }
        observeResumeWatching()
    }

    private fun getAvailableProviders(): List<MainAPI> {
        return providersProvider?.invoke() ?: providerRepository.getAllProviders()
    }

    private fun buildActiveResumeItem(
        mediaId: Int,
        bookmarkMap: Map<Int, BookmarkEntity>,
        progressMap: Map<Int, WatchProgressEntity>,
        resumeMap: Map<Int, ResumeWatchingEntity>
    ): Triple<SearchResponse, Pair<String, Float>, Long>? {
        val bookmark = bookmarkMap[mediaId] ?: return null
        val resume = resumeMap[mediaId]
        val progress = (resume?.episodeId?.let { progressMap[it] }) ?: progressMap[mediaId] ?: return null

        if (progress.duration <= 0 || progress.position <= 0 || progress.watchState == 2) return null
        val ratio = (progress.position.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
        if (ratio !in 0.005f..0.95f) return null

        val lastUpdated = maxOf(
            progress.lastUpdated,
            resume?.updateTime ?: 0L,
            bookmark.latestUpdatedTime,
            bookmark.bookmarkedTime
        )
        val item = createResumeSearchResponse(
            name = bookmark.name,
            url = bookmark.url,
            apiName = bookmark.apiName,
            type = bookmark.type,
            posterUrl = bookmark.posterUrl,
            year = bookmark.year,
            id = bookmark.id,
            quality = bookmark.quality
        )
        return Triple(item, bookmark.url to ratio, lastUpdated)
    }

    private fun observeResumeWatching() {
        if (bookmarkRepository == null || watchProgressRepository == null) return
        launchSafeJob(key = "resume_watching") {
            combine(
                bookmarkRepository.getAllBookmarksFlow(accountId),
                watchProgressRepository.getAllProgressFlow(accountId),
                resumeWatchingRepository?.getAllResumeWatchingFlow(accountId) ?: flowOf(emptyList())
            ) { bookmarks, progresses, resumeWatchings ->
                val bookmarkMap = bookmarks.associateBy { it.id }
                val progressMap = progresses.associateBy { it.mediaId }
                val resumeMap = resumeWatchings.associateBy { it.parentId }

                val candidateIds = (resumeWatchings.map { it.parentId } + bookmarks.map { it.id }).distinct()
                val activeItems = candidateIds.mapNotNull { mediaId ->
                    buildActiveResumeItem(mediaId, bookmarkMap, progressMap, resumeMap)
                }.sortedByDescending { it.third }

                val resumeItems = activeItems.map { it.first }.distinctBy { it.url }.toImmutableList()
                val progressValues = activeItems.map { it.second }.toMap().toImmutableMap()

                updateState {
                    copy(
                        resumeWatching = resumeItems,
                        resumeWatchingProgress = progressValues
                    )
                }
            }.collect()
        }
    }

    fun initializeProviders(loadContent: Boolean = true) {
        val all = getAvailableProviders()
        val providers = all.filter { it.hasMainPage }.ifEmpty { all }
        val savedProviderName = preferenceRepository.getStringSync(AppPreferenceManager.KEY_HOME_API_USED)
        val selected = currentState.selectedProvider?.let { curr ->
            providers.find { it.name == curr.name }
        } ?: savedProviderName?.let { name ->
            providers.find { it.name == name }
        } ?: providers.firstOrNull()

        updateState {
            copy(
                availableProviders = providers.toImmutableList(),
                selectedProvider = selected
            )
        }

        if (loadContent || (currentState.carousels.isEmpty() && !currentState.isLoading)) {
            if (selected != null) {
                handleEvent(HomeEvent.LoadHome(providerName = selected.name))
            } else if (providers.isEmpty()) {
                updateState {
                    copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = txt(Res.string.home_error_no_provider)
                    )
                }
            }
        }
    }

    override fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHome,
            is HomeEvent.SelectProvider,
            is HomeEvent.SelectProviderByName,
            is HomeEvent.RefreshHome -> handleProviderEvent(event)

            is HomeEvent.SelectItem,
            is HomeEvent.ResumeItem,
            is HomeEvent.ExpandCarousel,
            is HomeEvent.RemoveFromResumeWatching,
            is HomeEvent.DismissError -> handleNavigationAndContentEvent(event)
        }
    }

    private fun handleProviderEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHome -> handleLoadHome(event.providerName, event.forceReload)
            is HomeEvent.SelectProvider -> selectProvider(event.provider)
            is HomeEvent.SelectProviderByName -> handleSelectProviderByName(event.providerName)
            is HomeEvent.RefreshHome -> handleRefreshHome()
            else -> Unit
        }
    }

    private fun handleNavigationAndContentEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SelectItem -> {
                updateState { copy(selectedItem = event.item) }
                if (event.item != null) emitEffect(HomeEffect.NavigateToDetails(item = event.item, autoResume = false))
            }
            is HomeEvent.ResumeItem -> {
                updateState { copy(selectedItem = event.item) }
                emitEffect(HomeEffect.NavigateToDetails(item = event.item, autoResume = true))
            }
            is HomeEvent.ExpandCarousel -> expandCarousel(event.carouselName)
            is HomeEvent.RemoveFromResumeWatching -> handleRemoveResumeWatching(event.item)
            is HomeEvent.DismissError -> updateState { copy(error = null) }
            else -> Unit
        }
    }

    private fun handleLoadHome(providerName: String?, forceReload: Boolean) {
        val targetProvider = if (providerName != null) {
            currentState.availableProviders.find { it.name == providerName }
                ?: providerRepository.getApiByName(providerName)
        } else {
            currentState.selectedProvider ?: currentState.availableProviders.firstOrNull()
        }

        if (targetProvider != null) {
            updateState { copy(selectedProvider = targetProvider) }
            loadHomePage(targetProvider, forceReload = forceReload)
        } else {
            updateState {
                copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = txt(Res.string.home_error_no_provider)
                )
            }
        }
    }

    private fun handleSelectProviderByName(providerName: String) {
        val provider = currentState.availableProviders.find { it.name == providerName }
            ?: providerRepository.getApiByName(providerName)
        if (provider != null) {
            selectProvider(provider)
        }
    }

    private fun handleRefreshHome() {
        val currentProvider = currentState.selectedProvider ?: return
        updateState { copy(isRefreshing = true, error = null) }
        loadHomePage(currentProvider, forceReload = true, isRefresh = true)
    }

    private fun handleRemoveResumeWatching(item: SearchResponse) {
        launch {
            val mediaId = item.id
                ?: bookmarkRepository?.getAllBookmarks(accountId)?.find { it.url == item.url }?.id
            if (mediaId != null) {
                val resume = resumeWatchingRepository?.getResumeWatching(accountId, mediaId)
                resume?.episodeId?.let { epId ->
                    watchProgressRepository?.deleteProgress(accountId, epId)
                }
                watchProgressRepository?.deleteProgress(accountId, mediaId)
                resumeWatchingRepository?.deleteResumeWatching(accountId, mediaId)
            }
        }
    }

    private fun selectProvider(provider: MainAPI) {
        if (currentState.selectedProvider?.name != provider.name) {
            preferenceRepository.setStringSync(AppPreferenceManager.KEY_HOME_API_USED, provider.name)
            updateState { copy(selectedProvider = provider) }
            loadHomePage(provider, forceReload = true)
        }
    }

    private suspend fun fetchMainPageResponses(
        provider: MainAPI,
        mainPages: List<MainPageData>
    ): List<HomePageResponse?> {
        if (provider.sequentialMainPage) {
            val list = mutableListOf<HomePageResponse?>()
            for ((index, pageData) in mainPages.withIndex()) {
                if (index > 0 && provider.sequentialMainPageDelay > 0) {
                    delay(provider.sequentialMainPageDelay)
                }
                val res = provider.getMainPage(
                    page = 1,
                    request = MainPageRequest(
                        name = pageData.name,
                        data = pageData.data,
                        horizontalImages = pageData.horizontalImages
                    )
                )
                list.add(res)
            }
            return list
        }
        return coroutineScope {
            mainPages.map { pageData ->
                async {
                    provider.getMainPage(
                        page = 1,
                        request = MainPageRequest(
                            name = pageData.name,
                            data = pageData.data,
                            horizontalImages = pageData.horizontalImages
                        )
                    )
                }
            }.awaitAll()
        }
    }

    private fun parseHomeCarousels(
        mainPages: List<MainPageData>,
        responses: List<HomePageResponse?>
    ): List<HomeCarousel> {
        val carousels = mutableListOf<HomeCarousel>()
        for ((index, pageData) in mainPages.withIndex()) {
            val response = responses.getOrNull(index) ?: continue
            for (item in response.items) {
                if (item.list.isNotEmpty()) {
                    carousels.add(
                        HomeCarousel(
                            name = item.name.ifBlank { pageData.name.ifBlank { "Featured" } },
                            items = item.list.toImmutableList(),
                            isHorizontalImages = item.isHorizontalImages || pageData.horizontalImages,
                            currentPage = 1,
                            hasNext = response.hasNext,
                            isLoadingMore = false,
                            data = pageData.data
                        )
                    )
                }
            }
        }
        return carousels
    }

    private fun loadHomePage(
        provider: MainAPI,
        forceReload: Boolean = false,
        isRefresh: Boolean = false
    ) {
        launchSafeJob(
            key = "load_home",
            onError = { e ->
                updateState {
                    copy(
                        carousels = persistentListOf(),
                        featuredItems = persistentListOf(),
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message?.let { txt(it) } ?: txt(Res.string.home_error_load_failed)
                    )
                }
            }
        ) job@{
            updateState {
                copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null
                )
            }

            if (!provider.hasMainPage) {
                updateState {
                    copy(
                        carousels = persistentListOf(),
                        featuredItems = persistentListOf(),
                        isLoading = false,
                        isRefreshing = false,
                        error = txt(Res.string.home_error_no_homepage, provider.name)
                    )
                }
                return@job
            }

            val mainPages = provider.mainPage.ifEmpty {
                listOf(MainPageData(name = "", data = "", horizontalImages = false))
            }
            val responses = fetchMainPageResponses(provider, mainPages)
            val carousels = parseHomeCarousels(mainPages, responses)
            val allItems = carousels.flatMap { it.items }.distinctBy { it.url }
            val featured = if (allItems.isNotEmpty()) {
                allItems.shuffled().take(6).toImmutableList()
            } else {
                persistentListOf()
            }

            updateState {
                copy(
                    carousels = carousels.toImmutableList(),
                    featuredItems = featured,
                    isLoading = false,
                    isRefreshing = false,
                    error = if (carousels.isEmpty()) txt("No content found on homepage") else null
                )
            }
        }
    }

    private fun patchCarousel(carouselName: String, transform: (HomeCarousel) -> HomeCarousel) {
        updateState {
            copy(
                carousels = carousels.map {
                    if (it.name == carouselName) transform(it) else it
                }.toImmutableList()
            )
        }
    }

    private fun expandCarousel(carouselName: String) {
        val targetCarousel = currentState.carousels.find { it.name == carouselName } ?: return
        if (!targetCarousel.hasNext || targetCarousel.isLoadingMore) return

        launchSafeJob(
            key = "expand_carousel_$carouselName",
            onError = {
                patchCarousel(carouselName) { it.copy(isLoadingMore = false) }
            }
        ) job@{
            patchCarousel(carouselName) { it.copy(isLoadingMore = true) }

            val provider = currentState.selectedProvider
            if (provider == null) {
                patchCarousel(carouselName) { it.copy(isLoadingMore = false) }
                return@job
            }

            val nextPage = targetCarousel.currentPage + 1
            val pageData = provider.mainPage.find { it.name == carouselName }
                ?: MainPageData(
                    name = targetCarousel.name,
                    data = targetCarousel.data,
                    horizontalImages = targetCarousel.isHorizontalImages
                )

            val response = provider.getMainPage(
                page = nextPage,
                request = MainPageRequest(
                    name = pageData.name,
                    data = pageData.data,
                    horizontalImages = pageData.horizontalImages
                )
            )

            if (response != null && response.items.isNotEmpty()) {
                val newItems = response.items.flatMap { it.list }
                val mergedList = (targetCarousel.items + newItems).distinctBy { it.url }.toImmutableList()

                patchCarousel(carouselName) {
                    it.copy(
                        items = mergedList,
                        currentPage = nextPage,
                        hasNext = response.hasNext,
                        isLoadingMore = false
                    )
                }
            } else {
                patchCarousel(carouselName) {
                    it.copy(hasNext = false, isLoadingMore = false)
                }
            }
        }
    }

    override fun onCleared() {
        unregisterProvidersListener?.invoke()
        unregisterProvidersListener = null
        super.onCleared()
    }
}
