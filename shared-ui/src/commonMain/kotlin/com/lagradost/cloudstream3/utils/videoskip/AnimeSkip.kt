package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager.Companion.animeSkipApi
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.syncproviders.providers.AnimeSkipAuth
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

class AnimeSkip : SkipAPI() {
    override val name: String = "AniSkip"
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.OVA)

    val auth = AuthRepo(animeSkipApi)
    //val clientId = "ZGfO0sMF3eCwLYf8yMSCJjlynwNGRXWE"

    companion object {
        const val MIN_LENGTH: Int = 4

        private val strip = Regex("[ :\\-.!]")

        /** Makes names more uniform to make partial matches more still give a result */
        fun stripName(name: String?): String? =
            name?.replace(strip, "")?.lowercase()

        private val asciiRegex = Regex("[^a-zA-Z0-9 ]")

        /** Makes names more uniform to make partial matches more still give a result */
        fun asciiName(name: String?): String? =
            name?.replace(asciiRegex, "")?.lowercase()
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

    val cache: ConcurrentHashMap<String, Data> = ConcurrentHashMap()

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val clientId : String = parseJson<AnimeSkipAuth.Payload>(
            auth.authData()?.token?.payload ?: return null
        ).clientId

        when (data) {
            is AnimeLoadResponse, is TvSeriesLoadResponse -> {
                /** Require episode based anime */
            }

            else -> return null
        }

        val query = """{
  searchShows(search: "${data.name}", limit: 1) {
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
        val root = cache[data.name] ?: run {
            app.post(
                "https://api.anime-skip.com/graphql",
                json = mapOf("query" to query),
                headers = mapOf(
                    "Accept" to "*/*",
                    "content-type" to "application/json",
                    "X-Client-ID" to clientId
                )
            )
                .parsed<Root>().data.also { root ->
                    cache[data.name] = root
                }
        }
        val show = root.searchShows.firstOrNull { show ->
            /** Match ascii */
            val ascii1 = asciiName(data.name)
            val ascii2 = asciiName(show.name)
            if (ascii1 == ascii2 && (ascii1?.length ?: 0) > MIN_LENGTH) {
                return@firstOrNull true
            }

            if (data !is AnimeLoadResponse) {
                return@firstOrNull false
            }

            /** Match original name */
            val strip1 = stripName(show.originalName)
            val strip2 = stripName(data.japName)

            /** Match english name*/
            val ascii3 = stripName(data.engName)
            (strip1 == strip2 && (strip1?.length ?: 0) > MIN_LENGTH) ||
                    (ascii2 == ascii3 && (ascii2?.length ?: 0) > MIN_LENGTH)
        } ?: return null

        val showEpisode = when (data) {
            is AnimeLoadResponse -> {
                val episodeNumber = episode.episode.toString()
                /** For anime, match on number */
                show.episodes.firstOrNull {
                    it.absoluteNumber == episodeNumber
                } ?: show.episodes.firstOrNull {
                    it.number == episodeNumber
                }
            }

            is TvSeriesLoadResponse -> {
                /** For tv-series, match on season + number */
                val seasonNumber = episode.season?.toString()
                val episodeNumber = episode.episode.toString()
                val episodeIndex = episode.totalEpisodeIndex.toString()

                show.episodes.firstOrNull {
                    it.season == seasonNumber && it.number == episodeNumber
                } ?: show.episodes.firstOrNull {
                    it.absoluteNumber == episodeIndex
                }
            }

            else -> null
        } ?: return null

        val result = ArrayList<SkipStamp>()
        var pending: SkipStamp? = null
        for (stamp in showEpisode.timestamps) {
            val startMS = (stamp.at * 1000.0).toLong()
            pending?.let { pending ->
                result.add(pending.copy(endMs = startMS))
            }
            val type = when (stamp.type.name) {
                "Intro", "New Intro" -> SkipType.Intro
                "Credits" -> SkipType.Credits
                "Preview" -> SkipType.Preview
                "Recap" -> SkipType.Recap
                "Mixed Credits" -> SkipType.MixedEnding
                "Filler", "Transition", "Branding", "Canon", "Title Card" -> null
                else -> null
            }
            if (type == null) {
                pending = null
                continue
            }
            pending = SkipStamp(type, startMS, 0L)
        }
        pending?.let { pending ->
            result.add(pending.copy(endMs = episodeDurationMs))
            /** Base duration = fucked */
        }

        return result
    }
}
