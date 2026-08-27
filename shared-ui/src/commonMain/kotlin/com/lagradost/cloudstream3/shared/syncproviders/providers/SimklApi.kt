package com.lagradost.cloudstream3.shared.syncproviders.providers

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse.Companion.readIdFromString
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SimklSyncServices
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.models.ListSorting
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager.Companion.APP_STRING
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginPage
import com.lagradost.cloudstream3.shared.syncproviders.AuthPinData
import com.lagradost.cloudstream3.shared.syncproviders.AuthToken
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.shared.syncproviders.SyncAPI
import com.lagradost.cloudstream3.shared.syncproviders.SyncConfig
import com.lagradost.cloudstream3.shared.syncproviders.toYear
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.serializers.NonEmptySerializer
import com.lagradost.cloudstream3.utils.txt
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import java.math.BigInteger
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SimklApi : SyncAPI() {
    override val name = "Simkl"
    override val idPrefix = "simkl"

    override val redirectUrlIdentifier = "simkl"
    override val hasOAuth2 = true
    override val hasPin = true
    override var requireLibraryRefresh = true
    override val mainUrl = "https://api.simkl.com"
    override val icon = Res.drawable.simkl_logo
    override val createAccountUrl = "$mainUrl/signup"
    override val syncIdName = SyncIdName.Simkl

    private var lastScoreTime = -1L

    private object SimklCache {
        private const val SIMKL_CACHE_KEY = "SIMKL_API_CACHE"
        enum class CacheTimes(val value: String) {
            OneMonth("30d"),
            ThirtyMinutes("30m")
        }

        @Serializable
        private data class MediaObjectCacheEntry(
            @SerialName("obj") val obj: MediaObject?,
            @SerialName("validUntil") val validUntil: Long,
            @SerialName("cacheTime") val cacheTime: Long = APIHolder.unixTime,
        )

        @Serializable
        private data class EpisodesCacheEntry(
            @SerialName("obj") val obj: Array<EpisodeMetadata>?,
            @SerialName("validUntil") val validUntil: Long,
            @SerialName("cacheTime") val cacheTime: Long = APIHolder.unixTime,
        )

        @Serializable
        private data class CacheFreshness(
            @SerialName("validUntil") val validUntil: Long,
        )

        private fun Long.isFresh(): Boolean {
            return this > APIHolder.unixTime
        }

        fun cleanOldCache() {
            AppPreferenceManager.getKeysSync(SIMKL_CACHE_KEY).forEach {
                val isOld = AppPreferenceManager.getStringSync(it)?.let { raw ->
                    tryParseJson<CacheFreshness>(raw)?.validUntil?.isFresh() == false
                } ?: false
                if (isOld) AppPreferenceManager.deletePreferenceSync(it)
            }
        }

        fun setMediaObject(path: String, value: MediaObject, cacheTime: Duration) {
            val key = "$SIMKL_CACHE_KEY/$path"
            AppPreferenceManager.setStringSync(
                key,
                MediaObjectCacheEntry(value, APIHolder.unixTime + cacheTime.inWholeSeconds).toJson()
            )
        }

        fun getMediaObject(path: String): MediaObject? {
            val key = "$SIMKL_CACHE_KEY/$path"
            val cache = AppPreferenceManager.getStringSync(key)?.let {
                tryParseJson<MediaObjectCacheEntry>(it)
            }
            return if (cache?.validUntil?.isFresh() == true) {
                cache.obj
            } else {
                AppPreferenceManager.deletePreferenceSync(key)
                null
            }
        }

        fun setEpisodes(path: String, value: Array<EpisodeMetadata>, cacheTime: Duration) {
            val key = "$SIMKL_CACHE_KEY/$path"
            AppPreferenceManager.setStringSync(
                key,
                EpisodesCacheEntry(value, APIHolder.unixTime + cacheTime.inWholeSeconds).toJson()
            )
        }

        fun getEpisodes(path: String): Array<EpisodeMetadata>? {
            val key = "$SIMKL_CACHE_KEY/$path"
            val cache = AppPreferenceManager.getStringSync(key)?.let {
                tryParseJson<EpisodesCacheEntry>(it)
            }
            return if (cache?.validUntil?.isFresh() == true) {
                cache.obj
            } else {
                AppPreferenceManager.deletePreferenceSync(key)
                null
            }
        }
    }

    companion object {
        const val SIMKL_CACHED_LIST: String = "simkl_cached_list"
        const val SIMKL_CACHED_LIST_TIME: String = "simkl_cached_time"

        private val CLIENT_ID get() = SyncConfig.simklClientId
        private val CLIENT_SECRET get() = SyncConfig.simklClientSecret

        private const val SIMKL_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"

        fun getUnixTime(string: String?): Long? {
            return try {
                SimpleDateFormat(SIMKL_DATE_FORMAT, Locale.getDefault()).apply {
                    this.timeZone = TimeZone.getTimeZone("UTC")
                }.parse(string ?: return null)?.time?.toDuration(DurationUnit.MILLISECONDS)
                    ?.toLong(DurationUnit.SECONDS)
            } catch (_: Exception) {
                null
            }
        }

        fun getDateTime(unixTime: Long?): String? {
            return try {
                SimpleDateFormat(SIMKL_DATE_FORMAT, Locale.getDefault()).apply {
                    this.timeZone = TimeZone.getTimeZone("UTC")
                }.format(
                    Date.from(
                        Instant.ofEpochSecond(
                            unixTime ?: return null
                        )
                    )
                )
            } catch (_: Exception) {
                null
            }
        }

        fun getPosterUrl(poster: String): String {
            return "https://wsrv.nl/?url=https://simkl.in/posters/${poster}_m.webp"
        }

        private fun getUrlFromId(id: Int): String {
            return "https://simkl.com/shows/$id"
        }

        enum class SimklListStatusType(
            var value: Int,
            val stringRes: StringResource,
            val originalName: String?,
        ) {
            Watching(0, Res.string.type_watching, "watching"),
            Completed(1, Res.string.type_completed, "completed"),
            Paused(2, Res.string.type_on_hold, "hold"),
            Dropped(3, Res.string.type_dropped, "dropped"),
            Planning(4, Res.string.type_plan_to_watch, "plantowatch"),
            ReWatching(5, Res.string.type_re_watching, "watching"),
            None(-1, Res.string.type_none, null);

            companion object {
                fun fromString(string: String): SimklListStatusType? {
                    return SimklListStatusType.entries.firstOrNull {
                        it.originalName == string
                    }
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = TokenRequest.Serializer::class)
        data class TokenRequest(
            @SerialName("code") val code: String,
            @SerialName("client_id") val clientId: String = CLIENT_ID,
            @SerialName("client_secret") val clientSecret: String = CLIENT_SECRET,
            @SerialName("redirect_uri") val redirectUri: String = "$APP_STRING://simkl",
            @SerialName("grant_type") val grantType: String = "authorization_code",
        ) {
            object Serializer : NonEmptySerializer<TokenRequest>(TokenRequest.generatedSerializer())
        }

        @Serializable
        data class TokenResponse(
            @SerialName("access_token") val accessToken: String,
            @SerialName("token_type") val tokenType: String,
            @SerialName("scope") val scope: String,
        )

        @Serializable
        data class SettingsResponse(
            @SerialName("user") val user: User,
            @SerialName("account") val account: Account,
        ) {
            @Serializable
            data class User(
                @SerialName("name") val name: String,
                @SerialName("avatar") val avatar: String,
            )

            @Serializable
            data class Account(
                @SerialName("id") val id: Int,
            )
        }

        @Serializable
        data class PinAuthResponse(
            @SerialName("result") val result: String,
            @SerialName("device_code") val deviceCode: String,
            @SerialName("user_code") val userCode: String,
            @SerialName("verification_url") val verificationUrl: String,
            @SerialName("expires_in") val expiresIn: Int,
            @SerialName("interval") val interval: Int,
        )

        @Serializable
        data class PinExchangeResponse(
            @SerialName("result") val result: String,
            @SerialName("message") val message: String? = null,
            @SerialName("access_token") val accessToken: String? = null,
        )

        @Serializable
        data class ActivitiesResponse(
            @SerialName("all") val all: String?,
            @SerialName("tv_shows") val tvShows: UpdatedAt,
            @SerialName("anime") val anime: UpdatedAt,
            @SerialName("movies") val movies: UpdatedAt,
        ) {
            @Serializable
            data class UpdatedAt(
                @SerialName("all") val all: String?,
                @SerialName("removed_from_list") val removedFromList: String?,
                @SerialName("rated_at") val ratedAt: String?,
            )
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = EpisodeMetadata.Serializer::class)
        data class EpisodeMetadata(
            @SerialName("title") val title: String?,
            @SerialName("description") val description: String?,
            @SerialName("season") val season: Int?,
            @SerialName("episode") val episode: Int,
            @SerialName("img") val img: String?,
        ) {
            object Serializer : NonEmptySerializer<EpisodeMetadata>(EpisodeMetadata.generatedSerializer())

            companion object {
                fun convertToEpisodes(list: List<EpisodeMetadata>?): List<MediaObject.Season.Episode>? {
                    return list?.map {
                        MediaObject.Season.Episode(it.episode)
                    }
                }

                fun convertToSeasons(list: List<EpisodeMetadata>?): List<MediaObject.Season>? {
                    return list?.filter { it.season != null }?.groupBy {
                        it.season
                    }?.mapNotNull { (season, episodes) ->
                        convertToEpisodes(episodes)?.let { MediaObject.Season(season!!, it) }
                    }?.ifEmpty { null }
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = MediaObject.Serializer::class)
        data class MediaObject(
            @SerialName("title") val title: String?,
            @SerialName("year") val year: Int?,
            @SerialName("ids") val ids: Ids?,
            @SerialName("total_episodes") val totalEpisodes: Int? = null,
            @SerialName("status") val status: String? = null,
            @SerialName("poster") val poster: String? = null,
            @SerialName("type") val type: String? = null,
            @SerialName("seasons") val seasons: List<Season>? = null,
            @SerialName("episodes") val episodes: List<Season.Episode>? = null,
        ) {
            object Serializer : NonEmptySerializer<MediaObject>(MediaObject.generatedSerializer())

            fun hasEnded(): Boolean {
                return status == "released" || status == "ended"
            }

            @OptIn(ExperimentalSerializationApi::class)
            @KeepGeneratedSerializer
            @Serializable(with = Season.Serializer::class)
            data class Season(
                @SerialName("number") val number: Int,
                @SerialName("episodes") val episodes: List<Episode>,
            ) {
                object Serializer : NonEmptySerializer<Season>(Season.generatedSerializer())

                @Serializable
                data class Episode(
                    @SerialName("number") val number: Int,
                )
            }

            @OptIn(ExperimentalSerializationApi::class)
            @KeepGeneratedSerializer
            @Serializable(with = Ids.Serializer::class)
            data class Ids(
                @SerialName("simkl") val simkl: Int?,
                @SerialName("imdb") val imdb: String? = null,
                @SerialName("tmdb") val tmdb: String? = null,
                @SerialName("mal") val mal: String? = null,
                @SerialName("anilist") val anilist: String? = null,
            ) {
                object Serializer : NonEmptySerializer<Ids>(Ids.generatedSerializer())

                companion object {
                    fun fromMap(map: Map<SimklSyncServices, String>): Ids {
                        return Ids(
                            simkl = map[SimklSyncServices.Simkl]?.toIntOrNull(),
                            imdb = map[SimklSyncServices.Imdb],
                            tmdb = map[SimklSyncServices.Tmdb],
                            mal = map[SimklSyncServices.Mal],
                            anilist = map[SimklSyncServices.AniList],
                        )
                    }
                }
            }

            fun toSyncSearchResult(): SyncAPI.SyncSearchResult? {
                val currentIds = this.ids
                return SyncAPI.SyncSearchResult(
                    this.title ?: return null,
                    "Simkl",
                    currentIds?.simkl?.toString() ?: return null,
                    getUrlFromId(currentIds.simkl),
                    this.poster?.let { getPosterUrl(it) },
                    if (this.type == "movie") TvType.Movie else TvType.TvSeries,
                )
            }
        }

        class SimklScoreBuilder private constructor() {
            data class Builder(
                private var url: String? = null,
                private var headers: Map<String, String>? = null,
                private var ids: MediaObject.Ids? = null,
                private var score: Int? = null,
                private var status: Int? = null,
                private var addEpisodes: Pair<List<MediaObject.Season>?, List<MediaObject.Season.Episode>?>? = null,
                private var removeEpisodes: Pair<List<MediaObject.Season>?, List<MediaObject.Season.Episode>?>? = null,
                private var onList: Boolean = false,
            ) {
                fun token(token: AuthToken) = apply { this.headers = getHeaders(token) }
                fun apiUrl(url: String) = apply { this.url = url }
                fun ids(ids: MediaObject.Ids) = apply { this.ids = ids }
                fun score(score: Int?, oldScore: Int?) = apply {
                    if (score != oldScore) {
                        this.score = score
                    }
                }

                fun status(newStatus: Int?, oldStatus: Int?) = apply {
                    onList = oldStatus != null
                    this.status = if (newStatus != oldStatus) newStatus else null
                }

                fun episodes(
                    allEpisodes: List<EpisodeMetadata>?,
                    newEpisodes: Int?,
                    oldEpisodes: Int?,
                ) = apply {
                    if (allEpisodes == null || newEpisodes == null) return@apply
                    fun getEpisodes(rawEpisodes: List<EpisodeMetadata>) =
                        if (rawEpisodes.any { it.season != null }) {
                            EpisodeMetadata.convertToSeasons(rawEpisodes) to null
                        } else {
                            null to EpisodeMetadata.convertToEpisodes(rawEpisodes)
                        }

                    if (newEpisodes > (oldEpisodes ?: 0)) {
                        this.addEpisodes = getEpisodes(allEpisodes.take(newEpisodes))
                        if (!onList) {
                            status = SimklListStatusType.Watching.value
                        }
                    }

                    if ((oldEpisodes ?: 0) > newEpisodes) {
                        this.removeEpisodes = getEpisodes(allEpisodes.drop(newEpisodes))
                    }
                }

                suspend fun execute(): Boolean {
                    val time = getDateTime(APIHolder.unixTime)
                    val headers = this.headers ?: emptyMap()
                    return if (this.status == SimklListStatusType.None.value) {
                        app.post(
                            "$url/sync/history/remove",
                            json = HistoryRequest(
                                shows = listOf(HistoryMediaObject(ids = ids)),
                                movies = emptyList(),
                            ),
                            headers = headers,
                        ).isSuccessful
                    } else {
                        val statusResponse = this.status?.let { setStatus ->
                            val newStatus = SimklListStatusType.entries.firstOrNull {
                                it.value == setStatus
                            }?.originalName ?: SimklListStatusType.Watching.originalName!!
                            app.post(
                                "${this.url}/sync/add-to-list",
                                json = StatusRequest(
                                    shows = listOf(
                                        StatusMediaObject(
                                            null,
                                            null,
                                            ids,
                                            newStatus,
                                        ),
                                    ),
                                    movies = emptyList(),
                                ),
                                headers = headers,
                            ).isSuccessful
                        } ?: true

                        val episodeRemovalResponse = removeEpisodes?.let { (seasons, episodes) ->
                            app.post(
                                "${this.url}/sync/history/remove",
                                json = HistoryRequest(
                                    shows = listOf(
                                        HistoryMediaObject(
                                            ids = ids,
                                            seasons = seasons,
                                            episodes = episodes,
                                        ),
                                    ),
                                    movies = emptyList(),
                                ),
                                headers = headers,
                            ).isSuccessful
                        } ?: true

                        val shouldRate = score != null && status != SimklListStatusType.Planning.value
                        val realScore = if (shouldRate) score else null
                        val historyResponse =
                            if (addEpisodes != null || shouldRate) {
                                app.post(
                                    "${this.url}/sync/history",
                                    json = HistoryRequest(
                                        shows = listOf(
                                            HistoryMediaObject(
                                                null,
                                                null,
                                                ids,
                                                addEpisodes?.first,
                                                addEpisodes?.second,
                                                realScore,
                                                realScore?.let { time },
                                            ),
                                        ),
                                        movies = emptyList(),
                                    ),
                                    headers = headers,
                                ).isSuccessful
                            } else true
                        statusResponse && episodeRemovalResponse && historyResponse
                    }
                }
            }
        }

        fun getHeaders(token: AuthToken): Map<String, String> = mapOf(
            "Authorization" to "Bearer ${token.accessToken}",
            "simkl-api-key" to CLIENT_ID,
        )

        suspend fun getEpisodes(
            simklId: Int?,
            type: String?,
            episodes: Int?,
            hasEnded: Boolean?,
        ): Array<EpisodeMetadata>? {
            if (simklId == null) return null
            val cacheKey = "Episodes/$simklId"
            val cache = SimklCache.getEpisodes(cacheKey)

            if (cache != null && cache.size >= (episodes ?: 0)) {
                return cache
            }

            if (type == "anime" && episodes != null) {
                return episodes.takeIf { it > 0 }?.let {
                    (1..it).map { episode ->
                        EpisodeMetadata(
                            null, null, null, episode, null
                        )
                    }.toTypedArray()
                }
            }

            val url = when (type) {
                "anime" -> "https://api.simkl.com/anime/episodes/$simklId"
                "tv" -> "https://api.simkl.com/tv/episodes/$simklId"
                "movie" -> return null
                else -> return null
            }

            debugPrint { "Requesting episodes from $url" }
            return app.get(url, params = mapOf("client_id" to CLIENT_ID))
                .parsedSafe<Array<EpisodeMetadata>>()?.also {
                    val cacheTime =
                        if (hasEnded == true) SimklCache.CacheTimes.OneMonth.value else SimklCache.CacheTimes.ThirtyMinutes.value
                    SimklCache.setEpisodes(cacheKey, it, Duration.parse(cacheTime))
                }
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = HistoryMediaObject.Serializer::class)
        data class HistoryMediaObject(
            @SerialName("title") val title: String? = null,
            @SerialName("year") val year: Int? = null,
            @SerialName("ids") val ids: MediaObject.Ids? = null,
            @SerialName("seasons") val seasons: List<MediaObject.Season>? = null,
            @SerialName("episodes") val episodes: List<MediaObject.Season.Episode>? = null,
            @SerialName("rating") val rating: Int? = null,
            @SerialName("rated_at") val ratedAt: String? = null,
        ) {
            object Serializer : NonEmptySerializer<HistoryMediaObject>(HistoryMediaObject.generatedSerializer())
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = RatingMediaObject.Serializer::class)
        data class RatingMediaObject(
            @SerialName("title") val title: String? = null,
            @SerialName("year") val year: Int? = null,
            @SerialName("ids") val ids: MediaObject.Ids? = null,
            @SerialName("rating") val rating: Int,
            @SerialName("rated_at") val ratedAt: String? = getDateTime(APIHolder.unixTime),
        ) {
            object Serializer : NonEmptySerializer<RatingMediaObject>(RatingMediaObject.generatedSerializer())
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = StatusMediaObject.Serializer::class)
        data class StatusMediaObject(
            @SerialName("title") val title: String? = null,
            @SerialName("year") val year: Int? = null,
            @SerialName("ids") val ids: MediaObject.Ids? = null,
            @SerialName("to") val to: String,
            @SerialName("watched_at") val watchedAt: String? = getDateTime(APIHolder.unixTime),
        ) {
            object Serializer : NonEmptySerializer<StatusMediaObject>(StatusMediaObject.generatedSerializer())
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = StatusRequest.Serializer::class)
        data class StatusRequest(
            @SerialName("movies") val movies: List<StatusMediaObject>,
            @SerialName("shows") val shows: List<StatusMediaObject>,
        ) {
            object Serializer : NonEmptySerializer<StatusRequest>(StatusRequest.generatedSerializer())
        }

        @OptIn(ExperimentalSerializationApi::class)
        @KeepGeneratedSerializer
        @Serializable(with = HistoryRequest.Serializer::class)
        data class HistoryRequest(
            @SerialName("movies") val movies: List<HistoryMediaObject>,
            @SerialName("shows") val shows: List<HistoryMediaObject>,
        ) {
            object Serializer : NonEmptySerializer<HistoryRequest>(HistoryRequest.generatedSerializer())
        }

        @Serializable
        data class AllItemsResponse(
            @SerialName("shows") val shows: List<ShowMetadata> = emptyList(),
            @SerialName("anime") val anime: List<ShowMetadata> = emptyList(),
            @SerialName("movies") val movies: List<MovieMetadata> = emptyList(),
        ) {
            companion object {
                fun merge(first: AllItemsResponse?, second: AllItemsResponse?): AllItemsResponse {
                    fun <T> MutableList<T>.replaceOrAddItem(newItem: T, predicate: (T) -> Boolean) {
                        for (i in this.indices) {
                            if (predicate(this[i])) {
                                this[i] = newItem
                                return
                            }
                        }
                        this.add(newItem)
                    }

                    fun <T : Metadata> merge(
                        first: List<T>?,
                        second: List<T>?
                    ): List<T> {
                        return (first?.toMutableList() ?: mutableListOf()).apply {
                            second?.forEach { secondShow ->
                                this.replaceOrAddItem(secondShow) {
                                    it.getIds().simkl == secondShow.getIds().simkl
                                }
                            }
                        }
                    }

                    return AllItemsResponse(
                        merge(first?.shows, second?.shows),
                        merge(first?.anime, second?.anime),
                        merge(first?.movies, second?.movies),
                    )
                }
            }

            interface Metadata {
                val lastWatchedAt: String?
                val status: String?
                val userRating: Int?
                val lastWatched: String?
                val watchedEpisodesCount: Int?
                val totalEpisodesCount: Int?

                fun getIds(): ShowMetadata.Show.Ids
                fun toLibraryItem(): SyncAPI.LibraryItem
            }

            @Serializable
            data class MovieMetadata(
                @SerialName("last_watched_at") override val lastWatchedAt: String?,
                @SerialName("status") override val status: String,
                @SerialName("user_rating") override val userRating: Int?,
                @SerialName("last_watched") override val lastWatched: String?,
                @SerialName("watched_episodes_count") override val watchedEpisodesCount: Int?,
                @SerialName("total_episodes_count") override val totalEpisodesCount: Int?,
                @SerialName("movie") val movie: ShowMetadata.Show,
            ) : Metadata {
                override fun getIds(): ShowMetadata.Show.Ids {
                    return this.movie.ids
                }

                override fun toLibraryItem(): SyncAPI.LibraryItem {
                    return SyncAPI.LibraryItem(
                        this.movie.title,
                        "https://simkl.com/tv/${movie.ids.simkl}",
                        movie.ids.simkl.toString(),
                        this.watchedEpisodesCount,
                        this.totalEpisodesCount,
                        Score.from10(this.userRating),
                        getUnixTime(lastWatchedAt) ?: 0,
                        "Simkl",
                        TvType.Movie,
                        this.movie.poster?.let { getPosterUrl(it) },
                        null,
                        null,
                        this.movie.year?.toYear(),
                        movie.ids.simkl,
                    )
                }
            }

            @Serializable
            data class ShowMetadata(
                @SerialName("last_watched_at") override val lastWatchedAt: String?,
                @SerialName("status") override val status: String,
                @SerialName("user_rating") override val userRating: Int?,
                @SerialName("last_watched") override val lastWatched: String?,
                @SerialName("watched_episodes_count") override val watchedEpisodesCount: Int?,
                @SerialName("total_episodes_count") override val totalEpisodesCount: Int?,
                @SerialName("show") val show: Show,
            ) : Metadata {
                override fun getIds(): Show.Ids {
                    return this.show.ids
                }

                override fun toLibraryItem(): SyncAPI.LibraryItem {
                    return SyncAPI.LibraryItem(
                        this.show.title,
                        "https://simkl.com/tv/${show.ids.simkl}",
                        show.ids.simkl.toString(),
                        this.watchedEpisodesCount,
                        this.totalEpisodesCount,
                        Score.from10(this.userRating),
                        getUnixTime(lastWatchedAt) ?: 0,
                        "Simkl",
                        TvType.Anime,
                        this.show.poster?.let { getPosterUrl(it) },
                        null,
                        null,
                        this.show.year?.toYear(),
                        show.ids.simkl,
                    )
                }

                @Serializable
                data class Show(
                    @SerialName("title") val title: String,
                    @SerialName("poster") val poster: String?,
                    @SerialName("year") val year: Int?,
                    @SerialName("ids") val ids: Ids,
                ) {
                    @Serializable
                    data class Ids(
                        @SerialName("simkl") val simkl: Int,
                        @SerialName("slug") val slug: String?,
                        @SerialName("imdb") val imdb: String?,
                        @SerialName("zap2it") val zap2it: String?,
                        @SerialName("tmdb") val tmdb: String?,
                        @SerialName("offen") val offen: String?,
                        @SerialName("tvdb") val tvdb: String?,
                        @SerialName("mal") val mal: String?,
                        @SerialName("anidb") val anidb: String?,
                        @SerialName("anilist") val anilist: String?,
                        @SerialName("traktslug") val traktslug: String?,
                    ) {
                        fun matchesId(database: SimklSyncServices, id: String): Boolean {
                            return when (database) {
                                SimklSyncServices.Simkl -> this.simkl == id.toIntOrNull()
                                SimklSyncServices.AniList -> this.anilist == id
                                SimklSyncServices.Mal -> this.mal == id
                                SimklSyncServices.Tmdb -> this.tmdb == id
                                SimklSyncServices.Imdb -> this.imdb == id
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun getUser(token: AuthToken): SettingsResponse =
        app.post("$mainUrl/users/settings", headers = getHeaders(token))
            .parsed<SettingsResponse>()

    class SimklEpisodeConstructor(
        private val simklId: Int?,
        private val type: String?,
        private val totalEpisodeCount: Int?,
        private val hasEnded: Boolean?,
    ) {
        suspend fun getEpisodes(): Array<EpisodeMetadata>? {
            return getEpisodes(simklId, type, totalEpisodeCount, hasEnded)
        }
    }

    class SimklSyncStatus(
        override var status: SyncWatchType,
        override var score: Score?,
        val oldScore: Int?,
        override var watchedEpisodes: Int?,
        val episodeConstructor: SimklEpisodeConstructor,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
        val oldEpisodes: Int,
        val oldStatus: String?,
    ) : SyncAPI.AbstractSyncStatus()

    override suspend fun status(auth: AuthData?, id: String): SyncAPI.AbstractSyncStatus? {
        if (auth == null) return null
        val realIds = readIdFromString(id)

        val idKey = realIds.toList().map {
            "${it.first.originalName}=${it.second}"
        }.sorted().joinToString()

        val cachedObject = SimklCache.getMediaObject(idKey)
        val searchResult: MediaObject = cachedObject
            ?: (searchByIds(realIds)?.firstOrNull()?.also { result ->
                val cacheTime =
                    if (result.hasEnded()) SimklCache.CacheTimes.OneMonth.value else SimklCache.CacheTimes.ThirtyMinutes.value
                SimklCache.setMediaObject(idKey, result, Duration.parse(cacheTime))
            }) ?: return null

        val episodeConstructor = SimklEpisodeConstructor(
            searchResult.ids?.simkl,
            searchResult.type,
            searchResult.totalEpisodes,
            searchResult.hasEnded(),
        )

        val foundItem = getSyncListSmart(auth)?.let { list ->
            listOf(list.shows, list.anime, list.movies).flatten().firstOrNull { show ->
                realIds.any { (database, id) ->
                    show.getIds().matchesId(database, id)
                }
            }
        }

        if (foundItem != null) {
            return SimklSyncStatus(
                status = foundItem.status?.let {
                    SyncWatchType.fromInternalId(
                        SimklListStatusType.fromString(it)?.value
                    )
                } ?: return null,
                score = Score.from10(foundItem.userRating),
                watchedEpisodes = foundItem.watchedEpisodesCount,
                maxEpisodes = searchResult.totalEpisodes,
                episodeConstructor = episodeConstructor,
                oldEpisodes = foundItem.watchedEpisodesCount ?: 0,
                oldScore = foundItem.userRating,
                oldStatus = foundItem.status,
            )
        } else {
            return SimklSyncStatus(
                status = SyncWatchType.fromInternalId(SimklListStatusType.None.value),
                score = null,
                watchedEpisodes = 0,
                maxEpisodes = if (searchResult.type == "movie") 0 else searchResult.totalEpisodes,
                episodeConstructor = episodeConstructor,
                oldEpisodes = 0,
                oldStatus = null,
                oldScore = null,
            )
        }
    }

    override suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus,
    ): Boolean {
        lastScoreTime = APIHolder.unixTime
        val parsedId = readIdFromString(id)
        val simklStatus = newStatus as? SimklSyncStatus
        val builder = SimklScoreBuilder.Builder()
            .apiUrl(this.mainUrl)
            .score(newStatus.score?.toInt(10), simklStatus?.oldScore)
            .status(
                newStatus.status.internalId,
                (newStatus as? SimklSyncStatus)?.oldStatus?.let { oldStatus ->
                    SimklListStatusType.entries.firstOrNull {
                        it.originalName == oldStatus
                    }?.value
                })
            .token(auth?.token ?: return false)
            .ids(MediaObject.Ids.fromMap(parsedId))

        val episodes = simklStatus?.episodeConstructor?.getEpisodes()

        val watchedEpisodes =
            if (newStatus.status.internalId == SimklListStatusType.Completed.value) {
                episodes?.size
            } else {
                newStatus.watchedEpisodes
            }

        builder.episodes(episodes?.toList(), watchedEpisodes, simklStatus?.oldEpisodes)
        requireLibraryRefresh = true
        return builder.execute()
    }

    private suspend fun searchByIds(serviceMap: Map<SimklSyncServices, String>): Array<MediaObject>? {
        if (serviceMap.isEmpty()) return emptyArray()
        return app.get(
            "$mainUrl/search/id",
            params = mapOf("client_id" to CLIENT_ID) + serviceMap.map { (service, id) ->
                service.originalName to id
            }
        ).parsedSafe<Array<MediaObject>>()
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncAPI.SyncSearchResult>? {
        return app.get(
            "$mainUrl/search/", params = mapOf("client_id" to CLIENT_ID, "q" to query)
        ).parsedSafe<Array<MediaObject>>()?.mapNotNull { it.toSyncSearchResult() }
    }

    override fun loginRequest(): AuthLoginPage? {
        val lastLoginState = BigInteger(130, SecureRandom()).toString(32)
        val url = "https://simkl.com/oauth/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$APP_STRING://$redirectUrlIdentifier&state=$lastLoginState"
        return AuthLoginPage(
            url = url,
            payload = lastLoginState,
        )
    }

    override suspend fun load(auth: AuthData?, id: String): SyncResult? = null

    private suspend fun getSyncListSince(auth: AuthData, since: Long?): AllItemsResponse? {
        val params = getDateTime(since)?.let {
            mapOf("date_from" to it)
        } ?: emptyMap()

        return app.get(
            "$mainUrl/sync/all-items/",
            params = params,
            headers = getHeaders(auth.token),
        ).parsedSafe<AllItemsResponse>()
    }

    private suspend fun getActivities(token: AuthToken): ActivitiesResponse? {
        return app.post("$mainUrl/sync/activities", headers = getHeaders(token)).parsedSafe<ActivitiesResponse>()
    }

    private fun getSyncListCached(auth: AuthData): AllItemsResponse? {
        val raw = AppPreferenceManager.getStringSync("$SIMKL_CACHED_LIST/${auth.user.id}") ?: return null
        return tryParseJson<AllItemsResponse>(raw)
    }

    private suspend fun getSyncListSmart(auth: AuthData): AllItemsResponse? {
        val activities = getActivities(auth.token)
        val userId = auth.user.id.toString()
        val lastCacheUpdate = AppPreferenceManager.getStringSync("$SIMKL_CACHED_LIST_TIME/$userId")?.toLongOrNull()
        val lastRemoval = listOf(
            activities?.tvShows?.removedFromList,
            activities?.anime?.removedFromList,
            activities?.movies?.removedFromList,
        ).maxOf { getUnixTime(it) ?: -1 }
        val lastRealUpdate = listOf(
            activities?.tvShows?.all,
            activities?.anime?.all,
            activities?.movies?.all,
        ).maxOf { getUnixTime(it) ?: -1 }

        debugPrint { "Cache times: lastCacheUpdate=$lastCacheUpdate, lastRemoval=$lastRemoval, lastRealUpdate=$lastRealUpdate" }
        val list = if (lastCacheUpdate == null || lastCacheUpdate < lastRemoval) {
            debugPrint { "Full list update in ${this.name}." }
            AppPreferenceManager.setStringSync("$SIMKL_CACHED_LIST_TIME/$userId", lastRemoval.toString())
            getSyncListSince(auth, null)
        } else if (lastCacheUpdate < lastRealUpdate || lastCacheUpdate < lastScoreTime) {
            debugPrint { "Partial list update in ${this.name}." }
            AppPreferenceManager.setStringSync("$SIMKL_CACHED_LIST_TIME/$userId", lastCacheUpdate.toString())
            AllItemsResponse.merge(
                getSyncListCached(auth),
                getSyncListSince(auth, lastCacheUpdate),
            )
        } else {
            debugPrint { "Cached list update in ${this.name}." }
            getSyncListCached(auth)
        }

        debugPrint { "List sizes: movies=${list?.movies?.size}, shows=${list?.shows?.size}, anime=${list?.anime?.size}" }
        if (list != null) {
            AppPreferenceManager.setStringSync("$SIMKL_CACHED_LIST/$userId", list.toJson())
        }
        return list
    }

    override suspend fun library(auth: AuthData?): SyncAPI.LibraryMetadata? {
        val list = getSyncListSmart(auth ?: return null) ?: return null
        val baseMap = SimklListStatusType.entries
            .filter { it.value >= 0 && it.value != SimklListStatusType.ReWatching.value }
            .associate {
                it.stringRes to emptyList<SyncAPI.LibraryItem>()
            }

        val syncMap = listOf(list.anime, list.movies, list.shows)
            .flatten()
            .groupBy { it.status }
            .mapNotNull { (status, list) ->
                val stringRes = status?.let {
                    SimklListStatusType.fromString(it)?.stringRes
                } ?: return@mapNotNull null
                val libraryList = list.map { it.toLibraryItem() }
                stringRes to libraryList
            }.toMap()

        return SyncAPI.LibraryMetadata(
            (baseMap + syncMap).map { SyncAPI.LibraryList(txt(it.key), it.value) }, setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.ReleaseDateNew,
                ListSorting.ReleaseDateOld,
                ListSorting.RatingHigh,
                ListSorting.RatingLow,
            )
        )
    }

    override fun urlToId(url: String): String? {
        val simklUrlRegex = Regex("""https://simkl\.com/[^/]*/(\d+).*""")
        return simklUrlRegex.find(url)?.groupValues?.get(1) ?: ""
    }

    override suspend fun pinRequest(): AuthPinData? {
        val pinAuthResp = app.get(
            "$mainUrl/oauth/pin?client_id=$CLIENT_ID&redirect_uri=$APP_STRING://$redirectUrlIdentifier"
        ).parsedSafe<PinAuthResponse>() ?: return null
        return AuthPinData(
            deviceCode = pinAuthResp.deviceCode,
            userCode = pinAuthResp.userCode,
            verificationUrl = pinAuthResp.verificationUrl,
            expiresIn = pinAuthResp.expiresIn,
            interval = pinAuthResp.interval,
        )
    }

    override suspend fun login(payload: AuthPinData): AuthToken? {
        val pinAuthResp = app.get(
            "$mainUrl/oauth/pin/${payload.userCode}?client_id=$CLIENT_ID"
        ).parsedSafe<PinExchangeResponse>() ?: return null
        return AuthToken(
            accessToken = pinAuthResp.accessToken ?: return null,
        )
    }

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val sanitizer = splitRedirectUrl(redirectUrl)
        val state = sanitizer["state"]
        if (state != payload) return null

        val code = sanitizer["code"] ?: return null
        val tokenResponse = app.post(
            "$mainUrl/oauth/token", json = TokenRequest(code)
        ).parsedSafe<TokenResponse>() ?: return null
        return AuthToken(
            accessToken = tokenResponse.accessToken,
        )
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = getUser(token ?: return null)
        return AuthUser(
            id = user.account.id,
            name = user.user.name,
            profilePicture = user.user.avatar,
        )
    }
}
