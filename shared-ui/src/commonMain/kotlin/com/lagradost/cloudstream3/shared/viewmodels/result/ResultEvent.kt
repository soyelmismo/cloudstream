package com.lagradost.cloudstream3.shared.viewmodels.result

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.utils.ExtractorLink

sealed interface ResultEvent : UiEvent {
    data class LoadResult(
        val url: String,
        val apiName: String,
        val restart: Boolean = false,
        val autoResume: Boolean = false
    ) : ResultEvent

    data class ToggleBookmark(
        val watchType: Int? = null
    ) : ResultEvent

    data class SetBookmark(
        val watchType: Int
    ) : ResultEvent

    data object ToggleFavorite : ResultEvent
    data class SetFavorite(val isFavorite: Boolean) : ResultEvent
    data object ToggleSubscription : ResultEvent
    data class SetSubscription(val isSubscribed: Boolean) : ResultEvent
    data class SetWatchState(val episodeId: Int, val watchState: Int) : ResultEvent
    data class UpdateWatchProgress(
        val episodeId: Int,
        val position: Long,
        val duration: Long,
        val watchState: Int = 0
    ) : ResultEvent

    data class SelectSeason(val season: Int) : ResultEvent
    data class SelectDubStatus(val dubStatus: DubStatus) : ResultEvent
    data class SelectEpisode(val episode: ResultEpisode) : ResultEvent
    data object Refresh : ResultEvent
    data class OpenEpisodeMenu(val episode: ResultEpisode) : ResultEvent
    data object CloseEpisodeMenu : ResultEvent
    data class MarkEpisodesUpTo(val episodeId: Int, val season: Int) : ResultEvent
    data class CopyEpisodeLink(val episode: ResultEpisode) : ResultEvent
    data class ReloadLinks(
        val episode: ResultEpisode? = null,
        val isCasting: Boolean = false,
        val clearCache: Boolean = false
    ) : ResultEvent

    data object ClearLinks : ResultEvent
    data class ClearError(val linksOnly: Boolean = false) : ResultEvent
    data class UpdateSyncStatus(val service: SyncService, val status: ExternalSyncStatus) : ResultEvent

    interface SyncScoreEvent {
        val score: Int?
        val rawScore: Score?
    }

    data class UpdateSyncScore(
        val service: SyncService,
        override val score: Int? = null,
        override val rawScore: Score? = null,
        val scale: TrackerScoreScale? = null
    ) : ResultEvent, SyncScoreEvent {
        constructor(service: SyncService, rawScore: Score?, scale: TrackerScoreScale? = null) : this(
            service = service,
            score = rawScore?.toInt(10),
            rawScore = rawScore,
            scale = scale
        )
    }

    data class SetSyncScoreScale(val service: SyncService, val scale: TrackerScoreScale) : ResultEvent
    data class UpdateSyncEpisode(val service: SyncService, val episode: Int) : ResultEvent
    data class SelectSyncService(val service: SyncService) : ResultEvent

    data class SaveSyncData(
        val service: SyncService,
        val syncId: String?,
        val status: ExternalSyncStatus,
        override val score: Int? = null,
        override val rawScore: Score? = null,
        val scoreScale: TrackerScoreScale = TrackerScoreScale.Point10Decimal,
        val watchedEpisodes: Int,
        val maxEpisodes: Int? = null
    ) : ResultEvent, SyncScoreEvent {
        constructor(
            service: SyncService,
            syncId: String?,
            status: ExternalSyncStatus,
            rawScore: Score?,
            scoreScale: TrackerScoreScale = TrackerScoreScale.Point10Decimal,
            watchedEpisodes: Int,
            maxEpisodes: Int? = null
        ) : this(
            service = service,
            syncId = syncId,
            status = status,
            score = rawScore?.toInt(10),
            rawScore = rawScore,
            scoreScale = scoreScale,
            watchedEpisodes = watchedEpisodes,
            maxEpisodes = maxEpisodes
        )
    }

    data class UnlinkSyncService(val service: SyncService) : ResultEvent
    data class OpenTrailer(val trailerIndex: Int = 0) : ResultEvent
    data class LoadTrailer(val trailerIndex: Int = 0) : ResultEvent
    data class SelectTrailerQuality(val link: ExtractorLink) : ResultEvent
    data object CloseTrailer : ResultEvent
}

val ResultEvent.SyncScoreEvent.effectiveScore: Score?
    get() = rawScore ?: score?.let { Score.from10(it) }
