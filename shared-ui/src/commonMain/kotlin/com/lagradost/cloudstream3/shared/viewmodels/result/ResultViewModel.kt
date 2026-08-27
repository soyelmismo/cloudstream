package com.lagradost.cloudstream3.shared.viewmodels.result

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.isAnimeBased
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import cloudstream.shared_ui.generated.resources.Res
import cloudstream.shared_ui.generated.resources.copy_link_toast
import cloudstream.shared_ui.generated.resources.result_error_api_not_found
import cloudstream.shared_ui.generated.resources.result_error_extract_links_failed
import cloudstream.shared_ui.generated.resources.result_error_invalid_trailer_url
import cloudstream.shared_ui.generated.resources.result_error_load_details_failed
import cloudstream.shared_ui.generated.resources.result_error_no_api_specified
import cloudstream.shared_ui.generated.resources.result_error_no_stream_url
import cloudstream.shared_ui.generated.resources.result_error_no_trailer
import cloudstream.shared_ui.generated.resources.result_error_provider_not_found
import cloudstream.shared_ui.generated.resources.result_error_trailer_load_failed
import cloudstream.shared_ui.generated.resources.result_error_trailer_no_links
import com.lagradost.cloudstream3.utils.txt
import org.jetbrains.compose.resources.getString
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SyncMappingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.SubscriptionRepository
import com.lagradost.cloudstream3.shared.persistence.repository.SyncMappingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Extension function to calculate a consistent unique Integer ID for a LoadResponse.
 */
fun LoadResponse.getId(): Int {
    val mainUrl = APIHolder.getApiFromNameNull(apiName)?.mainUrl ?: ""
    return uniqueUrl.replace(mainUrl, "").replace("/", "").hashCode()
}

/**
 * ResultViewModel for Media Details screen in Kotlin Multiplatform (MVI Architecture).
 *
 * Handles:
 * - Media metadata, synopsis, poster, background, cast, score, trailers.
 * - Seasons and episodes grouping, dub status selection, episode filtering.
 * - Reactive Room persistence integration (Bookmarks, Favorites, Subscriptions, Watch Progress, Resume Watching, Sync Mappings).
 * - External synchronization (AniList, MyAnimeList, Simkl, Kitsu).
 * - Streaming links and subtitles extraction.
 * - StateFlow immutable state updates with MviViewModel.
 */
class ResultViewModel(
    private val bookmarkRepository: BookmarkRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val favoriteRepository: FavoriteRepository,
    private val resumeWatchingRepository: ResumeWatchingRepository? = null,
    private val subscriptionRepository: SubscriptionRepository? = null,
    private val syncMappingRepository: SyncMappingRepository? = null,
    private val accountId: Int = 0,
    initialState: ResultState = ResultState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<ResultState, ResultEvent>(initialState, coroutineContext) {

    private val linksMutex = Mutex()

    override fun handleEvent(event: ResultEvent) {
        when (event) {
            is ResultEvent.LoadResult -> loadResult(event.url, event.apiName, event.restart, event.autoResume)
            is ResultEvent.ToggleBookmark -> toggleBookmark(event.watchType)
            is ResultEvent.SetBookmark -> setBookmark(event.watchType)
            is ResultEvent.ToggleFavorite -> setFavorite(!currentState.isFavorite)
            is ResultEvent.SetFavorite -> setFavorite(event.isFavorite)
            is ResultEvent.ToggleSubscription -> setSubscription(!currentState.isSubscribed)
            is ResultEvent.SetSubscription -> setSubscription(event.isSubscribed)
            is ResultEvent.SetWatchState -> setWatchState(event.episodeId, event.watchState)
            is ResultEvent.UpdateWatchProgress -> updateWatchProgress(
                event.episodeId,
                event.position,
                event.duration,
                event.watchState
            )
            is ResultEvent.SelectSeason -> selectSeason(event.season)
            is ResultEvent.SelectDubStatus -> selectDubStatus(event.dubStatus)
            is ResultEvent.SelectEpisode -> selectEpisode(event.episode)
            is ResultEvent.Refresh -> {
                val url = currentState.url ?: return
                val apiName = currentState.apiName ?: return
                loadResult(url, apiName, restart = true)
            }
            is ResultEvent.OpenEpisodeMenu -> updateState {
                copy(
                    isEpisodeMenuOpen = true,
                    selectedMenuEpisode = event.episode
                )
            }
            is ResultEvent.CloseEpisodeMenu -> updateState {
                copy(
                    isEpisodeMenuOpen = false,
                    selectedMenuEpisode = null
                )
            }
            is ResultEvent.MarkEpisodesUpTo -> markEpisodesUpTo(event.episodeId, event.season)
            is ResultEvent.CopyEpisodeLink -> copyEpisodeLink(event.episode)
            is ResultEvent.ReloadLinks -> reloadLinks(event.episode, event.isCasting, event.clearCache)
            is ResultEvent.ClearLinks -> clearLinks()
            is ResultEvent.ClearError -> clearError(event.linksOnly)
            is ResultEvent.UpdateSyncStatus -> updateSyncStatus(event.service, event.status)
            is ResultEvent.UpdateSyncScore -> updateSyncScore(event.service, event)
            is ResultEvent.SetSyncScoreScale -> setSyncScoreScale(event.service, event.scale)
            is ResultEvent.UpdateSyncEpisode -> updateSyncEpisode(event.service, event.episode)
            is ResultEvent.SelectSyncService -> updateState { copy(selectedSyncService = event.service) }
            is ResultEvent.SaveSyncData -> saveSyncData(event)
            is ResultEvent.UnlinkSyncService -> unlinkSyncService(event.service)
            is ResultEvent.OpenTrailer -> openTrailer(event.trailerIndex)
            is ResultEvent.LoadTrailer -> loadTrailer(event.trailerIndex)
            is ResultEvent.SelectTrailerQuality -> updateState { copy(selectedTrailerQuality = event.link) }
            is ResultEvent.CloseTrailer -> closeTrailer()
        }
    }

    /**
     * Loads media details from the provider.
     */
    private fun loadResult(url: String, apiName: String, restart: Boolean = false, autoResume: Boolean = false) {
        if (!restart && !autoResume && currentState.loadResponse != null && currentState.url == url && currentState.apiName == apiName) {
            return
        }

        launchSafeJob(key = "load_result") job@{
            updateState {
                copy(
                    isLoading = true,
                    error = null,
                    url = url,
                    apiName = apiName
                )
            }

            try {
                val api = APIHolder.getApiFromNameNull(apiName)
                    ?: APIHolder.getApiFromUrlNull(url)
                if (api == null) {
                    updateState {
                        copy(
                            isLoading = false,
                            error = txt(Res.string.result_error_api_not_found, apiName)
                        )
                    }
                    return@job
                }

                val response = try {
                    api.load(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError(e)
                    null
                }

                if (response == null) {
                    updateState {
                        copy(
                            isLoading = false,
                            error = txt(Res.string.result_error_load_details_failed)
                        )
                    }
                    return@job
                }

                val mediaId = response.getId()

                // Process Episodes and Indexing
                val episodesMap = buildEpisodesMap(response, mediaId)
                val distinctSeasons = episodesMap.keys.map { it.season }.distinct().sorted()
                val seasonNames = (response as? EpisodeResponse)?.seasonNames
                val availableSeasons = distinctSeasons.map { s ->
                    val sData = seasonNames?.firstOrNull { it.season == s }
                    val count = episodesMap.filterKeys { it.season == s }.values.flatten().size
                    ResultSeason(
                        season = s,
                        name = sData?.name,
                        displaySeason = sData?.displaySeason ?: s,
                        episodeCount = count
                    )
                }

                val availableDubStatuses = episodesMap.keys.map { it.dubStatus }.distinct()
                val initialDubStatus = availableDubStatuses.firstOrNull { it == DubStatus.Subbed }
                    ?: availableDubStatuses.firstOrNull()
                    ?: DubStatus.None
                val initialSeason = distinctSeasons.firstOrNull { it > 0 } ?: distinctSeasons.firstOrNull() ?: 0

                val filteredEpisodes = episodesMap[EpisodeIndexer(initialDubStatus, initialSeason)]
                    ?: episodesMap.entries.firstOrNull { it.key.season == initialSeason }?.value
                    ?: episodesMap.entries.firstOrNull { it.key.dubStatus == initialDubStatus }?.value
                    ?: episodesMap.values.firstOrNull { it.isNotEmpty() }
                    ?: episodesMap.values.flatten()

                // Check initial progress / resume watching
                val resumeData = resumeWatchingRepository?.getResumeWatching(accountId, mediaId)
                val allEps = episodesMap.values.flatten()
                val lastWatchedEp = resumeData?.episodeId?.let { id -> allEps.firstOrNull { it.id == id } }
                    ?: allEps.firstOrNull { it.position > 0 }
                    ?: allEps.firstOrNull()

                val lastProgress = lastWatchedEp?.let { ep ->
                    watchProgressRepository.getProgress(accountId, ep.id)
                } ?: watchProgressRepository.getProgress(accountId, mediaId)

                val resolvedSelectedEp = lastWatchedEp?.let { ep ->
                    if (lastProgress != null && (lastProgress.position > 0 || lastProgress.duration > 0 || lastProgress.watchState > 0)) {
                        ep.copy(
                            position = lastProgress.position,
                            duration = lastProgress.duration,
                            videoWatchState = lastProgress.watchState
                        )
                    } else {
                        ep
                    }
                }

                // Check Initial Bookmark & Favorite & Subscription
                var bookmark = bookmarkRepository.getBookmark(accountId, mediaId)
                if (bookmark == null) {
                    val now = APIHolder.unixTimeMS
                    val newBookmark = BookmarkEntity(
                        accountId = accountId,
                        id = mediaId,
                        name = response.name,
                        url = response.url,
                        apiName = response.apiName,
                        type = response.type,
                        posterUrl = response.posterUrl ?: response.backgroundPosterUrl,
                        year = response.year,
                        watchType = 0,
                        bookmarkedTime = now,
                        latestUpdatedTime = now,
                        plot = response.plot,
                        score = response.score?.toDouble(10)
                    )
                    bookmarkRepository.saveBookmark(newBookmark)
                    bookmark = newBookmark
                }

                val favorite = favoriteRepository.getFavorite(accountId, mediaId)
                val subscription = subscriptionRepository?.getSubscription(accountId, mediaId)

                // Calculate episode counts and watched count for External Sync
                val totalEpisodeCount: Int? = if (response.isEpisodeBased()) {
                    allEps.size.takeIf { it > 0 }
                } else null
                val watchedCount = allEps.count { it.isWatched || it.videoWatchState == 2 }

                // Check stored sync mappings
                val storedMappings = syncMappingRepository?.getSyncMappings(accountId, mediaId) ?: emptyList()
                val initialSyncStates = SyncService.entries.associateWith { service ->
                    val storedMapping = storedMappings.firstOrNull { it.syncPrefix.equals(service.idPrefix, ignoreCase = true) }
                    val responseSyncId = response.syncData[service.idPrefix]
                        ?: response.syncData[service.serviceName.lowercase()]
                        ?: response.syncData[service.name.lowercase()]

                    val effectiveId = storedMapping?.remoteUrl ?: responseSyncId
                    val isLinked = !effectiveId.isNullOrBlank()
                    val initialStatus = when {
                        !isLinked -> ExternalSyncStatus.None
                        totalEpisodeCount != null && watchedCount >= totalEpisodeCount -> ExternalSyncStatus.Completed
                        watchedCount > 0 -> ExternalSyncStatus.Watching
                        else -> ExternalSyncStatus.PlanToWatch
                    }

                    val defaultScale = when (service) {
                        SyncService.AniList -> TrackerScoreScale.Point10Decimal
                        SyncService.MyAnimeList -> TrackerScoreScale.Point10Decimal
                        SyncService.Trakt -> TrackerScoreScale.Point10Decimal
                        SyncService.Simkl -> TrackerScoreScale.Point10Decimal
                        SyncService.Kitsu -> TrackerScoreScale.Point100
                    }

                    ExternalSyncEntry(
                        service = service,
                        syncId = effectiveId,
                        isLinked = isLinked,
                        status = initialStatus,
                        score = response.score?.toInt(10)?.coerceIn(1, 10),
                        rawScore = response.score,
                        scoreScale = defaultScale,
                        watchedEpisodes = watchedCount,
                        maxEpisodes = totalEpisodeCount,
                        lastUpdated = storedMapping?.updatedAt ?: 0L
                    )
                }

                updateState {
                    copy(
                        isLoading = false,
                        error = null,
                        url = url,
                        apiName = apiName,
                        mediaId = mediaId,
                        loadResponse = response,
                        title = response.name,
                        synopsis = response.plot,
                        posterUrl = response.posterUrl,
                        backgroundPosterUrl = response.backgroundPosterUrl,
                        logoUrl = response.logoUrl,
                        year = response.year,
                        rating = response.score,
                        tags = response.tags ?: emptyList(),
                        actors = response.actors ?: emptyList(),
                        tvType = response.type,
                        duration = response.duration,
                        comingSoon = response.comingSoon,
                        showStatus = (response as? EpisodeResponse)?.showStatus,
                        contentRating = response.contentRating,
                        trailers = response.trailers,
                        recommendations = response.recommendations ?: emptyList(),
                        syncData = response.syncData,
                        posterHeaders = response.posterHeaders,
                        isMovie = response.isMovie(),
                        isAnime = response.isAnimeBased(),
                        isEpisodeBased = response.isEpisodeBased(),
                        availableSeasons = availableSeasons,
                        availableDubStatuses = availableDubStatuses,
                        selectedSeason = initialSeason,
                        selectedDubStatus = initialDubStatus,
                        episodesByIndexer = episodesMap,
                        episodes = filteredEpisodes,
                        selectedEpisode = resolvedSelectedEp,
                        isBookmarked = bookmark.watchType > 0,
                        bookmarkWatchType = bookmark.watchType,
                        isFavorite = favorite != null,
                        isSubscribed = subscription != null,
                        lastWatchedEpisode = resolvedSelectedEp,
                        lastWatchedProgress = lastProgress,
                        resumeWatching = resumeData,
                        externalSyncStates = initialSyncStates
                    )
                }

                // Start observing reactive Room KMP flows
                observeRepositories(mediaId)

                if (autoResume) {
                    val targetEp = lastWatchedEp ?: resolvedSelectedEp ?: allEps.firstOrNull()
                    if (targetEp != null) {
                        val resumePos = lastProgress?.position ?: targetEp.position.takeIf { it > 0 }
                        emitEffect(
                            ResultEffect.AutoPlayEpisode(
                                episode = targetEp,
                                resumePosition = resumePos,
                                parentId = mediaId
                            )
                        )
                        reloadLinks(targetEp, isCasting = false)
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e)
                updateState {
                    copy(
                        isLoading = false,
                        error = e.message?.let { txt(it) } ?: txt("An unexpected error occurred")
                    )
                }
            }
        }
    }

    private suspend fun buildSingleEpisode(
        response: LoadResponse,
        mediaId: Int,
        id: Int = mediaId,
        name: String? = response.name,
        poster: String? = response.posterUrl ?: response.backgroundPosterUrl,
        episode: Int = 0,
        seasonIndex: Int? = 0,
        season: Int? = 0,
        data: String = response.url,
        index: Int = 0,
        score: Score? = response.score,
        description: String? = response.plot,
        totalEpisodeIndex: Int? = null,
        airDate: Long? = null,
        runTime: Int? = null,
        seasonData: com.lagradost.cloudstream3.SeasonData? = null
    ): ResultEpisode {
        val progress = watchProgressRepository.getProgress(accountId, id)
        return ResultEpisode(
            headerName = response.name,
            name = name,
            poster = poster,
            episode = episode,
            seasonIndex = seasonIndex,
            season = season,
            data = data,
            apiName = response.apiName,
            id = id,
            index = index,
            position = progress?.position ?: 0L,
            duration = progress?.duration ?: 0L,
            score = score,
            description = description,
            isFiller = null,
            tvType = response.type,
            parentId = mediaId,
            videoWatchState = progress?.watchState ?: 0,
            totalEpisodeIndex = totalEpisodeIndex,
            airDate = airDate,
            runTime = runTime,
            seasonData = seasonData
        )
    }

    /**
     * Builds the map of EpisodeIndexer to list of ResultEpisodes for all response types.
     */
    private suspend fun buildEpisodesMap(
        response: LoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val map = mutableMapOf<EpisodeIndexer, MutableList<ResultEpisode>>()

        when (response) {
            is AnimeLoadResponse -> {
                for ((dubStatus, episodeList) in response.episodes) {
                    val existingIds = HashSet<Int>()
                    for ((index, ep) in episodeList.withIndex()) {
                        val epNum = ep.episode ?: (index + 1)
                        val seasonNum = ep.season ?: 1
                        val epId = mediaId + epNum + dubStatus.id * 1_000_000 + (seasonNum * 10_000)

                        if (existingIds.add(epId)) {
                            val seasonData = response.seasonNames?.firstOrNull { it.season == ep.season }
                            val totalIndex = ep.season?.let { s ->
                                response.getTotalEpisodeIndex(epNum, s)
                            }
                            val resultEp = buildSingleEpisode(
                                response = response,
                                mediaId = mediaId,
                                id = epId,
                                name = ep.name,
                                poster = ep.posterUrl ?: response.posterUrl,
                                episode = epNum,
                                seasonIndex = ep.season,
                                season = seasonData?.displaySeason ?: ep.season,
                                data = ep.data,
                                index = index,
                                score = ep.score,
                                description = ep.description,
                                totalEpisodeIndex = totalIndex,
                                airDate = ep.date,
                                runTime = ep.runTime,
                                seasonData = seasonData
                            )
                            val indexer = EpisodeIndexer(dubStatus, seasonNum)
                            map.getOrPut(indexer) { mutableListOf() }.add(resultEp)
                        }
                    }
                }
            }

            is TvSeriesLoadResponse -> {
                val existingIds = HashSet<Int>()
                val sorted = response.episodes.sortedBy {
                    (it.season?.times(10_000) ?: 0) + (it.episode ?: 0)
                }

                for ((index, ep) in sorted.withIndex()) {
                    val epNum = ep.episode ?: (index + 1)
                    val seasonNum = ep.season ?: 1
                    val epId = mediaId + (seasonNum * 100_000) + epNum + 1

                    if (existingIds.add(epId)) {
                        val seasonData = response.seasonNames?.firstOrNull { it.season == ep.season }
                        val totalIndex = ep.season?.let { s ->
                            response.getTotalEpisodeIndex(epNum, s)
                        }
                        val resultEp = buildSingleEpisode(
                            response = response,
                            mediaId = mediaId,
                            id = epId,
                            name = ep.name,
                            poster = ep.posterUrl ?: response.posterUrl,
                            episode = epNum,
                            seasonIndex = ep.season,
                            season = seasonData?.displaySeason ?: ep.season,
                            data = ep.data,
                            index = index,
                            score = ep.score,
                            description = ep.description,
                            totalEpisodeIndex = totalIndex,
                            airDate = ep.date,
                            runTime = ep.runTime,
                            seasonData = seasonData
                        )
                        val indexer = EpisodeIndexer(DubStatus.None, seasonNum)
                        map.getOrPut(indexer) { mutableListOf() }.add(resultEp)
                    }
                }
            }

            is MovieLoadResponse -> {
                val movieEp = buildSingleEpisode(
                    response = response,
                    mediaId = mediaId,
                    data = response.dataUrl,
                    runTime = response.duration?.times(60)
                )
                map[EpisodeIndexer(DubStatus.None, 0)] = mutableListOf(movieEp)
            }

            is LiveStreamLoadResponse -> {
                val streamEp = buildSingleEpisode(
                    response = response,
                    mediaId = mediaId,
                    data = response.dataUrl
                )
                map[EpisodeIndexer(DubStatus.None, 0)] = mutableListOf(streamEp)
            }

            is TorrentLoadResponse -> {
                val torrentEp = buildSingleEpisode(
                    response = response,
                    mediaId = mediaId,
                    data = response.torrent ?: response.magnet ?: "",
                    runTime = response.duration?.times(60)
                )
                map[EpisodeIndexer(DubStatus.None, 0)] = mutableListOf(torrentEp)
            }

            else -> {
                val fallbackEp = buildSingleEpisode(
                    response = response,
                    mediaId = mediaId,
                    data = response.url,
                    runTime = response.duration?.times(60)
                )
                map[EpisodeIndexer(DubStatus.None, 0)] = mutableListOf(fallbackEp)
            }
        }

        return map
    }

    /**
     * Reactively observes Room KMP flows for this media item.
     */
    private fun observeRepositories(mediaId: Int) {
        launchSafeJob(key = "observe_persistence") {
            // Bookmark Flow
            launch {
                bookmarkRepository.getBookmarkFlow(accountId, mediaId).collect { bookmark ->
                    updateState {
                        copy(
                            isBookmarked = bookmark != null && bookmark.watchType > 0,
                            bookmarkWatchType = bookmark?.watchType ?: 0
                        )
                    }
                }
            }

            // Favorite Flow
            launch {
                favoriteRepository.getFavoriteFlow(accountId, mediaId).collect { favorite ->
                    updateState {
                        copy(
                            isFavorite = favorite != null
                        )
                    }
                }
            }

            // Subscription Flow
            subscriptionRepository?.let { subRepo ->
                launch {
                    subRepo.getSubscriptionFlow(accountId, mediaId).collect { sub ->
                        updateState {
                            copy(
                                isSubscribed = sub != null
                            )
                        }
                    }
                }
            }

            // Resume Watching Flow
            resumeWatchingRepository?.let { resumeRepo ->
                launch {
                    resumeRepo.getResumeWatchingFlow(accountId, mediaId).collect { resume ->
                        val allEps = currentState.episodesByIndexer.values.flatten()
                        val lastEp = resume?.episodeId?.let { id -> allEps.firstOrNull { it.id == id } }
                            ?: (if (allEps.size == 1) allEps.firstOrNull() else null)
                        val progress = lastEp?.let { watchProgressRepository.getProgress(accountId, it.id) }
                            ?: watchProgressRepository.getProgress(accountId, mediaId)
                        val epWithProgress = lastEp?.let { ep ->
                            if (progress != null && (progress.position > 0 || progress.duration > 0 || progress.watchState > 0)) {
                                ep.copy(
                                    position = progress.position,
                                    duration = progress.duration,
                                    videoWatchState = progress.watchState
                                )
                            } else {
                                ep
                            }
                        }
                        updateState {
                            copy(
                                resumeWatching = resume,
                                lastWatchedEpisode = epWithProgress ?: lastWatchedEpisode,
                                selectedEpisode = if (selectedEpisode == null || selectedEpisode.id == epWithProgress?.id) (epWithProgress ?: selectedEpisode) else selectedEpisode,
                                lastWatchedProgress = progress ?: lastWatchedProgress
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Toggles bookmark state (sets to Watching (1) or deletes).
     */
    private fun toggleBookmark(watchType: Int?) {
        launch {
            val mediaId = currentState.mediaId ?: return@launch
            val targetType = watchType ?: (if (currentState.isBookmarked) 0 else 1)
            setBookmark(targetType)
        }
    }

    /**
     * Sets or deletes a bookmark in BookmarkRepository.
     */
    private fun setBookmark(watchType: Int) {
        launch {
            val mediaId = currentState.mediaId ?: return@launch
            val response = currentState.loadResponse
            val existing = bookmarkRepository.getBookmark(accountId, mediaId)
            val now = APIHolder.unixTimeMS

            if (watchType <= 0) {
                if (existing != null) {
                    bookmarkRepository.saveBookmark(existing.copy(watchType = 0, latestUpdatedTime = now))
                }
            } else if (existing != null) {
                bookmarkRepository.saveBookmark(existing.copy(watchType = watchType, latestUpdatedTime = now))
            } else if (response != null) {
                val entity = BookmarkEntity(
                    accountId = accountId,
                    id = mediaId,
                    name = response.name,
                    url = response.url,
                    apiName = response.apiName,
                    type = response.type,
                    posterUrl = response.posterUrl ?: response.backgroundPosterUrl,
                    year = response.year,
                    watchType = watchType,
                    bookmarkedTime = now,
                    latestUpdatedTime = now,
                    plot = response.plot,
                    score = response.score?.toDouble(10)
                )
                bookmarkRepository.saveBookmark(entity)
            }
        }
    }

    /**
     * Sets favorite status in FavoriteRepository.
     */
    private fun setFavorite(isFavorite: Boolean) {
        launch {
            val mediaId = currentState.mediaId ?: return@launch
            val response = currentState.loadResponse

            if (!isFavorite) {
                favoriteRepository.deleteFavorite(accountId, mediaId)
            } else if (response != null) {
                val entity = FavoriteEntity(
                    accountId = accountId,
                    id = mediaId,
                    name = response.name,
                    url = response.url,
                    apiName = response.apiName,
                    type = response.type,
                    posterUrl = response.posterUrl ?: response.backgroundPosterUrl,
                    favoritesTime = APIHolder.unixTimeMS
                )
                favoriteRepository.saveFavorite(entity)
            }
        }
    }

    /**
     * Sets subscription status in SubscriptionRepository.
     */
    private fun setSubscription(isSubscribed: Boolean) {
        launch {
            val subRepo = subscriptionRepository ?: return@launch
            val mediaId = currentState.mediaId ?: return@launch
            val response = currentState.loadResponse

            if (!isSubscribed) {
                subRepo.deleteSubscription(accountId, mediaId)
            } else if (response != null) {
                val entity = SubscriptionEntity(
                    accountId = accountId,
                    id = mediaId,
                    name = response.name,
                    url = response.url,
                    apiName = response.apiName,
                    type = response.type,
                    posterUrl = response.posterUrl ?: response.backgroundPosterUrl,
                    year = response.year,
                    latestUpdatedTime = APIHolder.unixTimeMS
                )
                subRepo.saveSubscription(entity)
            }
        }
    }

    /**
     * Sets the watch state of an episode.
     */
    private fun setWatchState(episodeId: Int, watchState: Int) {
        launch {
            val ep = currentState.episodesByIndexer.values.flatten().firstOrNull { it.id == episodeId }
            val pos = if (watchState == 2 && ep != null && ep.duration > 0) ep.duration else ep?.position ?: 0L
            val dur = ep?.duration ?: 0L
            updateWatchProgress(episodeId, pos, dur, watchState)
        }
    }

    /**
     * Pure state transition helper that updates episodes matching [matchingIds] with new positions,
     * durations, and watch states, while maintaining consistency across filtered episode lists,
     * selection states, last watched references, watch progress entities, and external sync counters.
     */
    private fun ResultState.updateEpisodesWatchState(
        matchingIds: Set<Int>,
        watchState: Int,
        resolveProgress: (ResultEpisode) -> Pair<Long, Long>,
        newLastWatchedProgress: WatchProgressEntity? = null
    ): ResultState {
        val updatedMap = episodesByIndexer.mapValues { (_, eps) ->
            eps.map { ep ->
                if (ep.id in matchingIds) {
                    val (pos, dur) = resolveProgress(ep)
                    ep.copy(
                        position = pos,
                        duration = dur,
                        videoWatchState = watchState
                    )
                } else ep
            }
        }

        val currentSelectedDub = selectedDubStatus
        val currentSelectedSeason = selectedSeason ?: 0
        val updatedFiltered = updatedMap[EpisodeIndexer(currentSelectedDub, currentSelectedSeason)]
            ?: updatedMap.entries.firstOrNull { it.key.season == currentSelectedSeason }?.value
            ?: updatedMap.values.firstOrNull()
            ?: emptyList()

        val currentSelectedEp = if (selectedEpisode != null && selectedEpisode.id in matchingIds) {
            val (pos, dur) = resolveProgress(selectedEpisode)
            selectedEpisode.copy(
                position = pos,
                duration = dur,
                videoWatchState = watchState
            )
        } else selectedEpisode

        val lastWatched = if (lastWatchedEpisode != null && lastWatchedEpisode.id in matchingIds) {
            val (pos, dur) = resolveProgress(lastWatchedEpisode)
            lastWatchedEpisode.copy(
                position = pos,
                duration = dur,
                videoWatchState = watchState
            )
        } else lastWatchedEpisode

        val updatedAllEps = updatedMap.values.flatten()
        val newWatchedCount = updatedAllEps.count { it.isWatched || it.videoWatchState == 2 }
        val updatedSyncStates = externalSyncStates.mapValues { (_, entry) ->
            if (entry.isLinked || entry.hasTracking) {
                val newEpCount = maxOf(entry.watchedEpisodes, newWatchedCount)
                val newStatus = if (entry.maxEpisodes != null && newEpCount >= entry.maxEpisodes && entry.maxEpisodes > 0) {
                    ExternalSyncStatus.Completed
                } else if (newEpCount > 0 && entry.status == ExternalSyncStatus.PlanToWatch) {
                    ExternalSyncStatus.Watching
                } else {
                    entry.status
                }
                entry.copy(watchedEpisodes = newEpCount, status = newStatus)
            } else {
                entry
            }
        }

        return copy(
            episodesByIndexer = updatedMap,
            episodes = updatedFiltered,
            selectedEpisode = currentSelectedEp,
            lastWatchedEpisode = lastWatched,
            lastWatchedProgress = newLastWatchedProgress ?: lastWatchedProgress,
            externalSyncStates = updatedSyncStates
        )
    }

    /**
     * Updates playback progress and watch state for an episode.
     */
    private fun updateWatchProgress(
        episodeId: Int,
        position: Long,
        duration: Long,
        watchState: Int
    ) {
        launch {
            watchProgressRepository.setProgress(
                accountId = accountId,
                mediaId = episodeId,
                position = position,
                duration = duration,
                watchState = watchState
            )

            val updatedProgress = WatchProgressEntity(
                accountId = accountId,
                mediaId = episodeId,
                position = position,
                duration = duration,
                watchState = watchState,
                lastUpdated = APIHolder.unixTimeMS
            )

            updateState {
                updateEpisodesWatchState(
                    matchingIds = setOf(episodeId),
                    watchState = watchState,
                    resolveProgress = { position to duration },
                    newLastWatchedProgress = updatedProgress
                )
            }

            // Update resume watching parent state and bookmark metadata
            val currentEp = currentState.episodesByIndexer.values.flatten().firstOrNull { it.id == episodeId }
            val parentId = currentState.mediaId ?: currentEp?.parentId ?: episodeId
            val now = APIHolder.unixTimeMS

            resumeWatchingRepository?.saveResumeWatching(
                ResumeWatchingEntity(
                    accountId = accountId,
                    parentId = parentId,
                    episodeId = episodeId,
                    episode = currentEp?.episode,
                    season = currentEp?.seasonIndex ?: currentEp?.season,
                    isFromDownload = false,
                    updateTime = now
                )
            )

            val response = currentState.loadResponse
            val existingBookmark = bookmarkRepository.getBookmark(accountId, parentId)
            if (existingBookmark != null) {
                bookmarkRepository.saveBookmark(existingBookmark.copy(latestUpdatedTime = now))
            } else if (response != null) {
                val newBookmark = BookmarkEntity(
                    accountId = accountId,
                    id = parentId,
                    name = response.name,
                    url = response.url,
                    apiName = response.apiName,
                    type = response.type,
                    posterUrl = response.posterUrl ?: response.backgroundPosterUrl,
                    year = response.year,
                    watchType = 0,
                    bookmarkedTime = now,
                    latestUpdatedTime = now,
                    plot = response.plot,
                    score = response.score?.toDouble(10)
                )
                bookmarkRepository.saveBookmark(newBookmark)
            } else if (currentEp != null) {
                val newBookmark = BookmarkEntity(
                    accountId = accountId,
                    id = parentId,
                    name = currentEp.name ?: currentEp.headerName,
                    url = currentState.url ?: currentEp.data,
                    apiName = currentEp.apiName,
                    type = currentEp.tvType,
                    posterUrl = currentEp.poster,
                    watchType = 0,
                    bookmarkedTime = now,
                    latestUpdatedTime = now,
                    plot = currentEp.description
                )
                bookmarkRepository.saveBookmark(newBookmark)
            }
        }
    }

    /**
     * Marks all episodes from previous seasons and current season with index <= targetEp.index as watched.
     */
    private fun markEpisodesUpTo(episodeId: Int, season: Int) {
        launch {
            val allEpisodes = currentState.episodesByIndexer.values.flatten()
            val targetEp = allEpisodes.firstOrNull { it.id == episodeId }
            val targetSeason = targetEp?.season ?: season
            val targetIndex = targetEp?.index
                ?: allEpisodes.filter { (it.season ?: 0) == targetSeason }.indexOfFirst { it.id == episodeId }.takeIf { it >= 0 }
                ?: 0

            val episodesToMark = allEpisodes.filter { ep ->
                val epSeason = ep.season ?: 0
                epSeason < targetSeason || (epSeason == targetSeason && ep.index <= targetIndex)
            }

            episodesToMark.forEach { ep ->
                val duration = ep.duration
                val position = if (duration > 0) duration else 0L
                watchProgressRepository.setProgress(
                    accountId = accountId,
                    mediaId = ep.id,
                    position = position,
                    duration = duration,
                    watchState = 2
                )
            }

            val matchingIds = episodesToMark.map { it.id }.toSet()
            updateState {
                updateEpisodesWatchState(
                    matchingIds = matchingIds,
                    watchState = 2,
                    resolveProgress = { ep ->
                        val duration = ep.duration
                        val position = if (duration > 0) duration else ep.position
                        position to duration
                    }
                )
            }
        }
    }

    /**
     * Filters episodes by the selected season.
     */
    private fun selectSeason(season: Int) {
        updateState {
            val dub = selectedDubStatus
            val filtered = episodesByIndexer[EpisodeIndexer(dub, season)]
                ?: episodesByIndexer.entries.firstOrNull { it.key.season == season }?.value
                ?: emptyList()
            copy(
                selectedSeason = season,
                episodes = filtered
            )
        }
    }

    /**
     * Filters episodes by the selected anime dub status.
     */
    private fun selectDubStatus(dubStatus: DubStatus) {
        updateState {
            val season = selectedSeason ?: 0
            val filtered = episodesByIndexer[EpisodeIndexer(dubStatus, season)]
                ?: episodesByIndexer.entries.firstOrNull { it.key.dubStatus == dubStatus }?.value
                ?: emptyList()
            copy(
                selectedDubStatus = dubStatus,
                episodes = filtered
            )
        }
    }

    /**
     * Sets the currently selected episode and ensures exact progress and duration are reflected.
     */
    private fun selectEpisode(episode: ResultEpisode) {
        val existingEp = currentState.episodesByIndexer.values.flatten().firstOrNull { it.id == episode.id }
        val baseEp = if (existingEp != null && (existingEp.position > 0 || existingEp.duration > 0 || existingEp.videoWatchState > 0)) {
            episode.copy(
                position = existingEp.position,
                duration = existingEp.duration,
                videoWatchState = existingEp.videoWatchState
            )
        } else {
            episode
        }

        updateState {
            copy(
                selectedEpisode = baseEp,
                lastWatchedEpisode = baseEp
            )
        }

        launch {
            val progress = watchProgressRepository.getProgress(accountId, episode.id)

            if (progress != null) {
                val updatedEp = baseEp.copy(
                    position = progress.position,
                    duration = progress.duration,
                    videoWatchState = progress.watchState
                )
                updateState {
                    copy(
                        selectedEpisode = updatedEp,
                        lastWatchedEpisode = updatedEp,
                        lastWatchedProgress = progress
                    )
                }
            }
        }
    }

    /**
     * Extracts streaming links and subtitles for the specified or current episode/movie.
     */
    private fun reloadLinks(episode: ResultEpisode?, isCasting: Boolean, clearCache: Boolean = false) {
        launchSafeJob(key = "link_extraction") job@{
            val targetEp = episode ?: currentState.selectedEpisode ?: currentState.episodes.firstOrNull()
            updateState {
                val currentEpisodes = if (episodes.isEmpty() && targetEp != null) listOf(targetEp) else episodes
                copy(
                    isExtractingLinks = true,
                    linksLoadingProgress = 0,
                    linksLoadingError = null,
                    extractedLinks = if (clearCache || episode != null) emptyList() else extractedLinks,
                    extractedSubtitles = if (clearCache || episode != null) emptyList() else extractedSubtitles,
                    selectedEpisode = episode ?: selectedEpisode ?: targetEp,
                    episodes = currentEpisodes
                )
            }

            val apiName = currentState.apiName
            if (apiName == null) {
                val errorMessage = getString(Res.string.result_error_no_api_specified)
                updateState { copy(isExtractingLinks = false, linksLoadingError = errorMessage) }
                return@job
            }

            val api = APIHolder.getApiFromNameNull(apiName)
                ?: APIHolder.getApiFromUrlNull(currentState.url)
            if (api == null) {
                val errorMessage = getString(Res.string.result_error_provider_not_found, apiName)
                updateState {
                    copy(
                        isExtractingLinks = false,
                        linksLoadingError = errorMessage
                    )
                }
                return@job
            }

            val dataUrl = targetEp?.data
                ?: (currentState.loadResponse as? MovieLoadResponse)?.dataUrl
                ?: (currentState.loadResponse as? LiveStreamLoadResponse)?.dataUrl
                ?: (currentState.loadResponse as? TorrentLoadResponse)?.let { it.torrent ?: it.magnet }
                ?: currentState.url
                ?: ""

            if (dataUrl.isBlank()) {
                val errorMessage = getString(Res.string.result_error_no_stream_url)
                updateState {
                    copy(
                        isExtractingLinks = false,
                        linksLoadingError = errorMessage
                    )
                }
                return@job
            }

            try {
                val handledByApi = try {
                    api.loadLinks(
                        data = dataUrl,
                        isCasting = isCasting,
                        subtitleCallback = { sub ->
                            launch {
                                linksMutex.withLock {
                                    updateState {
                                        if (!extractedSubtitles.contains(sub)) {
                                            copy(extractedSubtitles = extractedSubtitles + sub)
                                        } else this
                                    }
                                }
                            }
                        },
                        callback = { link ->
                            launch {
                                linksMutex.withLock {
                                    updateState {
                                        if (!extractedLinks.contains(link)) {
                                            copy(
                                                extractedLinks = extractedLinks + link,
                                                linksLoadingProgress = linksLoadingProgress + 1
                                            )
                                        } else this
                                    }
                                }
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError(e)
                    false
                }

                // If not handled by api or if standard extractor url
                if (!handledByApi && (dataUrl.startsWith("http://") || dataUrl.startsWith("https://"))) {
                    try {
                        loadExtractor(
                            url = dataUrl,
                            subtitleCallback = { sub ->
                                launch {
                                    linksMutex.withLock {
                                        updateState {
                                            if (!extractedSubtitles.contains(sub)) {
                                                copy(extractedSubtitles = extractedSubtitles + sub)
                                            } else this
                                        }
                                    }
                                }
                            },
                            callback = { link ->
                                launch {
                                    linksMutex.withLock {
                                        updateState {
                                            if (!extractedLinks.contains(link)) {
                                                copy(
                                                    extractedLinks = extractedLinks + link,
                                                    linksLoadingProgress = linksLoadingProgress + 1
                                                )
                                            } else this
                                        }
                                    }
                                }
                            }
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                updateState { copy(isExtractingLinks = false) }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e)
                val errorMessage = e.message ?: getString(Res.string.result_error_extract_links_failed)
                updateState {
                    copy(
                        isExtractingLinks = false,
                        linksLoadingError = errorMessage
                    )
                }
            }
        }
    }

    /**
     * Clears all extracted links and subtitles.
     */
    private fun clearLinks() {
        cancelJob("link_extraction")
        updateState {
            copy(
                isExtractingLinks = false,
                extractedLinks = emptyList(),
                extractedSubtitles = emptyList(),
                linksLoadingProgress = 0,
                linksLoadingError = null
            )
        }
    }

    /**
     * Clears error messages.
     */
    private fun clearError(linksOnly: Boolean) {
        updateState {
            if (linksOnly) {
                copy(linksLoadingError = null)
            } else {
                copy(error = null, linksLoadingError = null)
            }
        }
    }

    /**
     * Resolves the primary URL of an episode and emits [ResultEffect.CopyToClipboard].
     */
    private fun copyEpisodeLink(episode: ResultEpisode) {
        launch {
            val api = APIHolder.getApiFromNameNull(episode.apiName)
                ?: APIHolder.getApiFromNameNull(currentState.apiName ?: "")
            val resolvedUrl = when {
                episode.data.startsWith("http://") || episode.data.startsWith("https://") || episode.data.startsWith("magnet:") -> episode.data
                api != null && episode.data.isNotBlank() -> {
                    val base = api.mainUrl.removeSuffix("/")
                    val path = if (episode.data.startsWith("/")) episode.data else "/${episode.data}"
                    "$base$path"
                }
                episode.data.isNotBlank() -> episode.data
                else -> currentState.url ?: ""
            }

            if (resolvedUrl.isNotBlank()) {
                val toastMsg = try {
                    getString(Res.string.copy_link_toast)
                } catch (e: Throwable) {
                    "Link copied to clipboard"
                }
                emitEffect(ResultEffect.CopyToClipboard(text = resolvedUrl, toastMessage = toastMsg))
            }
        }
    }

    /**
     * Updates tracking watch status for an external service.
     */
    private fun updateSyncStatus(service: SyncService, status: ExternalSyncStatus) {
        launch {
            val mediaId = currentState.mediaId
            val now = APIHolder.unixTimeMS
            updateState {
                val current = externalSyncStates[service] ?: ExternalSyncEntry(service)
                val newWatched = if (status == ExternalSyncStatus.Completed && current.maxEpisodes != null && current.maxEpisodes > 0) {
                    current.maxEpisodes
                } else current.watchedEpisodes

                val updatedEntry = current.copy(
                    status = status,
                    watchedEpisodes = newWatched,
                    isLinked = status != ExternalSyncStatus.None || !current.syncId.isNullOrBlank(),
                    lastUpdated = now
                )

                copy(
                    externalSyncStates = externalSyncStates + (service to updatedEntry)
                )
            }

            if (mediaId != null && syncMappingRepository != null) {
                val current = currentState.externalSyncStates[service]
                if (status != ExternalSyncStatus.None && current != null) {
                    val entity = SyncMappingEntity(
                        accountId = accountId,
                        mediaId = mediaId,
                        syncPrefix = service.idPrefix,
                        remoteUrl = current.syncId ?: service.idPrefix,
                        updatedAt = now
                    )
                    syncMappingRepository.saveSyncMapping(entity)
                }
            }
        }
    }

    /**
     * Updates user score for an external tracking service.
     */
    private fun updateSyncScore(service: SyncService, event: ResultEvent.UpdateSyncScore) {
        updateState {
            val current = externalSyncStates[service] ?: ExternalSyncEntry(service)
            val newScale = event.scale ?: current.scoreScale
            val effectiveScore = event.effectiveScore
            val updated = current.copy(
                score = effectiveScore?.toInt(10) ?: event.score?.coerceIn(1, 10),
                rawScore = effectiveScore,
                scoreScale = newScale,
                lastUpdated = APIHolder.unixTimeMS
            )
            copy(
                externalSyncStates = externalSyncStates + (service to updated)
            )
        }
    }

    /**
     * Sets the active score scale format for an external tracking service.
     */
    private fun setSyncScoreScale(service: SyncService, scale: TrackerScoreScale) {
        updateState {
            val current = externalSyncStates[service] ?: ExternalSyncEntry(service)
            val updated = current.copy(
                scoreScale = scale,
                lastUpdated = APIHolder.unixTimeMS
            )
            copy(
                externalSyncStates = externalSyncStates + (service to updated)
            )
        }
    }

    /**
     * Updates watched episodes count for an external tracking service.
     */
    private fun updateSyncEpisode(service: SyncService, episode: Int) {
        updateState {
            val current = externalSyncStates[service] ?: ExternalSyncEntry(service)
            val max = current.maxEpisodes ?: episodes.size.takeIf { it > 0 } ?: episodesByIndexer.values.flatten().size.takeIf { it > 0 }
            val coerced = if (max != null && max > 0) {
                episode.coerceIn(0, max)
            } else {
                episode.coerceAtLeast(0)
            }

            val autoStatus = if (max != null && coerced >= max && max > 0) {
                ExternalSyncStatus.Completed
            } else if (coerced > 0 && (current.status == ExternalSyncStatus.PlanToWatch || current.status == ExternalSyncStatus.None)) {
                ExternalSyncStatus.Watching
            } else current.status

            val updated = current.copy(
                watchedEpisodes = coerced,
                maxEpisodes = max ?: current.maxEpisodes,
                status = autoStatus,
                isLinked = current.isLinked || coerced > 0,
                lastUpdated = APIHolder.unixTimeMS
            )
            copy(
                externalSyncStates = externalSyncStates + (service to updated)
            )
        }
    }

    /**
     * Saves and commits all synchronization fields for a tracking service.
     */
    private fun saveSyncData(event: ResultEvent.SaveSyncData) {
        launch {
            val mediaId = currentState.mediaId
            val now = APIHolder.unixTimeMS
            val isLinked = event.status != ExternalSyncStatus.None || !event.syncId.isNullOrBlank()
            val effectiveScore = event.effectiveScore

            updateState {
                val current = externalSyncStates[event.service] ?: ExternalSyncEntry(event.service)
                val effectiveMax = event.maxEpisodes ?: current.maxEpisodes
                val updated = current.copy(
                    service = event.service,
                    syncId = event.syncId?.ifBlank { null } ?: current.syncId,
                    isLinked = isLinked,
                    status = event.status,
                    score = effectiveScore?.toInt(10) ?: event.score?.coerceIn(1, 10),
                    rawScore = effectiveScore,
                    scoreScale = event.scoreScale,
                    watchedEpisodes = event.watchedEpisodes.coerceAtLeast(0),
                    maxEpisodes = effectiveMax,
                    lastUpdated = now
                )
                copy(
                    externalSyncStates = externalSyncStates + (event.service to updated)
                )
            }

            if (mediaId != null && syncMappingRepository != null) {
                if (isLinked) {
                    val entity = SyncMappingEntity(
                        accountId = accountId,
                        mediaId = mediaId,
                        syncPrefix = event.service.idPrefix,
                        remoteUrl = event.syncId?.ifBlank { null } ?: event.service.idPrefix,
                        updatedAt = now
                    )
                    syncMappingRepository.saveSyncMapping(entity)
                }
            }
        }
    }

    /**
     * Unlinks tracking for a specific external service.
     */
    private fun unlinkSyncService(service: SyncService) {
        launch {
            val mediaId = currentState.mediaId
            updateState {
                val reset = ExternalSyncEntry(
                    service = service,
                    syncId = null,
                    isLinked = false,
                    status = ExternalSyncStatus.None,
                    score = null,
                    watchedEpisodes = 0,
                    maxEpisodes = null,
                    lastUpdated = 0L
                )
                copy(
                    externalSyncStates = externalSyncStates + (service to reset)
                )
            }

            if (mediaId != null && syncMappingRepository != null) {
                syncMappingRepository.deleteSyncMapping(accountId, mediaId, service.idPrefix)
            }
        }
    }

    /**
     * Opens the trailer modal dialog and triggers extraction of the trailer at [trailerIndex].
     */
    private fun openTrailer(trailerIndex: Int) {
        updateState {
            copy(
                isTrailerDialogOpen = true,
                selectedTrailerIndex = trailerIndex
            )
        }
        loadTrailer(trailerIndex)
    }

    /**
     * Extracts streaming links and subtitles for the trailer at [trailerIndex].
     */
    private fun loadTrailer(trailerIndex: Int) {
        launchSafeJob(key = "trailer_extraction") job@{
            val allTrailers = currentState.trailers.ifEmpty { currentState.loadResponse?.trailers ?: emptyList() }
            val targetTrailer = allTrailers.getOrNull(trailerIndex)

            updateState {
                copy(
                    selectedTrailerIndex = trailerIndex,
                    isExtractingTrailer = true,
                    extractedTrailerLinks = emptyList(),
                    extractedTrailerSubtitles = emptyList(),
                    selectedTrailerQuality = null,
                    trailerExtractionError = null
                )
            }

            if (targetTrailer == null) {
                val errorMessage = getString(Res.string.result_error_no_trailer)
                updateState {
                    copy(
                        isExtractingTrailer = false,
                        trailerExtractionError = errorMessage
                    )
                }
                return@job
            }

            val trailerUrl = targetTrailer.extractorUrl
            val referer = targetTrailer.referer
            val raw = targetTrailer.raw
            val headers = targetTrailer.headers

            if (trailerUrl.isBlank()) {
                val errorMessage = getString(Res.string.result_error_invalid_trailer_url)
                updateState {
                    copy(
                        isExtractingTrailer = false,
                        trailerExtractionError = errorMessage
                    )
                }
                return@job
            }

            try {
                com.lagradost.cloudstream3.shared.services.TrailerService.extractTrailer(
                    trailer = targetTrailer,
                    subtitleCallback = { sub ->
                        updateState {
                            if (!extractedTrailerSubtitles.contains(sub)) {
                                copy(extractedTrailerSubtitles = extractedTrailerSubtitles + sub)
                            } else this
                        }
                    },
                    linkCallback = { link ->
                        updateState {
                            val updatedLinks = if (!extractedTrailerLinks.contains(link)) {
                                extractedTrailerLinks + link
                            } else extractedTrailerLinks
                            copy(
                                extractedTrailerLinks = updatedLinks,
                                selectedTrailerQuality = selectedTrailerQuality ?: link
                            )
                        }
                    }
                )

                val errorMessage = if (currentState.extractedTrailerLinks.isEmpty()) {
                    getString(Res.string.result_error_trailer_no_links)
                } else null
                updateState {
                    copy(
                        isExtractingTrailer = false,
                        trailerExtractionError = errorMessage
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e)
                val errorMessage = e.message ?: getString(Res.string.result_error_trailer_load_failed)
                updateState {
                    copy(
                        isExtractingTrailer = false,
                        trailerExtractionError = errorMessage
                    )
                }
            }
        }
    }

    /**
     * Closes trailer dialog, releases active trailer extraction job and clears extracted trailer links.
     */
    private fun closeTrailer() {
        cancelJob("trailer_extraction")
        updateState {
            copy(
                isTrailerDialogOpen = false,
                isExtractingTrailer = false,
                extractedTrailerLinks = emptyList(),
                extractedTrailerSubtitles = emptyList(),
                selectedTrailerQuality = null,
                trailerExtractionError = null
            )
        }
    }
}
