package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager.Companion.animeSkipApi
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.syncproviders.providers.AnimeSkipAuth
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AnimeSkip : SkipAPI() {
    override val name: String = "AniSkip"
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.OVA)

    val auth = AuthRepo(animeSkipApi)
    internal val cache = BoundedCache<String, Data>()

    companion object {
        const val MIN_LENGTH: Int = 4

        private val strip = Regex("[ :\\-.!]")
        private val asciiRegex = Regex("[^a-zA-Z0-9 ]")

        private val TYPE_MAPPING = mapOf(
            "Intro" to SkipType.Intro,
            "New Intro" to SkipType.Intro,
            "Credits" to SkipType.Credits,
            "Preview" to SkipType.Preview,
            "Recap" to SkipType.Recap,
            "Mixed Credits" to SkipType.MixedEnding
        )

        fun stripName(name: String?): String? =
            name?.replace(strip, "")?.lowercase()

        fun asciiName(name: String?): String? =
            name?.replace(asciiRegex, "")?.lowercase()

        private fun matchesAsciiName(dataName: String, showName: String): Boolean {
            val asciiData = asciiName(dataName)
            val asciiShow = asciiName(showName)
            return asciiData != null && asciiData == asciiShow && asciiData.length > MIN_LENGTH
        }

        private fun matchesAlternativeNames(data: AnimeLoadResponse, show: SearchShow): Boolean {
            val showOriginal = stripName(show.originalName)
            val dataJap = stripName(data.japName)
            if (showOriginal != null && showOriginal == dataJap && showOriginal.length > MIN_LENGTH) {
                return true
            }

            val showAscii = asciiName(show.name)
            val dataEng = stripName(data.engName)
            return showAscii != null && showAscii == dataEng && showAscii.length > MIN_LENGTH
        }

        private fun isMatchingShow(show: SearchShow, data: LoadResponse): Boolean {
            if (matchesAsciiName(data.name, show.name)) return true
            if (data !is AnimeLoadResponse) return false
            return matchesAlternativeNames(data, show)
        }

        private fun findMatchingShow(shows: List<SearchShow>, data: LoadResponse): SearchShow? =
            shows.firstOrNull { isMatchingShow(it, data) }

        private fun findAnimeEpisode(episodes: List<Episode>, episode: ResultEpisode): Episode? {
            val episodeNumber = episode.episode.toString()
            return episodes.firstOrNull { it.absoluteNumber == episodeNumber }
                ?: episodes.firstOrNull { it.number == episodeNumber }
        }

        private fun findTvEpisode(episodes: List<Episode>, episode: ResultEpisode): Episode? {
            val seasonNumber = episode.season?.toString()
            val episodeNumber = episode.episode.toString()
            val episodeIndex = episode.totalEpisodeIndex.toString()

            return episodes.firstOrNull { it.season == seasonNumber && it.number == episodeNumber }
                ?: episodes.firstOrNull { it.absoluteNumber == episodeIndex }
        }

        private fun findEpisodeInShow(show: SearchShow, data: LoadResponse, episode: ResultEpisode): Episode? =
            when (data) {
                is AnimeLoadResponse -> findAnimeEpisode(show.episodes, episode)
                is TvSeriesLoadResponse -> findTvEpisode(show.episodes, episode)
                else -> null
            }

        private fun parseTimestampType(typeName: String): SkipType? = TYPE_MAPPING[typeName]

        private fun parseSkipIntervals(
            timestamps: List<Timestamp>,
            episodeDurationMs: Long
        ): ImmutableList<SkipStamp> {
            val result = ArrayList<SkipStamp>()
            var pending: SkipStamp? = null

            for (stamp in timestamps) {
                val startMs = (stamp.at * 1000.0).toLong()
                pending?.let { result.add(it.copy(endMs = startMs)) }

                val type = parseTimestampType(stamp.type.name)
                pending = if (type != null) SkipStamp(type, startMs, 0L) else null
            }

            pending?.let { result.add(it.copy(endMs = episodeDurationMs)) }
            return result.toImmutableList()
        }
    }

    private fun getClientId(): String? {
        val payload = auth.authData()?.token?.payload ?: return null
        return runCatching { parseJson<AnimeSkipAuth.Payload>(payload).clientId }.getOrNull()
    }

    private suspend fun fetchShows(name: String, clientId: String): Data? {
        val query = """{
  searchShows(search: "$name", limit: 1) {
    name
    originalName
    seasonCount
    episodeCount
    episodes {
      number
      absoluteNumber
      season
      baseDuration
      timestamps {
        at
        type {
          name
        }
      }
    }
  }
}"""
        return runCatching {
            app.post(
                "https://api.anime-skip.com/graphql",
                json = mapOf("query" to query),
                headers = mapOf(
                    "Accept" to "*/*",
                    "content-type" to "application/json",
                    "X-Client-ID" to clientId
                )
            ).parsed<Root>().data
        }.getOrNull()
    }

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): ImmutableList<SkipStamp>? {
        val clientId = getClientId() ?: return null
        if (data !is AnimeLoadResponse && data !is TvSeriesLoadResponse) return null

        val showData = cache[data.name] ?: fetchShows(data.name, clientId)?.also { cache[data.name] = it } ?: return null
        val show = findMatchingShow(showData.searchShows, data) ?: return null
        val showEpisode = findEpisodeInShow(show, data, episode) ?: return null

        return parseSkipIntervals(showEpisode.timestamps, episodeDurationMs)
    }

    @Serializable
    data class Root(
        @SerialName("data") val data: Data,
    )

    @Serializable
    data class Data(
        @SerialName("searchShows") val searchShows: List<SearchShow>,
    )

    @Serializable
    data class SearchShow(
        @SerialName("name") val name: String,
        @SerialName("originalName") val originalName: String?,
        @SerialName("seasonCount") val seasonCount: Long,
        @SerialName("episodeCount") val episodeCount: Long,
        @SerialName("baseDuration") val baseDuration: Double,
        @SerialName("episodes") val episodes: List<Episode>,
    )

    @Serializable
    data class Episode(
        @SerialName("number") val number: String?,
        @SerialName("absoluteNumber") val absoluteNumber: String?,
        @SerialName("season") val season: String?,
        @SerialName("timestamps") val timestamps: List<Timestamp>,
    )

    @Serializable
    data class Timestamp(
        @SerialName("at") val at: Double,
        @SerialName("type") val type: Type,
    )

    @Serializable
    data class Type(
        @SerialName("name") val name: String,
    )
}
