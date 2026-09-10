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
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.copy_link_toast
import com.lagradost.cloudstream4.generated.resources.result_error_api_not_found
import com.lagradost.cloudstream4.generated.resources.result_error_extract_links_failed
import com.lagradost.cloudstream4.generated.resources.result_error_invalid_trailer_url
import com.lagradost.cloudstream4.generated.resources.result_error_load_details_failed
import com.lagradost.cloudstream4.generated.resources.result_error_no_api_specified
import com.lagradost.cloudstream4.generated.resources.result_error_no_stream_url
import com.lagradost.cloudstream4.generated.resources.result_error_no_trailer
import com.lagradost.cloudstream4.generated.resources.result_error_provider_not_found
import com.lagradost.cloudstream4.generated.resources.result_error_trailer_load_failed
import com.lagradost.cloudstream4.generated.resources.result_error_trailer_no_links
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
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

fun LoadResponse.getId(): Int {
    val mainUrl = APIHolder.getApiFromNameNull(apiName)?.mainUrl ?: ""
    return uniqueUrl.replace(mainUrl, "").replace("/", "").hashCode()
}

private data class ResolvedResumeState(
    val resumeData: ResumeWatchingEntity?,
    val lastWatchedEp: ResultEpisode?,
    val lastProgress: WatchProgressEntity?,
    val resolvedSelectedEp: ResultEpisode?
)

private data class InitialResultData(
    val mediaId: Int,
    val episodesMap: ImmutableMap<EpisodeIndexer, ImmutableList<ResultEpisode>>,
    val availableSeasons: ImmutableList<ResultSeason>,
    val availableDubStatuses: ImmutableList<DubStatus>,
    val initialSeason: Int,
    val initialDubStatus: DubStatus,
    val filteredEpisodes: ImmutableList<ResultEpisode>,
    val allEpisodes: List<ResultEpisode>,
    val resumeState: ResolvedResumeState,
    val bookmark: BookmarkEntity,
    val favorite: FavoriteEntity?,
    val subscription: SubscriptionEntity?,
    val initialSyncStates: ImmutableMap<SyncService, ExternalSyncEntry>
)

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
            is ResultEvent.LoadResult,
            is ResultEvent.Refresh,
            is ResultEvent.SelectSeason,
            is ResultEvent.SelectDubStatus,
            is ResultEvent.SelectEpisode,
            is ResultEvent.ClearError -> handleNavigationEvent(event)

            is ResultEvent.ToggleBookmark,
            is ResultEvent.SetBookmark,
            is ResultEvent.ToggleFavorite,
            is ResultEvent.SetFavorite,
            is ResultEvent.ToggleSubscription,
            is ResultEvent.SetSubscription -> handleLibraryEvent(event)

            is ResultEvent.SetWatchState,
            is ResultEvent.UpdateWatchProgress,
            is ResultEvent.OpenEpisodeMenu,
            is ResultEvent.CloseEpisodeMenu,
            is ResultEvent.MarkEpisodesUpTo,
            is ResultEvent.CopyEpisodeLink,
            is ResultEvent.ReloadLinks,
            is ResultEvent.ClearLinks -> handleEpisodeEvent(event)

            is ResultEvent.UpdateSyncStatus,
            is ResultEvent.UpdateSyncScore,
            is ResultEvent.SetSyncScoreScale,
            is ResultEvent.UpdateSyncEpisode,
            is ResultEvent.SelectSyncService,
            is ResultEvent.SaveSyncData,
            is ResultEvent.UnlinkSyncService -> handleSyncEvent(event)

            is ResultEvent.OpenTrailer,
            is ResultEvent.LoadTrailer,
            is ResultEvent.SelectTrailerQuality,
            is ResultEvent.CloseTrailer -> handleTrailerEvent(event)
        }
    }

    private fun handleNavigationEvent(event: ResultEvent) {
        when (event) {
            is ResultEvent.LoadResult -> loadResult(event.url, event.apiName, event.restart, event.autoResume)
            is ResultEvent.Refresh -> {
                val url = currentState.url ?: return
                val apiName = currentState.apiName ?: return
                loadResult(url, apiName, restart = true)
            }
            is ResultEvent.SelectSeason -> selectSeason(event.season)
            is ResultEvent.SelectDubStatus -> selectDubStatus(event.dubStatus)
            is ResultEvent.SelectEpisode -> selectEpisode(event.episode)
            is ResultEvent.ClearError -> clearError(event.linksOnly)
            else -> Unit
        }
    }

    private fun handleLibraryEvent(event: ResultEvent) {
        when (event) {
            is ResultEvent.ToggleBookmark -> toggleBookmark(event.watchType)
            is ResultEvent.SetBookmark -> setBookmark(event.watchType)
            is ResultEvent.ToggleFavorite -> setFavorite(!currentState.isFavorite)
            is ResultEvent.SetFavorite -> setFavorite(event.isFavorite)
            is ResultEvent.ToggleSubscription -> setSubscription(!currentState.isSubscribed)
            is ResultEvent.SetSubscription -> setSubscription(event.isSubscribed)
            else -> Unit
        }
    }

    private fun handleEpisodeEvent(event: ResultEvent) {
        when (event) {
            is ResultEvent.SetWatchState -> setWatchState(event.episodeId, event.watchState)
            is ResultEvent.UpdateWatchProgress -> updateWatchProgress(
                event.episodeId,
                event.position,
                event.duration,
                event.watchState
            )
            is ResultEvent.OpenEpisodeMenu -> updateState {
                copy(isEpisodeMenuOpen = true, selectedMenuEpisode = event.episode)
            }
            is ResultEvent.CloseEpisodeMenu -> updateState {
                copy(isEpisodeMenuOpen = false, selectedMenuEpisode = null)
            }
            is ResultEvent.MarkEpisodesUpTo -> markEpisodesUpTo(event.episodeId, event.season)
            is ResultEvent.CopyEpisodeLink -> copyEpisodeLink(event.episode)
            is ResultEvent.ReloadLinks -> reloadLinks(event.episode, event.isCasting, event.clearCache)
            is ResultEvent.ClearLinks -> clearLinks()
            else -> Unit
        }
    }

    private fun handleSyncEvent(event: ResultEvent) {
        when (event) {
            is ResultEvent.UpdateSyncStatus -> updateSyncStatus(event.service, event.status)
            is ResultEvent.UpdateSyncScore -> updateSyncScore(event.service, event)
            is ResultEvent.SetSyncScoreScale -> setSyncScoreScale(event.service, event.scale)
            is ResultEvent.UpdateSyncEpisode -> updateSyncEpisode(event.service, event.episode)
            is ResultEvent.SelectSyncService -> updateState { copy(selectedSyncService = event.service) }
            is ResultEvent.SaveSyncData -> saveSyncData(event)
            is ResultEvent.UnlinkSyncService -> unlinkSyncService(event.service)
            else -> Unit
        }
    }

    private fun handleTrailerEvent(event: ResultEvent) {
        when (event) {
            is ResultEvent.OpenTrailer -> openTrailer(event.trailerIndex)
            is ResultEvent.LoadTrailer -> loadTrailer(event.trailerIndex)
            is ResultEvent.SelectTrailerQuality -> updateState { copy(selectedTrailerQuality = event.link) }
            is ResultEvent.CloseTrailer -> closeTrailer()
            else -> Unit
        }
    }

    private fun buildAvailableSeasons(
        response: LoadResponse,
        episodesMap: Map<EpisodeIndexer, List<ResultEpisode>>,
        distinctSeasons: List<Int>
    ): ImmutableList<ResultSeason> {
        val seasonNames = (response as? EpisodeResponse)?.seasonNames
        return distinctSeasons.map { s ->
            val sData = seasonNames?.firstOrNull { it.season == s }
            val count = episodesMap.filterKeys { it.season == s }.values.flatten().size
            ResultSeason(
                season = s,
                name = sData?.name,
                displaySeason = sData?.displaySeason ?: s,
                episodeCount = count
            )
        }.toImmutableList()
    }

    private fun resolveInitialEpisodes(
        episodesMap: Map<EpisodeIndexer, List<ResultEpisode>>,
        initialDubStatus: DubStatus,
        initialSeason: Int
    ): ImmutableList<ResultEpisode> {
        return (episodesMap[EpisodeIndexer(initialDubStatus, initialSeason)]
            ?: episodesMap.entries.firstOrNull { it.key.season == initialSeason }?.value
            ?: episodesMap.entries.firstOrNull { it.key.dubStatus == initialDubStatus }?.value
            ?: episodesMap.values.firstOrNull { it.isNotEmpty() }
            ?: episodesMap.values.flatten()).toImmutableList()
    }

    private suspend fun resolveResumePosition(
        mediaId: Int,
        allEpisodes: List<ResultEpisode>
    ): ResolvedResumeState {
        val resumeData = resumeWatchingRepository?.getResumeWatching(accountId, mediaId)
        val lastWatchedEp = resumeData?.episodeId?.let { id -> allEpisodes.firstOrNull { it.id == id } }
            ?: allEpisodes.firstOrNull { it.position > 0 }
            ?: allEpisodes.firstOrNull()

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

        return ResolvedResumeState(resumeData, lastWatchedEp, lastProgress, resolvedSelectedEp)
    }

    private suspend fun ensureBookmarksExist(mediaId: Int, response: LoadResponse): BookmarkEntity {
        val existing = bookmarkRepository.getBookmark(accountId, mediaId)
        if (existing != null) return existing

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
        return newBookmark
    }

    private fun resolveResponseSyncId(service: SyncService, syncData: Map<String, String>): String? {
        return syncData[service.idPrefix]
            ?: syncData[service.serviceName.lowercase()]
            ?: syncData[service.name.lowercase()]
    }

    private fun resolveInitialSyncStatus(isLinked: Boolean, totalEpisodes: Int?, watchedCount: Int): ExternalSyncStatus {
        if (!isLinked) return ExternalSyncStatus.None
        if (totalEpisodes != null && watchedCount >= totalEpisodes) return ExternalSyncStatus.Completed
        if (watchedCount > 0) return ExternalSyncStatus.Watching
        return ExternalSyncStatus.PlanToWatch
    }

    private fun buildSyncEntry(
        service: SyncService,
        response: LoadResponse,
        storedMapping: SyncMappingEntity?,
        totalEpisodeCount: Int?,
        watchedCount: Int
    ): ExternalSyncEntry {
        val responseSyncId = resolveResponseSyncId(service, response.syncData)
        val effectiveId = storedMapping?.remoteUrl ?: responseSyncId
        val isLinked = !effectiveId.isNullOrBlank()
        val initialStatus = resolveInitialSyncStatus(isLinked, totalEpisodeCount, watchedCount)

        return ExternalSyncEntry(
            service = service,
            syncId = effectiveId,
            isLinked = isLinked,
            status = initialStatus,
            score = response.score?.toInt(10)?.coerceIn(1, 10),
            rawScore = response.score,
            scoreScale = service.defaultScale,
            watchedEpisodes = watchedCount,
            maxEpisodes = totalEpisodeCount,
            lastUpdated = storedMapping?.updatedAt ?: 0L
        )
    }

    private suspend fun initializeSyncTrackers(
        mediaId: Int,
        response: LoadResponse,
        totalEpisodeCount: Int?,
        watchedCount: Int
    ): ImmutableMap<SyncService, ExternalSyncEntry> {
        val storedMappings = syncMappingRepository?.getSyncMappings(accountId, mediaId) ?: emptyList()
        return SyncService.entries.associateWith { service ->
            val storedMapping = storedMappings.firstOrNull { it.syncPrefix.equals(service.idPrefix, ignoreCase = true) }
            buildSyncEntry(service, response, storedMapping, totalEpisodeCount, watchedCount)
        }.toImmutableMap()
    }

    private suspend fun handleAutoResume(
        mediaId: Int,
        lastWatchedEp: ResultEpisode?,
        resolvedSelectedEp: ResultEpisode?,
        allEpisodes: List<ResultEpisode>,
        lastProgress: WatchProgressEntity?
    ) {
        val targetEp = lastWatchedEp ?: resolvedSelectedEp ?: allEpisodes.firstOrNull() ?: return
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

    private suspend fun fetchLoadResponse(url: String, apiName: String): LoadResponse? {
        val api = APIHolder.getApiFromNameNull(apiName) ?: APIHolder.getApiFromUrlNull(url) ?: return null
        return try {
            api.load(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private suspend fun resolveInitialResultData(response: LoadResponse): InitialResultData {
        val mediaId = response.getId()
        val episodesMap = buildEpisodesMap(response, mediaId)
        val distinctSeasons = episodesMap.keys.map { it.season }.distinct().sorted()
        val availableSeasons = buildAvailableSeasons(response, episodesMap, distinctSeasons)

        val availableDubStatuses = episodesMap.keys.map { it.dubStatus }.distinct().toImmutableList()
        val initialDubStatus = availableDubStatuses.firstOrNull { it == DubStatus.Subbed }
            ?: availableDubStatuses.firstOrNull()
            ?: DubStatus.None
        val initialSeason = distinctSeasons.firstOrNull { it > 0 } ?: distinctSeasons.firstOrNull() ?: 0
        val filteredEpisodes = resolveInitialEpisodes(episodesMap, initialDubStatus, initialSeason)

        val allEps = episodesMap.values.flatten()
        val resumeState = resolveResumePosition(mediaId, allEps)
        val bookmark = ensureBookmarksExist(mediaId, response)
        val favorite = favoriteRepository.getFavorite(accountId, mediaId)
        val subscription = subscriptionRepository?.getSubscription(accountId, mediaId)

        val totalEpisodeCount: Int? = if (response.isEpisodeBased()) {
            allEps.size.takeIf { it > 0 }
        } else null
        val watchedCount = allEps.count { it.isWatched || it.videoWatchState == 2 }
        val initialSyncStates = initializeSyncTrackers(mediaId, response, totalEpisodeCount, watchedCount)

        return InitialResultData(
            mediaId = mediaId,
            episodesMap = episodesMap,
            availableSeasons = availableSeasons,
            availableDubStatuses = availableDubStatuses,
            initialSeason = initialSeason,
            initialDubStatus = initialDubStatus,
            filteredEpisodes = filteredEpisodes,
            allEpisodes = allEps,
            resumeState = resumeState,
            bookmark = bookmark,
            favorite = favorite,
            subscription = subscription,
            initialSyncStates = initialSyncStates
        )
    }

    private fun applyLoadedResultState(
        url: String,
        apiName: String,
        response: LoadResponse,
        data: InitialResultData
    ) {
        updateState {
            copy(
                isLoading = false,
                error = null,
                url = url,
                apiName = apiName,
                mediaId = data.mediaId,
                loadResponse = response,
                title = response.name,
                synopsis = response.plot,
                posterUrl = response.posterUrl,
                backgroundPosterUrl = response.backgroundPosterUrl,
                logoUrl = response.logoUrl,
                year = response.year,
                rating = response.score,
                tags = response.tags?.toImmutableList() ?: persistentListOf(),
                actors = response.actors?.toImmutableList() ?: persistentListOf(),
                tvType = response.type,
                duration = response.duration,
                comingSoon = response.comingSoon,
                showStatus = (response as? EpisodeResponse)?.showStatus,
                contentRating = response.contentRating,
                trailers = response.trailers.toImmutableList(),
                recommendations = response.recommendations?.toImmutableList() ?: persistentListOf(),
                syncData = response.syncData.toImmutableMap(),
                posterHeaders = response.posterHeaders?.toImmutableMap(),
                isMovie = response.isMovie(),
                isAnime = response.isAnimeBased(),
                isEpisodeBased = response.isEpisodeBased(),
                availableSeasons = data.availableSeasons,
                availableDubStatuses = data.availableDubStatuses,
                selectedSeason = data.initialSeason,
                selectedDubStatus = data.initialDubStatus,
                episodesByIndexer = data.episodesMap,
                episodes = data.filteredEpisodes,
                selectedEpisode = data.resumeState.resolvedSelectedEp,
                isBookmarked = data.bookmark.watchType > 0,
                bookmarkWatchType = data.bookmark.watchType,
                isFavorite = data.favorite != null,
                isSubscribed = data.subscription != null,
                lastWatchedEpisode = data.resumeState.resolvedSelectedEp,
                lastWatchedProgress = data.resumeState.lastProgress,
                resumeWatching = data.resumeState.resumeData,
                externalSyncStates = data.initialSyncStates
            )
        }
    }

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
                val response = fetchLoadResponse(url, apiName)
                if (response == null) {
                    val isProviderMissing = APIHolder.getApiFromNameNull(apiName) == null && APIHolder.getApiFromUrlNull(url) == null
                    val errorRes = if (isProviderMissing) {
                        txt(Res.string.result_error_api_not_found, apiName)
                    } else {
                        txt(Res.string.result_error_load_details_failed)
                    }
                    updateState {
                        copy(
                            isLoading = false,
                            error = errorRes
                        )
                    }
                    return@job
                }

                val data = resolveInitialResultData(response)
                applyLoadedResultState(url, apiName, response, data)
                observeRepositories(data.mediaId)

                if (autoResume) {
                    handleAutoResume(
                        mediaId = data.mediaId,
                        lastWatchedEp = data.resumeState.lastWatchedEp,
                        resolvedSelectedEp = data.resumeState.resolvedSelectedEp,
                        allEpisodes = data.allEpisodes,
                        lastProgress = data.resumeState.lastProgress
                    )
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

    private suspend fun parseAnimeEpisodes(
        response: AnimeLoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val map = mutableMapOf<EpisodeIndexer, MutableList<ResultEpisode>>()
        for ((dubStatus, episodeList) in response.episodes) {
            val existingIds = HashSet<Int>()
            for ((index, ep) in episodeList.withIndex()) {
                val epNum = ep.episode ?: (index + 1)
                val seasonNum = ep.season ?: 1
                val epId = mediaId + epNum + dubStatus.id * 1_000_000 + (seasonNum * 10_000)

                if (existingIds.add(epId)) {
                    val seasonData = response.seasonNames?.firstOrNull { it.season == ep.season }
                    val totalIndex = ep.season?.let { s -> response.getTotalEpisodeIndex(epNum, s) }
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
        return map
    }

    private suspend fun parseTvEpisodes(
        response: TvSeriesLoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val map = mutableMapOf<EpisodeIndexer, MutableList<ResultEpisode>>()
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
                val totalIndex = ep.season?.let { s -> response.getTotalEpisodeIndex(epNum, s) }
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
        return map
    }

    private suspend fun parseMovieEpisode(
        response: MovieLoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val movieEp = buildSingleEpisode(
            response = response,
            mediaId = mediaId,
            data = response.dataUrl,
            runTime = response.duration?.times(60)
        )
        return mapOf(EpisodeIndexer(DubStatus.None, 0) to listOf(movieEp))
    }

    private suspend fun parseLiveStreamEpisode(
        response: LiveStreamLoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val streamEp = buildSingleEpisode(
            response = response,
            mediaId = mediaId,
            data = response.dataUrl
        )
        return mapOf(EpisodeIndexer(DubStatus.None, 0) to listOf(streamEp))
    }

    private suspend fun parseTorrentEpisode(
        response: TorrentLoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val torrentEp = buildSingleEpisode(
            response = response,
            mediaId = mediaId,
            data = response.torrent ?: response.magnet ?: "",
            runTime = response.duration?.times(60)
        )
        return mapOf(EpisodeIndexer(DubStatus.None, 0) to listOf(torrentEp))
    }

    private suspend fun parseFallbackEpisode(
        response: LoadResponse,
        mediaId: Int
    ): Map<EpisodeIndexer, List<ResultEpisode>> {
        val fallbackEp = buildSingleEpisode(
            response = response,
            mediaId = mediaId,
            data = response.url,
            runTime = response.duration?.times(60)
        )
        return mapOf(EpisodeIndexer(DubStatus.None, 0) to listOf(fallbackEp))
    }

    private suspend fun buildEpisodesMap(
        response: LoadResponse,
        mediaId: Int
    ): ImmutableMap<EpisodeIndexer, ImmutableList<ResultEpisode>> {
        val rawMap = when (response) {
            is AnimeLoadResponse -> parseAnimeEpisodes(response, mediaId)
            is TvSeriesLoadResponse -> parseTvEpisodes(response, mediaId)
            is MovieLoadResponse -> parseMovieEpisode(response, mediaId)
            is LiveStreamLoadResponse -> parseLiveStreamEpisode(response, mediaId)
            is TorrentLoadResponse -> parseTorrentEpisode(response, mediaId)
            else -> parseFallbackEpisode(response, mediaId)
        }

        return rawMap.mapValues { it.value.toImmutableList() }.toImmutableMap()
    }

    private suspend fun resolveResumeStateForEpisode(
        resume: ResumeWatchingEntity?,
        mediaId: Int
    ): Triple<ResultEpisode?, ResultEpisode?, WatchProgressEntity?> {
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
        return Triple(lastEp, epWithProgress, progress)
    }

    private fun observeRepositories(mediaId: Int) {
        launchSafeJob(key = "observe_persistence") {
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

            launch {
                favoriteRepository.getFavoriteFlow(accountId, mediaId).collect { favorite ->
                    updateState {
                        copy(
                            isFavorite = favorite != null
                        )
                    }
                }
            }

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

            resumeWatchingRepository?.let { resumeRepo ->
                launch {
                    resumeRepo.getResumeWatchingFlow(accountId, mediaId).collect { resume ->
                        val (_, epWithProgress, progress) = resolveResumeStateForEpisode(resume, mediaId)
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

    private fun toggleBookmark(watchType: Int?) {
        launch {
            val mediaId = currentState.mediaId ?: return@launch
            val targetType = watchType ?: (if (currentState.isBookmarked) 0 else 1)
            setBookmark(targetType)
        }
    }

    private fun setBookmark(watchType: Int) {
        launch {
            val mediaId = currentState.mediaId ?: return@launch
            val response = currentState.loadResponse
            val existing = bookmarkRepository.getBookmark(accountId, mediaId)
            val now = APIHolder.unixTimeMS

            if (existing != null) {
                val targetWatchType = if (watchType <= 0) 0 else watchType
                bookmarkRepository.saveBookmark(existing.copy(watchType = targetWatchType, latestUpdatedTime = now))
                return@launch
            }

            if (response != null && watchType > 0) {
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

    private fun setWatchState(episodeId: Int, watchState: Int) {
        launch {
            val ep = currentState.episodesByIndexer.values.flatten().firstOrNull { it.id == episodeId }
            val pos = if (watchState == 2 && ep != null && ep.duration > 0) ep.duration else ep?.position ?: 0L
            val dur = ep?.duration ?: 0L
            updateWatchProgress(episodeId, pos, dur, watchState)
        }
    }

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
            }.toImmutableList()
        }.toImmutableMap()

        val currentSelectedDub = selectedDubStatus
        val currentSelectedSeason = selectedSeason ?: 0
        val updatedFiltered = (updatedMap[EpisodeIndexer(currentSelectedDub, currentSelectedSeason)]
            ?: updatedMap.entries.firstOrNull { it.key.season == currentSelectedSeason }?.value
            ?: updatedMap.values.firstOrNull()
            ?: persistentListOf()).toImmutableList()

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
        }.toImmutableMap()

        return copy(
            episodesByIndexer = updatedMap,
            episodes = updatedFiltered,
            selectedEpisode = currentSelectedEp,
            lastWatchedEpisode = lastWatched,
            lastWatchedProgress = newLastWatchedProgress ?: lastWatchedProgress,
            externalSyncStates = updatedSyncStates
        )
    }

    private suspend fun ensureBookmarkUpdated(currentEp: ResultEpisode?, parentId: Int, now: Long) {
        val response = currentState.loadResponse
        val existingBookmark = bookmarkRepository.getBookmark(accountId, parentId)
        if (existingBookmark != null) {
            bookmarkRepository.saveBookmark(existingBookmark.copy(latestUpdatedTime = now))
            return
        }

        if (response != null) {
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

            ensureBookmarkUpdated(currentEp, parentId, now)
        }
    }

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

    private fun selectSeason(season: Int) {
        updateState {
            val dub = selectedDubStatus
            val filtered = (episodesByIndexer[EpisodeIndexer(dub, season)]
                ?: episodesByIndexer.entries.firstOrNull { it.key.season == season }?.value
                ?: persistentListOf()).toImmutableList()
            copy(
                selectedSeason = season,
                episodes = filtered
            )
        }
    }

    private fun selectDubStatus(dubStatus: DubStatus) {
        updateState {
            val season = selectedSeason ?: 0
            val filtered = (episodesByIndexer[EpisodeIndexer(dubStatus, season)]
                ?: episodesByIndexer.entries.firstOrNull { it.key.dubStatus == dubStatus }?.value
                ?: persistentListOf()).toImmutableList()
            copy(
                selectedDubStatus = dubStatus,
                episodes = filtered
            )
        }
    }

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

    private fun resolveStreamDataUrl(targetEp: ResultEpisode?): String {
        return targetEp?.data
            ?: (currentState.loadResponse as? MovieLoadResponse)?.dataUrl
            ?: (currentState.loadResponse as? LiveStreamLoadResponse)?.dataUrl
            ?: (currentState.loadResponse as? TorrentLoadResponse)?.let { it.torrent ?: it.magnet }
            ?: currentState.url
            ?: ""
    }

    private suspend fun extractLinksFallback(dataUrl: String) {
        if (!dataUrl.startsWith("http://") && !dataUrl.startsWith("https://")) return
        try {
            loadExtractor(
                url = dataUrl,
                subtitleCallback = { sub ->
                    launch {
                        linksMutex.withLock {
                            updateState {
                                if (!extractedSubtitles.contains(sub)) {
                                    copy(extractedSubtitles = (extractedSubtitles + sub).toImmutableList())
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
                                        extractedLinks = (extractedLinks + link).toImmutableList(),
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

    private fun reloadLinks(episode: ResultEpisode?, isCasting: Boolean, clearCache: Boolean = false) {
        launchSafeJob(key = "link_extraction") job@{
            val targetEp = episode ?: currentState.selectedEpisode ?: currentState.episodes.firstOrNull()
            updateState {
                val currentEpisodes = (if (episodes.isEmpty() && targetEp != null) listOf(targetEp) else episodes).toImmutableList()
                copy(
                    isExtractingLinks = true,
                    linksLoadingProgress = 0,
                    linksLoadingError = null,
                    extractedLinks = if (clearCache || episode != null) persistentListOf() else extractedLinks,
                    extractedSubtitles = if (clearCache || episode != null) persistentListOf() else extractedSubtitles,
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

            val dataUrl = resolveStreamDataUrl(targetEp)
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
                                            copy(extractedSubtitles = (extractedSubtitles + sub).toImmutableList())
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
                                                extractedLinks = (extractedLinks + link).toImmutableList(),
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

                if (!handledByApi) {
                    extractLinksFallback(dataUrl)
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

    private fun clearLinks() {
        cancelJob("link_extraction")
        updateState {
            copy(
                isExtractingLinks = false,
                extractedLinks = persistentListOf(),
                extractedSubtitles = persistentListOf(),
                linksLoadingProgress = 0,
                linksLoadingError = null
            )
        }
    }

    private fun clearError(linksOnly: Boolean) {
        updateState {
            if (linksOnly) {
                copy(linksLoadingError = null)
            } else {
                copy(error = null, linksLoadingError = null)
            }
        }
    }

    private fun resolveEpisodeUrl(episode: ResultEpisode): String {
        if (episode.data.startsWith("http://") || episode.data.startsWith("https://") || episode.data.startsWith("magnet:")) {
            return episode.data
        }
        val api = APIHolder.getApiFromNameNull(episode.apiName)
            ?: APIHolder.getApiFromNameNull(currentState.apiName ?: "")
        if (api != null && episode.data.isNotBlank()) {
            val base = api.mainUrl.removeSuffix("/")
            val path = if (episode.data.startsWith("/")) episode.data else "/${episode.data}"
            return "$base$path"
        }
        if (episode.data.isNotBlank()) {
            return episode.data
        }
        return currentState.url ?: ""
    }

    private fun copyEpisodeLink(episode: ResultEpisode) {
        launch {
            val resolvedUrl = resolveEpisodeUrl(episode)
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
                    externalSyncStates = (externalSyncStates + (service to updatedEntry)).toImmutableMap()
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
                externalSyncStates = (externalSyncStates + (service to updated)).toImmutableMap()
            )
        }
    }

    private fun setSyncScoreScale(service: SyncService, scale: TrackerScoreScale) {
        updateState {
            val current = externalSyncStates[service] ?: ExternalSyncEntry(service)
            val updated = current.copy(
                scoreScale = scale,
                lastUpdated = APIHolder.unixTimeMS
            )
            copy(
                externalSyncStates = (externalSyncStates + (service to updated)).toImmutableMap()
            )
        }
    }

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
                externalSyncStates = (externalSyncStates + (service to updated)).toImmutableMap()
            )
        }
    }

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
                    externalSyncStates = (externalSyncStates + (event.service to updated)).toImmutableMap()
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
                    externalSyncStates = (externalSyncStates + (service to reset)).toImmutableMap()
                )
            }

            if (mediaId != null && syncMappingRepository != null) {
                syncMappingRepository.deleteSyncMapping(accountId, mediaId, service.idPrefix)
            }
        }
    }

    private fun openTrailer(trailerIndex: Int) {
        updateState {
            copy(
                isTrailerDialogOpen = true,
                selectedTrailerIndex = trailerIndex
            )
        }
        loadTrailer(trailerIndex)
    }

    private fun loadTrailer(trailerIndex: Int) {
        launchSafeJob(key = "trailer_extraction") job@{
            val allTrailers = currentState.trailers.ifEmpty { currentState.loadResponse?.trailers?.toImmutableList() ?: persistentListOf() }
            val targetTrailer = allTrailers.getOrNull(trailerIndex)

            updateState {
                copy(
                    selectedTrailerIndex = trailerIndex,
                    isExtractingTrailer = true,
                    extractedTrailerLinks = persistentListOf(),
                    extractedTrailerSubtitles = persistentListOf(),
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
                                copy(extractedTrailerSubtitles = (extractedTrailerSubtitles + sub).toImmutableList())
                            } else this
                        }
                    },
                    linkCallback = { link ->
                        updateState {
                            val updatedLinks = if (!extractedTrailerLinks.contains(link)) {
                                (extractedTrailerLinks + link).toImmutableList()
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

    private fun closeTrailer() {
        cancelJob("trailer_extraction")
        updateState {
            copy(
                isTrailerDialogOpen = false,
                isExtractingTrailer = false,
                extractedTrailerLinks = persistentListOf(),
                extractedTrailerSubtitles = persistentListOf(),
                selectedTrailerQuality = null,
                trailerExtractionError = null
            )
        }
    }
}
