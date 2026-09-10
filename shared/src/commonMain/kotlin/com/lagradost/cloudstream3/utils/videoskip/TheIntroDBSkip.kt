package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.models.ResultEpisode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TheIntroDBSkip : SkipAPI() {
    override val name = "TheIntroDB"
    override val supportedTypes = setOf(
        TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.Movie,
        TvType.AsianDrama
    )

    companion object {
        private const val MAIN_URL = "https://api.theintrodb.org"

        private fun buildUrl(data: LoadResponse, episode: ResultEpisode): String? {
            val idSuffix = data.getTMDbId()?.let { "tmdb_id=$it" }
                ?: data.getImdbId()?.let { "imdb_id=$it" }
                ?: return null

            if (data.isMovie()) {
                return "$MAIN_URL/v2/media?$idSuffix"
            }

            val season = episode.season ?: return null
            return "$MAIN_URL/v2/media?$idSuffix&season=$season&episode=${episode.episode}"
        }

        private fun List<Stamp>.toSkipStamps(type: SkipType, defaultEndMs: Long): List<SkipStamp> =
            map { stamp ->
                SkipStamp(
                    type = type,
                    startMs = stamp.startMs ?: 0L,
                    endMs = stamp.endMs ?: defaultEndMs
                )
            }
    }

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): ImmutableList<SkipStamp>? {
        val url = buildUrl(data, episode) ?: return null
        val root = runCatching { app.get(url).parsed<Root>() }.getOrNull() ?: return null

        return (
            root.intro.toSkipStamps(SkipType.Intro, episodeDurationMs) +
            root.credits.toSkipStamps(SkipType.Credits, episodeDurationMs) +
            root.recap.toSkipStamps(SkipType.Recap, episodeDurationMs) +
            root.preview.toSkipStamps(SkipType.Preview, episodeDurationMs)
        ).toImmutableList()
    }

    @Serializable
    data class Root(
        @SerialName("tmdb_id") val tmdbId: Long,
        @SerialName("type") val type: String,
        @SerialName("intro") val intro: List<Stamp> = emptyList(),
        @SerialName("recap") val recap: List<Stamp> = emptyList(),
        @SerialName("credits") val credits: List<Stamp> = emptyList(),
        @SerialName("preview") val preview: List<Stamp> = emptyList(),
    )

    @Serializable
    data class Stamp(
        @SerialName("start_ms") val startMs: Long?,
        @SerialName("end_ms") val endMs: Long?,
    )
}
