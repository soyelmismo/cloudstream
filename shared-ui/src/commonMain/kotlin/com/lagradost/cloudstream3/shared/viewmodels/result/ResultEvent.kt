package com.lagradost.cloudstream3.shared.viewmodels.result

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.shared.mvi.UiEvent

/**
 * Events/Intents for ResultViewModel in KMP MVI architecture.
 */
sealed interface ResultEvent : UiEvent {
    /**
     * Loads media details given a content URL and the provider apiName.
     */
    data class LoadResult(
        val url: String,
        val apiName: String,
        val restart: Boolean = false,
        val autoResume: Boolean = false
    ) : ResultEvent

    /**
     * Toggles bookmark status or changes to a specific watchType.
     * If watchType is null or current status is already that watchType, it toggles/removes.
     */
    data class ToggleBookmark(
        val watchType: Int? = null
    ) : ResultEvent

    /**
     * Explicitly sets the bookmark watch type (0=None, 1=Watching, 2=Completed, 3=On Hold, 4=Dropped, 5=Planned).
     */
    data class SetBookmark(
        val watchType: Int
    ) : ResultEvent

    /**
     * Toggles the favorite status for this media.
     */
    data object ToggleFavorite : ResultEvent

    /**
     * Explicitly sets the favorite status.
     */
    data class SetFavorite(
        val isFavorite: Boolean
    ) : ResultEvent

    /**
     * Toggles subscription notifications for series updates.
     */
    data object ToggleSubscription : ResultEvent

    /**
     * Explicitly sets subscription status.
     */
    data class SetSubscription(
        val isSubscribed: Boolean
    ) : ResultEvent

    /**
     * Sets the watch state of a specific episode (0=None, 1=Watching, 2=Watched).
     */
    data class SetWatchState(
        val episodeId: Int,
        val watchState: Int
    ) : ResultEvent

    /**
     * Updates the playback position and duration of an episode in milliseconds.
     */
    data class UpdateWatchProgress(
        val episodeId: Int,
        val position: Long,
        val duration: Long,
        val watchState: Int = 0
    ) : ResultEvent

    /**
     * Selects a season to filter and display episodes.
     */
    data class SelectSeason(
        val season: Int
    ) : ResultEvent

    /**
     * Selects a dub status (e.g. Subbed, Dubbed, None) for anime.
     */
    data class SelectDubStatus(
        val dubStatus: DubStatus
    ) : ResultEvent

    /**
     * Selects an episode for viewing or link extraction.
     */
    data class SelectEpisode(
        val episode: ResultEpisode
    ) : ResultEvent

    /**
     * Refreshes the media details from the provider.
     */
    data object Refresh : ResultEvent

    /**
     * Opens the episode context/action menu for a specific episode.
     */
    data class OpenEpisodeMenu(
        val episode: ResultEpisode
    ) : ResultEvent

    /**
     * Closes the episode context/action menu.
     */
    data object CloseEpisodeMenu : ResultEvent

    /**
     * Marks all episodes up to [episodeId] in season [season] (including previous seasons) as watched.
     */
    data class MarkEpisodesUpTo(
        val episodeId: Int,
        val season: Int
    ) : ResultEvent

    /**
     * Resolves the primary URL of [episode] and copies it to clipboard.
     */
    data class CopyEpisodeLink(
        val episode: ResultEpisode
    ) : ResultEvent

    /**
     * Reloads streaming links and subtitles for the specified or currently selected episode/movie.
     */
    data class ReloadLinks(
        val episode: ResultEpisode? = null,
        val isCasting: Boolean = false,
        val clearCache: Boolean = false
    ) : ResultEvent

    /**
     * Clears all currently extracted links and subtitles.
     */
    data object ClearLinks : ResultEvent

    /**
     * Clears error messages from the state.
     */
    data class ClearError(
        val linksOnly: Boolean = false
    ) : ResultEvent

    /**
     * Updates the sync status for a tracking service (Watching, Completed, Plan to watch, Paused, Dropped, None).
     */
    data class UpdateSyncStatus(
        val service: SyncService,
        val status: ExternalSyncStatus
    ) : ResultEvent

    /**
     * Events carrying a user score in both multi-scale ([rawScore]) and
     * legacy 0-10 integer ([score]) form. Centralizes the resolved-score logic.
     */
    interface SyncScoreEvent {
        val score: Int?
        val rawScore: Score?
    }

    /**
     * Updates the user score for a tracking service (supports multi-scale Score and legacy Int).
     */
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

    /**
     * Updates the active score scale format for a tracking service.
     */
    data class SetSyncScoreScale(
        val service: SyncService,
        val scale: TrackerScoreScale
    ) : ResultEvent

    /**
     * Updates the watched episode count for a tracking service.
     */
    data class UpdateSyncEpisode(
        val service: SyncService,
        val episode: Int
    ) : ResultEvent

    /**
     * Changes the currently active/viewed tracking service in the SyncDialog.
     */
    data class SelectSyncService(
        val service: SyncService
    ) : ResultEvent

    /**
     * Saves / commits all synchronization fields for a tracking service.
     */
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

    /**
     * Unlinks / resets tracking for a specific service.
     */
    data class UnlinkSyncService(
        val service: SyncService
    ) : ResultEvent

    /**
     * Opens the trailer dialog and triggers extraction of the selected trailer.
     */
    data class OpenTrailer(
        val trailerIndex: Int = 0
    ) : ResultEvent

    /**
     * Loads/extracts links for a specific trailer by index.
     */
    data class LoadTrailer(
        val trailerIndex: Int = 0
    ) : ResultEvent

    /**
     * Selects a specific stream quality for the trailer.
     */
    data class SelectTrailerQuality(
        val link: com.lagradost.cloudstream3.utils.ExtractorLink
    ) : ResultEvent

    /**
     * Closes the trailer dialog and resets extraction.
     */
    data object CloseTrailer : ResultEvent
}

/**
 * Resolved user score for [ResultEvent.SyncScoreEvent] carriers:
 * prefers the native multi-scale value, falling back to a conversion of the legacy 0-10 integer.
 * Single source of truth shared by [ResultEvent.UpdateSyncScore] and [ResultEvent.SaveSyncData].
 */
val ResultEvent.SyncScoreEvent.effectiveScore: Score?
    get() = rawScore ?: score?.let { Score.from10(it) }
