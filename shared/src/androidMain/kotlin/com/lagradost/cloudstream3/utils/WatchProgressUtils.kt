package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.models.NEXT_WATCH_EPISODE_PERCENTAGE
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.models.VideoWatchState
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val VIDEO_POS_DUR = "video_pos_dur"
const val VIDEO_WATCH_STATE = "video_watch_state"
const val RESULT_RESUME_WATCHING = "result_resume_watching_2"
const val RESULT_DUB = "result_dub"

@Serializable
data class PosDur(
    @SerialName("position") val position: Long,
    @SerialName("duration") val duration: Long,
) {
    fun fixVisual(): PosDur {
        if (duration <= 0) return PosDur(0, duration)
        val percentage = position * 100 / duration
        return when {
            percentage <= 1 -> PosDur(0, duration)
            percentage <= 5 -> PosDur(5 * duration / 100, duration)
            percentage >= 95 -> PosDur(duration, duration)
            else -> this
        }
    }
}

@Serializable
data class ResumeWatching(
    @SerialName("parentId") val parentId: Int,
    @SerialName("episodeId") val episodeId: Int?,
    @SerialName("episode") val episode: Int?,
    @SerialName("season") val season: Int?,
    @SerialName("updateTime") val updateTime: Long,
    @SerialName("isFromDownload") val isFromDownload: Boolean,
)

@Serializable
data class ResumeWatchingResult(
    @SerialName("name") override val name: String,
    @SerialName("url") override val url: String,
    @SerialName("apiName") override val apiName: String,
    @SerialName("type") override var type: TvType? = null,
    @SerialName("posterUrl") override var posterUrl: String?,
    @SerialName("watchPos") val watchPos: PosDur?,
    @SerialName("id") override var id: Int?,
    @SerialName("parentId") val parentId: Int?,
    @SerialName("episode") val episode: Int?,
    @SerialName("season") val season: Int?,
    @SerialName("isFromDownload") val isFromDownload: Boolean,
    @SerialName("quality") override var quality: SearchQuality? = null,
    @SerialName("posterHeaders") override var posterHeaders: Map<String, String>? = null,
    @SerialName("score") override var score: Score? = null,
) : SearchResponse

fun getViewPos(id: Int?): PosDur? {
    if (id == null) return null
    val account = AccountManager.currentAccount()
    val json = AppPreferenceManager.getStringSync("$account/$VIDEO_POS_DUR/$id") ?: return null
    return tryParseJson<PosDur>(json)
}

fun setViewPos(id: Int?, pos: Long, dur: Long) {
    if (id == null || dur < 30_000) return
    val account = AccountManager.currentAccount()
    AppPreferenceManager.setStringSync("$account/$VIDEO_POS_DUR/$id", toJson(PosDur(pos, dur)))
}

fun getVideoWatchState(id: Int?): VideoWatchState? {
    if (id == null) return null
    val account = AccountManager.currentAccount()
    val json = AppPreferenceManager.getStringSync("$account/$VIDEO_WATCH_STATE/$id") ?: return null
    return tryParseJson<VideoWatchState>(json)
}

fun setVideoWatchState(id: Int?, watchState: VideoWatchState) {
    if (id == null) return
    val account = AccountManager.currentAccount()
    if (watchState == VideoWatchState.None) {
        AppPreferenceManager.deletePreferenceSync("$account/$VIDEO_WATCH_STATE/$id")
    } else {
        AppPreferenceManager.setStringSync("$account/$VIDEO_WATCH_STATE/$id", toJson(watchState))
    }
}

fun getAllResumeStateIds(): List<Int>? {
    val account = AccountManager.currentAccount()
    val folder = "$account/$RESULT_RESUME_WATCHING/"
    return AppPreferenceManager.getKeysSync(folder).mapNotNull {
        it.removePrefix(folder).toIntOrNull()
    }.ifEmpty { null }
}

fun getLastWatched(id: Int?): ResumeWatching? {
    if (id == null) return null
    val account = AccountManager.currentAccount()
    val json = AppPreferenceManager.getStringSync("$account/$RESULT_RESUME_WATCHING/$id") ?: return null
    return tryParseJson<ResumeWatching>(json)
}

fun setLastWatched(
    parentId: Int?,
    episodeId: Int?,
    episode: Int?,
    season: Int?,
    isFromDownload: Boolean = false,
    updateTime: Long? = null,
) {
    if (parentId == null) return
    val account = AccountManager.currentAccount()
    val data = ResumeWatching(
        parentId = parentId,
        episodeId = episodeId,
        episode = episode,
        season = season,
        updateTime = updateTime ?: System.currentTimeMillis(),
        isFromDownload = isFromDownload,
    )
    AppPreferenceManager.setStringSync("$account/$RESULT_RESUME_WATCHING/$parentId", toJson(data))
}

fun removeLastWatched(parentId: Int?) {
    if (parentId == null) return
    val account = AccountManager.currentAccount()
    AppPreferenceManager.deletePreferenceSync("$account/$RESULT_RESUME_WATCHING/$parentId")
}

fun setViewPosAndResume(
    id: Int?,
    position: Long,
    duration: Long,
    currentEpisode: Any?,
    nextEpisode: Any?,
) {
    setViewPos(id, position, duration)
    if (id != null) {
        when (val meta = currentEpisode) {
            is ResultEpisode -> {
                if (meta.videoWatchState == VideoWatchState.Watched) {
                    setVideoWatchState(id, VideoWatchState.None)
                }
            }
        }
    }

    val percentage = if (duration > 0) position * 100L / duration else 0L
    val nextEp = percentage >= NEXT_WATCH_EPISODE_PERCENTAGE
    val resumeMeta = if (nextEp) nextEpisode else currentEpisode
    if (resumeMeta == null && nextEp) {
        when (val newMeta = currentEpisode) {
            is ResultEpisode -> {
                removeLastWatched(newMeta.parentId)
            }
        }
    } else {
        when (resumeMeta) {
            is ResultEpisode -> {
                setLastWatched(
                    resumeMeta.parentId,
                    resumeMeta.id,
                    resumeMeta.episode,
                    resumeMeta.season,
                    isFromDownload = false,
                )
            }
        }
    }
}

fun getDub(id: Int): DubStatus? {
    val account = AccountManager.currentAccount()
    val ordinal = AppPreferenceManager.getIntSync("$account/$RESULT_DUB/$id", -1)
    return DubStatus.entries.getOrNull(ordinal)
}

fun setDub(id: Int, status: DubStatus) {
    val account = AccountManager.currentAccount()
    AppPreferenceManager.setIntSync("$account/$RESULT_DUB/$id", status.ordinal)
}
