package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.models.ResultEpisode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AniSkip : SkipAPI() {
    override val name: String = "AniSkip"
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.OVA)

    companion object {
        private val SKIP_TYPE_MAP = mapOf(
            "op" to SkipType.Opening,
            "ed" to SkipType.Ending,
            "recap" to SkipType.Recap,
            "mixed-ed" to SkipType.MixedEnding,
            "mixed-op" to SkipType.MixedOpening
        )

        private fun parseSkipType(rawType: String): SkipType? = SKIP_TYPE_MAP[rawType]

        private fun Stamp.toSkipStamp(): SkipStamp? {
            val type = parseSkipType(skipType) ?: return null
            return SkipStamp(
                type = type,
                startMs = (interval.startTime * 1000.0).toLong(),
                endMs = (interval.endTime * 1000.0).toLong(),
            )
        }
    }

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): ImmutableList<SkipStamp>? {
        if (data !is AnimeLoadResponse) return null

        val malId = data.getMalId()?.toIntOrNull() ?: return null
        val durationSec = episodeDurationMs / 1000L
        val url = "https://api.aniskip.com/v2/skip-times/$malId/${episode.episode}?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$durationSec"

        val response = runCatching { app.get(url).parsed<AniSkipResponse>() }.getOrNull() ?: return null

        return response.results?.mapNotNull { it.toSkipStamp() }?.toImmutableList()
    }

    @Serializable
    data class AniSkipResponse(
        @SerialName("found") val found: Boolean,
        @SerialName("results") val results: List<Stamp>?,
        @SerialName("message") val message: String?,
        @SerialName("statusCode") val statusCode: Int,
    )

    @Serializable
    data class Stamp(
        @SerialName("interval") val interval: AniSkipInterval,
        @SerialName("skipType") val skipType: String,
        @SerialName("skipId") val skipId: String,
        @SerialName("episodeLength") val episodeLength: Double,
    )

    @Serializable
    data class AniSkipInterval(
        @SerialName("startTime") val startTime: Double,
        @SerialName("endTime") val endTime: Double,
    )
}
