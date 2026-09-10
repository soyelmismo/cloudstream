package com.lagradost.cloudstream3.shared.viewmodels.result

import androidx.compose.runtime.Immutable
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import com.lagradost.cloudstream4.generated.resources.*
import kotlin.math.roundToInt

enum class TrackerScoreScale(
    val id: String,
    val stringRes: StringResource,
    val maxScore: Int,
    val isDecimal: Boolean = false
) {
    Point10Decimal(
        id = "POINT_10_DECIMAL",
        stringRes = Res.string.sync_scale_10_decimal,
        maxScore = 10,
        isDecimal = true
    ) {
        override fun toDisplayValue(score: Score?): Double? {
            if (score == null) return null
            return (score.toDouble(10) * 10.0).roundToInt() / 10.0
        }

        override fun formatScore(score: Score?): String? {
            if (score == null) return null
            val d = (score.toDouble(10) * 10.0).roundToInt() / 10.0
            return if (d % 1.0 == 0.0) "${d.toInt()}" else "$d"
        }

        override fun toScore(value: Double?): Score? {
            if (value == null || value <= 0.0) return null
            return Score.from(value.coerceIn(0.1, 10.0), 10)
        }
    },
    Point100(
        id = "POINT_100",
        stringRes = Res.string.sync_scale_100_point,
        maxScore = 100,
        isDecimal = false
    ) {
        override fun toDisplayValue(score: Score?): Double? {
            if (score == null) return null
            return score.toDouble(100).roundToInt().toDouble().coerceIn(1.0, 100.0)
        }

        override fun formatScore(score: Score?): String? {
            if (score == null) return null
            return "${score.toDouble(100).roundToInt().coerceIn(1, 100)}"
        }

        override fun toScore(value: Double?): Score? {
            if (value == null || value <= 0.0) return null
            return Score.from100(value.roundToInt().coerceIn(1, 100))
        }
    },
    Point5Star(
        id = "POINT_5",
        stringRes = Res.string.sync_scale_5_star,
        maxScore = 5,
        isDecimal = false
    ) {
        override fun toDisplayValue(score: Score?): Double? {
            if (score == null) return null
            return score.toDouble(5).roundToInt().toDouble().coerceIn(1.0, 5.0)
        }

        override fun formatScore(score: Score?): String? {
            if (score == null) return null
            return "${score.toDouble(5).roundToInt().coerceIn(1, 5)}"
        }

        override fun toScore(value: Double?): Score? {
            if (value == null || value <= 0.0) return null
            return Score.from5(value.roundToInt().coerceIn(1, 5))
        }
    },
    Point3Smiley(
        id = "POINT_3",
        stringRes = Res.string.sync_scale_3_smiley,
        maxScore = 3,
        isDecimal = false
    ) {
        override fun toDisplayValue(score: Score?): Double? {
            if (score == null) return null
            return score.toDouble(3).roundToInt().toDouble().coerceIn(1.0, 3.0)
        }

        override fun formatScore(score: Score?): String? {
            if (score == null) return null
            return when (score.toDouble(3).roundToInt().coerceIn(1, 3)) {
                1 -> "Sad"
                2 -> "Neutral"
                3 -> "Happy"
                else -> null
            }
        }

        override fun toScore(value: Double?): Score? {
            if (value == null || value <= 0.0) return null
            return Score.from(value.roundToInt().coerceIn(1, 3), 3)
        }
    };

    abstract fun toDisplayValue(score: Score?): Double?
    abstract fun formatScore(score: Score?): String?
    abstract fun toScore(value: Double?): Score?

    companion object {
        fun fromId(id: String?): TrackerScoreScale {
            if (id == null) return Point10Decimal
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Point10Decimal
        }
    }
}

enum class SyncService(
    val syncIdName: SyncIdName,
    val serviceName: String,
    val idPrefix: String,
    val defaultUrlPrefix: String,
    val brandColor: Color,
    val defaultScale: TrackerScoreScale = TrackerScoreScale.Point10Decimal
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
        brandColor = AppColors.BrandKitsu,
        defaultScale = TrackerScoreScale.Point100
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

@Immutable
data class ExternalSyncEntry(
    val service: SyncService,
    val syncId: String? = null,
    val isLinked: Boolean = false,
    val status: ExternalSyncStatus = ExternalSyncStatus.None,
    val score: Int? = null,
    val rawScore: Score? = null,
    val scoreScale: TrackerScoreScale = TrackerScoreScale.Point10Decimal,
    val watchedEpisodes: Int = 0,
    val maxEpisodes: Int? = null,
    val remoteUrl: String? = null,
    val lastUpdated: Long = 0L,
    val isSyncing: Boolean = false
) {
    val hasTracking: Boolean
        get() = isLinked || status != ExternalSyncStatus.None || !syncId.isNullOrBlank()

    val effectiveScore: Score?
        get() = rawScore ?: score?.let { Score.from10(it) }

    fun formattedScore(): String? = scoreScale.formatScore(effectiveScore)

    val displayScoreValue: Double?
        get() = scoreScale.toDisplayValue(effectiveScore)
}

@Immutable
@Serializable
data class EpisodeIndexer(
    val dubStatus: DubStatus = DubStatus.None,
    val season: Int = 0
)

@Immutable
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

@Immutable
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
    val videoWatchState: Int = 0,
    val totalEpisodeIndex: Int? = null,
    val airDate: Long? = null,
    val runTime: Int? = null,
    val seasonData: SeasonData? = null
) {
    fun getRealPosition(): Long {
        if (duration <= 0) return 0L
        val percentage = position * 100 / duration
        if (percentage <= 5 || percentage >= 95) return 0L
        return position
    }

    fun getDisplayPosition(): Long {
        if (duration <= 0) return 0L
        val percentage = position * 100 / duration
        if (percentage <= 1) return 0L
        if (percentage <= 5) return 5 * duration / 100
        if (percentage >= 95) return duration
        return position
    }

    fun getWatchProgress(): Float {
        if (duration <= 0) return 0f
        return (getDisplayPosition().toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    val isWatched: Boolean
        get() = videoWatchState == 2 || (duration > 0 && position * 100 / duration >= 90)
}

@Immutable
data class ResultState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val url: String? = null,
    val apiName: String? = null,
    val mediaId: Int? = null,
    val loadResponse: LoadResponse? = null,
    val title: String = "",
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backgroundPosterUrl: String? = null,
    val logoUrl: String? = null,
    val year: Int? = null,
    val rating: Score? = null,
    val tags: ImmutableList<String> = persistentListOf(),
    val actors: ImmutableList<ActorData> = persistentListOf(),
    val tvType: TvType? = null,
    val duration: Int? = null,
    val comingSoon: Boolean = false,
    val showStatus: ShowStatus? = null,
    val contentRating: String? = null,
    val trailers: ImmutableList<TrailerData> = persistentListOf(),
    val recommendations: ImmutableList<SearchResponse> = persistentListOf(),
    val syncData: ImmutableMap<String, String> = persistentMapOf(),
    val posterHeaders: ImmutableMap<String, String>? = null,
    val isMovie: Boolean = false,
    val isAnime: Boolean = false,
    val isEpisodeBased: Boolean = false,
    val availableSeasons: ImmutableList<ResultSeason> = persistentListOf(),
    val availableDubStatuses: ImmutableList<DubStatus> = persistentListOf(),
    val selectedSeason: Int? = null,
    val selectedDubStatus: DubStatus = DubStatus.None,
    val episodesByIndexer: ImmutableMap<EpisodeIndexer, ImmutableList<ResultEpisode>> = persistentMapOf(),
    val episodes: ImmutableList<ResultEpisode> = persistentListOf(),
    val selectedEpisode: ResultEpisode? = null,
    val isEpisodeMenuOpen: Boolean = false,
    val selectedMenuEpisode: ResultEpisode? = null,
    val isBookmarked: Boolean = false,
    val bookmarkWatchType: Int = 0,
    val isFavorite: Boolean = false,
    val isSubscribed: Boolean = false,
    val lastWatchedEpisode: ResultEpisode? = null,
    val lastWatchedProgress: WatchProgressEntity? = null,
    val resumeWatching: ResumeWatchingEntity? = null,
    val isExtractingLinks: Boolean = false,
    val extractedLinks: ImmutableList<ExtractorLink> = persistentListOf(),
    val extractedSubtitles: ImmutableList<SubtitleFile> = persistentListOf(),
    val linksLoadingProgress: Int = 0,
    val linksLoadingError: String? = null,
    val externalSyncStates: ImmutableMap<SyncService, ExternalSyncEntry> = SyncService.entries.associateWith {
        ExternalSyncEntry(service = it)
    }.toImmutableMap(),
    val selectedSyncService: SyncService = SyncService.AniList,
    val isSyncSaving: Boolean = false,
    val selectedTrailerIndex: Int = 0,
    val isExtractingTrailer: Boolean = false,
    val extractedTrailerLinks: ImmutableList<ExtractorLink> = persistentListOf(),
    val extractedTrailerSubtitles: ImmutableList<SubtitleFile> = persistentListOf(),
    val selectedTrailerQuality: ExtractorLink? = null,
    val trailerExtractionError: String? = null,
    val isTrailerDialogOpen: Boolean = false
) : UiState {
    val hasEpisodes: Boolean
        get() = episodes.isNotEmpty()

    val hasLinks: Boolean
        get() = extractedLinks.isNotEmpty()

    val hasTrailers: Boolean
        get() = trailers.isNotEmpty() || (loadResponse?.trailers?.isNotEmpty() == true)

    val currentTrailerData: TrailerData?
        get() = trailers.getOrNull(selectedTrailerIndex) ?: loadResponse?.trailers?.getOrNull(selectedTrailerIndex)

    val displayPosterUrl: String?
        get() = posterUrl ?: backgroundPosterUrl

    val displayBackgroundPosterUrl: String?
        get() = backgroundPosterUrl ?: posterUrl

    val currentSyncState: ExternalSyncEntry
        get() = externalSyncStates[selectedSyncService] ?: ExternalSyncEntry(selectedSyncService)

    val isSyncLinked: Boolean
        get() = externalSyncStates.values.any { it.hasTracking }

    val primaryLinkedSync: ExternalSyncEntry?
        get() = externalSyncStates.values.firstOrNull { it.hasTracking }
}

sealed interface ResultEffect : UiEffect {
    data class AutoPlayEpisode(val episode: ResultEpisode, val resumePosition: Long?, val parentId: Int) : ResultEffect
    data class ShowToast(val message: String) : ResultEffect
    data class CopyToClipboard(val text: String, val toastMessage: String) : ResultEffect
}
