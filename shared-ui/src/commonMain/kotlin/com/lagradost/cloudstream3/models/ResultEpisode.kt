package com.lagradost.cloudstream3.models

import com.lagradost.cloudstream3.IDownloadableMinimum
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.player.native.SubtitleData
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val START_ACTION_RESUME_LATEST = 1
const val START_ACTION_LOAD_EP = 2
const val NEXT_WATCH_EPISODE_PERCENTAGE = 90

enum class VideoWatchState {
    None,
    Watched
}

@Serializable
data class ResultEpisode(
    @SerialName("headerName") val headerName: String,
    @SerialName("name") val name: String?,
    @SerialName("poster") val poster: String?,
    @SerialName("episode") val episode: Int,
    @SerialName("seasonIndex") val seasonIndex: Int?,
    @SerialName("season") val season: Int?,
    @SerialName("data") val data: String,
    @SerialName("apiName") val apiName: String,
    @SerialName("id") val id: Int,
    @SerialName("index") val index: Int,
    @SerialName("position") val position: Long,
    @SerialName("duration") val duration: Long,
    @SerialName("score") val score: Score?,
    @SerialName("description") val description: String?,
    @SerialName("isFiller") val isFiller: Boolean?,
    @SerialName("tvType") val tvType: TvType,
    @SerialName("parentId") val parentId: Int,
    @SerialName("videoWatchState") val videoWatchState: VideoWatchState,
    @SerialName("totalEpisodeIndex") val totalEpisodeIndex: Int? = null,
    @SerialName("airDate") val airDate: Long? = null,
    @SerialName("runTime") val runTime: Int? = null,
    @SerialName("seasonData") val seasonData: SeasonData? = null,
)

fun ResultEpisode.getRealPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 5 || percentage >= 95) return 0
    return position
}

fun ResultEpisode.getDisplayPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 1) return 0
    if (percentage <= 5) return 5 * duration / 100
    if (percentage >= 95) return duration
    return position
}

fun buildResultEpisode(
    headerName: String,
    name: String? = null,
    poster: String? = null,
    episode: Int,
    seasonIndex: Int? = null,
    season: Int? = null,
    data: String,
    apiName: String,
    id: Int,
    index: Int,
    rating: Score? = null,
    description: String? = null,
    isFiller: Boolean? = null,
    tvType: TvType,
    parentId: Int,
    totalEpisodeIndex: Int? = null,
    airDate: Long? = null,
    runTime: Int? = null,
    seasonData: SeasonData? = null,
    position: Long = 0,
    duration: Long = 0,
    videoWatchState: VideoWatchState = VideoWatchState.None,
): ResultEpisode {
    return ResultEpisode(
        headerName = headerName,
        name = name,
        poster = poster,
        episode = episode,
        seasonIndex = seasonIndex,
        season = season,
        data = data,
        apiName = apiName,
        id = id,
        index = index,
        position = position,
        duration = duration,
        score = rating,
        description = description,
        isFiller = isFiller,
        tvType = tvType,
        parentId = parentId,
        videoWatchState = videoWatchState,
        totalEpisodeIndex = totalEpisodeIndex,
        airDate = airDate,
        runTime = runTime,
        seasonData = seasonData
    )
}

fun ResultEpisode.getWatchProgress(): Float {
    if (duration <= 0) return 0f
    return (getDisplayPosition() / 1000).toFloat() / (duration / 1000).toFloat()
}

data class LinkLoadingResult(
    val links: List<ExtractorLink>,
    val subs: List<SubtitleData>,
    val syncData: Any? = null,
)

enum class EpisodeSortType {
    NUMBER_ASC,
    NUMBER_DESC,
    RATING_HIGH_LOW,
    RATING_LOW_HIGH,
    DATE_NEWEST,
    DATE_OLDEST,
}

data class ExtractorSubtitleLink(
    val name: String,
    override val url: String,
    val extra: String = "",
    override val headers: Map<String, String> = mapOf(),
) : IDownloadableMinimum {
    override val referer: String
        get() = headers["referer"] ?: ""
}

data class MetadataHolder(
    val apiName: String?,
    val isMovie: Boolean,
    val title: String?,
    val poster: String?,
    val currentEpisodeIndex: Int,
    val episodes: List<ResultEpisode>,
    val currentLinks: List<ExtractorLink>,
    val subtitles: List<SubtitleData>,
)

fun LoadResponse.getId(): Int {
    return (this.apiName + "_" + this.url).hashCode()
}
