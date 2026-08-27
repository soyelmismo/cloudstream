package com.lagradost.cloudstream3.shared.viewmodels.result

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.ui.theme.AppColors
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import cloudstream.shared_ui.generated.resources.*
import kotlin.math.roundToInt

/**
 * Supported external tracking services.
 */
enum class SyncService(
    val syncIdName: SyncIdName,
    val serviceName: String,
    val idPrefix: String,
    val defaultUrlPrefix: String,
    val brandColor: Color
) {
    AniList(
        syncIdName = SyncIdName.Anilist,
        serviceName = "AniList",
        idPrefix = "anilist",
        defaultUrlPrefix = "https://anilist.co/anime/",
        brandColor = AppColors.BrandAniList
    ),
    MyAnimeList(
        syncIdName = SyncIdName.MyAnimeList,
        serviceName = "MyAnimeList",
        idPrefix = "mal",
        defaultUrlPrefix = "https://myanimelist.net/anime/",
        brandColor = AppColors.BrandMyAnimeList
    ),
    Trakt(
        syncIdName = SyncIdName.Trakt,
        serviceName = "Trakt",
        idPrefix = "trakt",
        defaultUrlPrefix = "https://trakt.tv/",
        brandColor = AppColors.BrandTrakt
    ),
    Simkl(
        syncIdName = SyncIdName.Simkl,
        serviceName = "Simkl",
        idPrefix = "simkl",
        defaultUrlPrefix = "https://simkl.com/anime/",
        brandColor = AppColors.BrandSimkl
    ),
    Kitsu(
        syncIdName = SyncIdName.Kitsu,
        serviceName = "Kitsu",
        idPrefix = "kitsu",
        defaultUrlPrefix = "https://kitsu.io/anime/",
        brandColor = AppColors.BrandKitsu
    );

    companion object {
        fun fromIdPrefix(prefix: String?): SyncService? {
            if (prefix == null) return null
            return entries.firstOrNull { it.idPrefix.equals(prefix, ignoreCase = true) }
        }

        fun fromSyncIdName(name: SyncIdName?): SyncService? {
            if (name == null) return null
            return entries.firstOrNull { it.syncIdName == name }
        }
    }
}

/**
 * Tracking status on an external service.
 */
enum class ExternalSyncStatus(
    val internalId: Int,
    val stringRes: StringResource,
    val color: Color
) {
    None(0, Res.string.sync_status_none, AppColors.SyncStatusNone),
    Watching(1, Res.string.sync_status_watching, AppColors.SyncStatusWatching),
    Completed(2, Res.string.sync_status_completed, AppColors.SyncStatusCompleted),
    PlanToWatch(3, Res.string.sync_status_plan_to_watch, AppColors.SyncStatusPlanToWatch),
    Paused(4, Res.string.sync_status_paused, AppColors.SyncStatusPaused),
    Dropped(5, Res.string.sync_status_dropped, AppColors.SyncStatusDropped);

    companion object {
        fun fromId(id: Int?): ExternalSyncStatus {
            return entries.firstOrNull { it.internalId == id } ?: None
        }
    }
}

/**
 * Supported scoring scale formats for external tracking services (AniList, MAL, Simkl, Kitsu).
 */
enum class TrackerScoreScale(
    val id: String,
    val stringRes: StringResource,
    val maxScore: Int,
    val isDecimal: Boolean = false
) {
    /** 10-point decimal rating (0.0 to 10.0, e.g. 8.5/10) */
    Point10Decimal(
        id = "POINT_10_DECIMAL",
        stringRes = Res.string.sync_scale_10_decimal,
        maxScore = 10,
        isDecimal = true
    ),
    /** 100-point integer rating (1 to 100, e.g. 85/100) */
    Point100(
        id = "POINT_100",
        stringRes = Res.string.sync_scale_100_point,
        maxScore = 100,
        isDecimal = false
    ),
    /** 5-star rating (1 to 5 stars) */
    Point5Star(
        id = "POINT_5",
        stringRes = Res.string.sync_scale_5_star,
        maxScore = 5,
        isDecimal = false
    ),
    /** 3-point smiley rating (1 = Sad, 2 = Neutral, 3 = Happy) */
    Point3Smiley(
        id = "POINT_3",
        stringRes = Res.string.sync_scale_3_smiley,
        maxScore = 3,
        isDecimal = false
    );

    /** Converts a raw Score object to the numeric value in scale units */
    fun toDisplayValue(score: Score?): Double? {
        if (score == null) return null
        return when (this) {
            Point10Decimal -> (score.toDouble(10) * 10.0).roundToInt() / 10.0
            Point100 -> score.toDouble(100).roundToInt().toDouble().coerceIn(1.0, 100.0)
            Point5Star -> score.toDouble(5).roundToInt().toDouble().coerceIn(1.0, 5.0)
            Point3Smiley -> score.toDouble(3).roundToInt().toDouble().coerceIn(1.0, 3.0)
        }
    }

    /** Formats Score into a string based on scale rules */
    fun formatScore(score: Score?): String? {
        if (score == null) return null
        return when (this) {
            Point10Decimal -> {
                val d = (score.toDouble(10) * 10.0).roundToInt() / 10.0
                if (d % 1.0 == 0.0) "${d.toInt()}" else "$d"
            }
            Point100 -> "${score.toDouble(100).roundToInt().coerceIn(1, 100)}"
            Point5Star -> "${score.toDouble(5).roundToInt().coerceIn(1, 5)}"
            Point3Smiley -> when (score.toDouble(3).roundToInt().coerceIn(1, 3)) {
                1 -> "Sad"
                2 -> "Neutral"
                3 -> "Happy"
                else -> null
            }
        }
    }

    /** Converts a scale value back to a normalized Score object */
    fun toScore(value: Double?): Score? {
        if (value == null || value <= 0.0) return null
        return when (this) {
            Point10Decimal -> Score.from(value.coerceIn(0.1, 10.0), 10)
            Point100 -> Score.from100(value.roundToInt().coerceIn(1, 100))
            Point5Star -> Score.from5(value.roundToInt().coerceIn(1, 5))
            Point3Smiley -> Score.from(value.roundToInt().coerceIn(1, 3), 3)
        }
    }

    companion object {
        fun fromId(id: String?): TrackerScoreScale {
            if (id == null) return Point10Decimal
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Point10Decimal
        }
    }
}

/**
 * 3-point smiley rating levels for external trackers (e.g. AniList 3-point scale).
 */
enum class SmileyRating(
    val scoreValue: Int,
    val stringRes: StringResource,
    val emoji: String
) {
    Sad(1, Res.string.sync_score_sad, "🙁"),
    Neutral(2, Res.string.sync_score_neutral, "😐"),
    Happy(3, Res.string.sync_score_happy, "😊");

    companion object {
        fun fromScoreValue(value: Int?): SmileyRating? {
            return entries.firstOrNull { it.scoreValue == value }
        }

        fun fromScore(score: Score?): SmileyRating? {
            if (score == null) return null
            val value = score.toDouble(3).roundToInt().coerceIn(1, 3)
            return fromScoreValue(value)
        }
    }
}

/**
 * Synchronization state for a specific tracking service.
 */
data class ExternalSyncEntry(
    val service: SyncService,
    val syncId: String? = null,
    val isLinked: Boolean = false,
    val status: ExternalSyncStatus = ExternalSyncStatus.None,
    val score: Int? = null, // 1 to 10 integer for backward compatibility
    val rawScore: Score? = null, // Normalized Score object
    val scoreScale: TrackerScoreScale = TrackerScoreScale.Point10Decimal,
    val watchedEpisodes: Int = 0,
    val maxEpisodes: Int? = null,
    val remoteUrl: String? = null,
    val lastUpdated: Long = 0L,
    val isSyncing: Boolean = false
) {
    val hasTracking: Boolean
        get() = isLinked || status != ExternalSyncStatus.None || !syncId.isNullOrBlank()

    /** Effective normalized Score representation */
    val effectiveScore: Score?
        get() = rawScore ?: score?.let { Score.from10(it) }

    /** Returns formatted score according to this entry's active score scale */
    fun formattedScore(): String? = scoreScale.formatScore(effectiveScore)

    /** Returns numeric value in active scale units */
    val displayScoreValue: Double?
        get() = scoreScale.toDisplayValue(effectiveScore)
}

/**
 * Key used to index episodes by Dub status (Sub/Dub) and Season number.
 */
@Serializable
data class EpisodeIndexer(
    val dubStatus: DubStatus = DubStatus.None,
    val season: Int = 0
)

/**
 * Season metadata for UI selection.
 */
@Serializable
data class ResultSeason(
    val season: Int,
    val name: String? = null,
    val displaySeason: Int? = null,
    val episodeCount: Int = 0
) {
    fun displayName(): String {
        return if (name != null && displaySeason == null) {
            name
        } else if (name != null) {
            "Season ${displaySeason ?: season} - $name"
        } else if (season == 0) {
            "Specials / Other"
        } else {
            "Season ${displaySeason ?: season}"
        }
    }
}

/**
 * Represents an episode in the media details view.
 */
@Serializable
data class ResultEpisode(
    val headerName: String,
    val name: String? = null,
    val poster: String? = null,
    val episode: Int,
    val seasonIndex: Int? = null,
    val season: Int? = null,
    val data: String,
    val apiName: String,
    val id: Int,
    val index: Int,
    val position: Long = 0L,
    val duration: Long = 0L,
    val score: Score? = null,
    val description: String? = null,
    val isFiller: Boolean? = null,
    val tvType: TvType,
    val parentId: Int,
    val videoWatchState: Int = 0, // 0 = None, 1 = Watching, 2 = Watched
    val totalEpisodeIndex: Int? = null,
    val airDate: Long? = null,
    val runTime: Int? = null,
    val seasonData: SeasonData? = null
) {
    /** Position in ms, ignoring negligible edges (start/finish) */
    fun getRealPosition(): Long {
        if (duration <= 0) return 0L
        val percentage = position * 100 / duration
        if (percentage <= 5 || percentage >= 95) return 0L
        return position
    }

    /** Position formatted for UI progress bars (0 to duration) */
    fun getDisplayPosition(): Long {
        if (duration <= 0) return 0L
        val percentage = position * 100 / duration
        if (percentage <= 1) return 0L
        if (percentage <= 5) return 5 * duration / 100
        if (percentage >= 95) return duration
        return position
    }

    /** Progress ratio between 0.0f and 1.0f */
    fun getWatchProgress(): Float {
        if (duration <= 0) return 0f
        return (getDisplayPosition().toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    val isWatched: Boolean
        get() = videoWatchState == 2 || (duration > 0 && position * 100 / duration >= 90)
}

/**
 * Immutable State for ResultViewModel in KMP MVI architecture.
 */
data class ResultState(
    // Loading & Error states
    val isLoading: Boolean = false,
    val error: UiText? = null,

    // Source Info
    val url: String? = null,
    val apiName: String? = null,
    val mediaId: Int? = null,

    // Raw response and Metadata
    val loadResponse: LoadResponse? = null,
    val title: String = "",
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backgroundPosterUrl: String? = null,
    val logoUrl: String? = null,
    val year: Int? = null,
    val rating: Score? = null,
    val tags: List<String> = emptyList(),
    val actors: List<ActorData> = emptyList(),
    val tvType: TvType? = null,
    val duration: Int? = null, // In minutes
    val comingSoon: Boolean = false,
    val showStatus: ShowStatus? = null,
    val contentRating: String? = null,
    val trailers: List<TrailerData> = emptyList(),
    val recommendations: List<SearchResponse> = emptyList(),
    val syncData: Map<String, String> = emptyMap(),
    val posterHeaders: Map<String, String>? = null,

    // Classification helpers
    val isMovie: Boolean = false,
    val isAnime: Boolean = false,
    val isEpisodeBased: Boolean = false,

    // Seasons & Episodes
    val availableSeasons: List<ResultSeason> = emptyList(),
    val availableDubStatuses: List<DubStatus> = emptyList(),
    val selectedSeason: Int? = null,
    val selectedDubStatus: DubStatus = DubStatus.None,
    val episodesByIndexer: Map<EpisodeIndexer, List<ResultEpisode>> = emptyMap(),
    val episodes: List<ResultEpisode> = emptyList(),
    val selectedEpisode: ResultEpisode? = null,
    val isEpisodeMenuOpen: Boolean = false,
    val selectedMenuEpisode: ResultEpisode? = null,

    // Persistence / Room status
    val isBookmarked: Boolean = false,
    val bookmarkWatchType: Int = 0, // 0=None, 1=Watching, 2=Completed, 3=On Hold, 4=Dropped, 5=Planned
    val isFavorite: Boolean = false,
    val isSubscribed: Boolean = false,
    val lastWatchedEpisode: ResultEpisode? = null,
    val lastWatchedProgress: WatchProgressEntity? = null,
    val resumeWatching: ResumeWatchingEntity? = null,

    // Extraction Links & Subtitles
    val isExtractingLinks: Boolean = false,
    val extractedLinks: List<ExtractorLink> = emptyList(),
    val extractedSubtitles: List<SubtitleFile> = emptyList(),
    val linksLoadingProgress: Int = 0,
    val linksLoadingError: String? = null,

    // External Tracking & Sync (AniList, MAL, Simkl, Kitsu)
    val externalSyncStates: Map<SyncService, ExternalSyncEntry> = SyncService.entries.associateWith {
        ExternalSyncEntry(service = it)
    },
    val selectedSyncService: SyncService = SyncService.AniList,
    val isSyncSaving: Boolean = false,

    // Trailer Viewer & Player State
    val selectedTrailerIndex: Int = 0,
    val isExtractingTrailer: Boolean = false,
    val extractedTrailerLinks: List<ExtractorLink> = emptyList(),
    val extractedTrailerSubtitles: List<SubtitleFile> = emptyList(),
    val selectedTrailerQuality: ExtractorLink? = null,
    val trailerExtractionError: String? = null,
    val isTrailerDialogOpen: Boolean = false
) : UiState {
    /** True if there is at least one episode available */
    val hasEpisodes: Boolean
        get() = episodes.isNotEmpty()

    /** True if links have been successfully extracted */
    val hasLinks: Boolean
        get() = extractedLinks.isNotEmpty()

    /** True if trailers are available from response */
    val hasTrailers: Boolean
        get() = trailers.isNotEmpty() || (loadResponse?.trailers?.isNotEmpty() == true)

    /** Currently active TrailerData */
    val currentTrailerData: TrailerData?
        get() = trailers.getOrNull(selectedTrailerIndex) ?: loadResponse?.trailers?.getOrNull(selectedTrailerIndex)

    /** Effective display poster url */
    val displayPosterUrl: String?
        get() = posterUrl ?: backgroundPosterUrl

    /** Effective display background poster url */
    val displayBackgroundPosterUrl: String?
        get() = backgroundPosterUrl ?: posterUrl

    /** Active sync state for the currently selected service */
    val currentSyncState: ExternalSyncEntry
        get() = externalSyncStates[selectedSyncService] ?: ExternalSyncEntry(selectedSyncService)

    /** Returns true if ANY service is actively linked or tracked */
    val isSyncLinked: Boolean
        get() = externalSyncStates.values.any { it.hasTracking }

    /** First linked service for badge indicator in header / action buttons */
    val primaryLinkedSync: ExternalSyncEntry?
        get() = externalSyncStates.values.firstOrNull { it.hasTracking }
}

/**
 * One-shot UI side effects for ResultViewModel.
 */
sealed interface ResultEffect : UiEffect {
    data class AutoPlayEpisode(val episode: ResultEpisode, val resumePosition: Long?, val parentId: Int) : ResultEffect
    data class ShowToast(val message: String) : ResultEffect
    data class CopyToClipboard(val text: String, val toastMessage: String) : ResultEffect
}
