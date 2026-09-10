package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.models.ResultEpisode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class IntroDbSkip : SkipAPI() {
    override val name = "IntroDb"

    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    companion object {
        private fun Segment?.toSkipStamp(type: SkipType): SkipStamp? {
            val start = this?.startMs ?: return null
            val end = this.endMs ?: return null
            return SkipStamp(type = type, startMs = start, endMs = end)
        }
    }

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): ImmutableList<SkipStamp>? {
        val season = episode.season ?: return null
        val imdbId = data.getImdbId() ?: return null

        val url = "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=${episode.episode}"
        val response = runCatching { app.get(url).parsed<IntroDbResponse>() }.getOrNull() ?: return null

        return listOfNotNull(
            response.intro.toSkipStamp(SkipType.Opening),
            response.recap.toSkipStamp(SkipType.Recap),
            response.outro.toSkipStamp(SkipType.Ending)
        ).toImmutableList()
    }

    @Serializable
    data class IntroDbResponse(
        @SerialName("imdb_id") val imdbId: String?,
        @SerialName("season") val season: Int?,
        @SerialName("episode") val episode: Int?,
        @SerialName("intro") val intro: Segment?,
        @SerialName("recap") val recap: Segment?,
        @SerialName("outro") val outro: Segment?,
    )

    @Serializable
    data class Segment(
        @SerialName("start_sec") val startSec: Double?,
        @SerialName("end_sec") val endSec: Double?,
        @SerialName("start_ms") val startMs: Long?,
        @SerialName("end_ms") val endMs: Long?,
        @SerialName("confidence") val confidence: Double?,
        @SerialName("submission_count") val submissionCount: Int?,
        @SerialName("updated_at") val updatedAt: String?,
    )
}
