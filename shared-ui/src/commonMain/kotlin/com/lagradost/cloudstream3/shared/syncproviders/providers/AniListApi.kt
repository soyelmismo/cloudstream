package com.lagradost.cloudstream3.shared.syncproviders.providers

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.models.ListSorting
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginPage
import com.lagradost.cloudstream3.shared.syncproviders.AuthToken
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.shared.syncproviders.SyncAPI
import com.lagradost.cloudstream3.shared.syncproviders.SyncConfig
import com.lagradost.cloudstream3.shared.syncproviders.toYear
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.txt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import java.net.URLEncoder
import java.util.Locale

class AniListApi : SyncAPI() {
    override var name = "AniList"
    override val idPrefix = "anilist"

    private val key get() = SyncConfig.anilistKey
    override val redirectUrlIdentifier = "anilistlogin"
    override var requireLibraryRefresh = true
    override val hasOAuth2 = true
    override var mainUrl = "https://anilist.co"
    override val icon = Res.drawable.ic_anilist_icon
    override val createAccountUrl = "$mainUrl/signup"
    override val syncIdName = SyncIdName.Anilist

    override fun loginRequest(): AuthLoginPage? =
        AuthLoginPage("https://anilist.co/api/v2/oauth/authorize?client_id=$key&response_type=token")

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val trimmed = redirectUrl.trim()
        val tokenStr = if (trimmed.startsWith("eyJ") && !trimmed.contains("=")) {
            trimmed
        } else {
            val sanitizer = splitRedirectUrl(trimmed)
            sanitizer["access_token"]
                ?: if (trimmed.contains("access_token=")) {
                    trimmed.substringAfter("access_token=").substringBefore("&")
                } else {
                    throw ErrorLoadingException("No access token found in redirect URL")
                }
        }
        val sanitizer = splitRedirectUrl(trimmed)
        val expiresIn = sanitizer["expires_in"]?.toLongOrNull() ?: 31536000L
        return AuthToken(
            accessToken = tokenStr,
            accessTokenLifetime = APIHolder.unixTime + expiresIn,
        )
    }

    // https://docs.anilist.co/guide/auth/
    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        // AniList access tokens are long-lived. They will remain valid for 1 year from the time they are issued.
        // Refresh tokens are not currently supported. Once a token expires, you will need to re-authenticate your users.
        return super.refreshToken(token)
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = getUser(token ?: return null)
            ?: throw ErrorLoadingException("Unable to fetch user data")

        return AuthUser(
            id = user.id,
            name = user.name,
            profilePicture = user.picture,
        )
    }

    override fun urlToId(url: String): String? =
        url.removePrefix("$mainUrl/anime/").removeSuffix("/")

    private fun getUrlFromId(id: Int): String {
        return "$mainUrl/anime/$id"
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncAPI.SyncSearchResult>? {
        val data = searchShows(query) ?: return null
        return data.data?.page?.media?.map {
            SyncAPI.SyncSearchResult(
                it.title.romaji ?: return null,
                this.name,
                it.id.toString(),
                getUrlFromId(it.id),
                it.bannerImage
            )
        }
    }

    override suspend fun load(auth: AuthData?, id: String): SyncAPI.SyncResult? {
        val internalId = (Regex("anilist\\.co/anime/(\\d*)").find(id)?.groupValues?.getOrNull(1)
            ?: id).toIntOrNull() ?: throw ErrorLoadingException("Invalid internalId")
        val season = getSeason(internalId).data.media
        return SyncAPI.SyncResult(
            season.id.toString(),
            nextAiring = season.nextAiringEpisode?.let {
                NextAiring(
                    it.episode ?: return@let null,
                    (it.timeUntilAiring ?: return@let null) + APIHolder.unixTime
                )
            },
            title = season.title?.userPreferred,
            synonyms = season.synonyms,
            isAdult = season.isAdult,
            totalEpisodes = season.episodes,
            synopsis = season.description,
            actors = season.characters?.edges?.mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                ActorData(
                    actor = Actor(
                        name = node.name?.userPreferred ?: node.name?.full ?: node.name?.native
                        ?: return@mapNotNull null,
                        image = node.image?.large ?: node.image?.medium
                    ),
                    role = when (edge.role) {
                        "MAIN" -> ActorRole.Main
                        "SUPPORTING" -> ActorRole.Supporting
                        "BACKGROUND" -> ActorRole.Background
                        else -> null
                    },
                    voiceActor = edge.voiceActors?.firstNotNullOfOrNull { staff ->
                        Actor(
                            name = staff.name?.userPreferred ?: staff.name?.full
                            ?: staff.name?.native
                            ?: return@mapNotNull null,
                            image = staff.image?.large ?: staff.image?.medium
                        )
                    }
                )
            },
            publicScore = Score.from100(season.averageScore),
            recommendations = season.recommendations?.edges?.mapNotNull { rec ->
                val recMedia = rec.node.mediaRecommendation
                SyncAPI.SyncSearchResult(
                    name = recMedia?.title?.userPreferred ?: return@mapNotNull null,
                    this.name,
                    recMedia.id?.toString() ?: return@mapNotNull null,
                    getUrlFromId(recMedia.id),
                    recMedia.coverImage?.extraLarge ?: recMedia.coverImage?.large
                    ?: recMedia.coverImage?.medium
                )
            },
            trailers = when (season.trailer?.site?.lowercase()?.trim()) {
                "youtube" -> listOf("https://www.youtube.com/watch?v=${season.trailer.id}")
                else -> null
            }
            // TODO REST
        )
    }

    override suspend fun status(auth: AuthData?, id: String): SyncAPI.AbstractSyncStatus? {
        val internalId = id.toIntOrNull() ?: return null
        val data = getDataAboutId(auth ?: return null, internalId) ?: return null
        return SyncAPI.SyncStatus(
            score = Score.from100(data.score),
            watchedEpisodes = data.progress,
            status = SyncWatchType.fromInternalId(data.type?.value ?: return null),
            isFavorite = data.isFavourite,
            maxEpisodes = data.episodes,
        )
    }

    override suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean {
        return postDataAboutId(
            auth ?: return false,
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(newStatus.status.internalId),
            newStatus.score,
            newStatus.watchedEpisodes
        )
    }

    companion object {
        const val MAX_STALE = 60 * 10
        private val aniListStatusString =
            arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        const val ANILIST_CACHED_LIST: String = "anilist_cached_list"

        private fun fixName(name: String): String {
            return name.lowercase(Locale.ROOT).replace(" ", "")
                .replace("[^a-zA-Z0-9]".toRegex(), "")
        }

        private suspend fun searchShows(name: String): GetSearchRoot? {
            try {
                val query = """
                query (${"$"}id: Int, ${"$"}page: Int, ${"$"}search: String, ${"$"}type: MediaType) {
                    Page (page: ${"$"}page, perPage: 10) {
                        media (id: ${"$"}id, search: ${"$"}search, type: ${"$"}type) {
                            id
                            idMal
                            seasonYear
                            startDate { year month day }
                            title {
                                romaji
                            }
                            averageScore
                            meanScore
                            nextAiringEpisode {
                                timeUntilAiring
                                episode
                            }
                            trailer { id site thumbnail }
                            bannerImage
                            recommendations {
                                nodes {
                                    id
                                    mediaRecommendation {
                                        id
                                        title {
                                            english
                                            romaji
                                        }
                                        idMal
                                        coverImage { medium large extraLarge }
                                        averageScore
                                    }
                                }
                            }
                            relations {
                                edges {
                                    id
                                    relationType(version: 2)
                                    node {
                                        format
                                        id
                                        idMal
                                        coverImage { medium large extraLarge }
                                        averageScore
                                        title {
                                            english
                                            romaji
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """
                val data =
                    mapOf(
                        "query" to query,
                        "variables" to Variables(
                            search = name,
                            page = 1,
                            type = "ANIME",
                        ).toJson()
                    )

                val res = app.post(
                    "https://graphql.anilist.co/",
                    data = data,
                    timeout = 5000
                ).text.replace("\\", "")
                return parseJson<GetSearchRoot>(res)
            } catch (e: Exception) {
                logError(e)
            }

            return null
        }

        suspend fun getShowId(malId: String?, name: String, year: Int?): GetSearchMedia? {
            val blackList = listOf(
                "TV Dubbed",
                "(Dub)",
                "Subbed",
                "(TV)",
                "(Uncensored)",
                "(Censored)",
                "(\\d+)"
            )
            val blackListRegex =
                Regex(
                    """ (${
                        blackList.joinToString(separator = "|").replace("(", "\\(")
                            .replace(")", "\\)")
                    })"""
                )
            val shows = searchShows(name.replace(blackListRegex, ""))

            shows?.data?.page?.media?.find {
                (malId ?: "NONE") == it.idMal.toString()
            }?.let { return it }

            val filtered =
                shows?.data?.page?.media?.filter {
                    (((it.startDate.year ?: year.toString()) == year.toString()
                            || year == null))
                }
            filtered?.forEach {
                it.title.romaji?.let { romaji ->
                    if (fixName(romaji) == fixName(name)) return it
                }
            }

            return filtered?.firstOrNull()
        }

        enum class AniListStatusType(var value: Int, val stringRes: StringResource) {
            Watching(0, Res.string.type_watching),
            Completed(1, Res.string.type_completed),
            Paused(2, Res.string.type_on_hold),
            Dropped(3, Res.string.type_dropped),
            Planning(4, Res.string.type_plan_to_watch),
            ReWatching(5, Res.string.type_re_watching),
            None(-1, Res.string.type_none)
        }

        fun fromIntToAnimeStatus(inp: Int): AniListStatusType {
            return when (inp) {
                -1 -> AniListStatusType.None
                0 -> AniListStatusType.Watching
                1 -> AniListStatusType.Completed
                2 -> AniListStatusType.Paused
                3 -> AniListStatusType.Dropped
                4 -> AniListStatusType.Planning
                5 -> AniListStatusType.ReWatching
                else -> AniListStatusType.None
            }
        }

        fun convertAniListStringToStatus(string: String): AniListStatusType {
            return fromIntToAnimeStatus(aniListStatusString.indexOf(string))
        }

        private suspend fun getSeason(id: Int): SeasonResponse {
            val q = """
               query (${'$'}id: Int = $id) {
                   Media (id: ${'$'}id, type: ANIME) {
                       id
                       idMal
                       coverImage {
                           extraLarge
                           large
                           medium
                           color
                       }
                       title {
                           romaji
                           english
                           native
                           userPreferred
                       }
                       duration
                       episodes
                       genres
                       synonyms
                       averageScore
                       isAdult
                       description(asHtml: false)
                       characters(sort: ROLE page: 1 perPage: 20) {
                           edges {
                               role
                               voiceActors {
                                   name {
                                       userPreferred
                                       full
                                       native
                                   }
                                   age
                                   image {
                                       large
                                       medium
                                   }
                               }
                               node {
                                   name {
                                       userPreferred
                                       full
                                       native
                                   }
                                   age
                                   image {
                                       large
                                       medium
                                   }
                               }
                           }
                       }
                       trailer {
                           id
                           site
                           thumbnail
                       }
                       relations {
                            edges {
                                 id
                                 relationType(version: 2)
                                 node {
                                      id
                                      coverImage {
                                          extraLarge
                                          large
                                          medium
                                          color
                                      }
                                 }
                            }
                       }
                       recommendations {
                           edges {
                               node {
                                   mediaRecommendation {
                                       id
                                       coverImage {
                                           extraLarge
                                           large
                                           medium
                                           color
                                       }
                                       title {
                                           romaji
                                           english
                                           native
                                           userPreferred
                                       }
                                   }
                               }
                           }
                       }
                       nextAiringEpisode {
                           timeUntilAiring
                           episode
                       }
                       format
                   }
               }
        """
            val data = app.post(
                "https://graphql.anilist.co",
                data = mapOf("query" to q),
                cacheTime = 0,
            ).text

            return tryParseJson<SeasonResponse>(data) ?: throw ErrorLoadingException("Error parsing $data")
        }
    }

    private suspend fun getDataAboutId(auth: AuthData, id: Int): AniListTitleHolder? {
        val q =
            """query (${'$'}id: Int = $id) {
                Media (id: ${'$'}id, type: ANIME) {
                    id
                    episodes
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_100)
                    }
                    title {
                        english
                        romaji
                    }
                }
            }"""

        val data = postApi(auth.token, q, true)
        val d = parseJson<GetDataRoot>(data ?: return null)

        val main = d.data?.media
        if (main?.mediaListEntry != null) {
            return AniListTitleHolder(
                title = main.title,
                id = id,
                isFavourite = main.isFavourite,
                progress = main.mediaListEntry.progress,
                episodes = main.episodes,
                score = main.mediaListEntry.score,
                type = fromIntToAnimeStatus(aniListStatusString.indexOf(main.mediaListEntry.status)),
            )
        } else {
            return AniListTitleHolder(
                title = main?.title,
                id = id,
                isFavourite = main?.isFavourite,
                progress = 0,
                episodes = main?.episodes,
                score = 0,
                type = AniListStatusType.None,
            )
        }
    }

    private suspend fun postApi(token: AuthToken, q: String, cache: Boolean = false): String? {
        return app.post(
            "https://graphql.anilist.co/",
            headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken ?: return null}",
                if (cache) "Cache-Control" to "max-stale=$MAX_STALE" else "Cache-Control" to "no-cache"
            ),
            cacheTime = 0,
            data = mapOf(
                "query" to URLEncoder.encode(
                    q,
                    "UTF-8"
                )
            ),
            timeout = 5
        ).text.replace("\\/", "/")
    }

    @Serializable
    data class Variables(
        @SerialName("search") val search: String,
        @SerialName("page") val page: Int,
        @SerialName("type") val type: String,
    )

    @Serializable
    data class MediaRecommendation(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: Title?,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("coverImage") val coverImage: CoverImage?,
        @SerialName("averageScore") val averageScore: Int?,
    )

    @Serializable
    data class FullAnilistList(
        @SerialName("data") val data: Data?,
    )

    @Serializable
    data class CompletedAt(
        @SerialName("year") val year: Int? = null,
        @SerialName("month") val month: Int? = null,
        @SerialName("day") val day: Int? = null,
    )

    @Serializable
    data class StartedAt(
        @SerialName("year") val year: Int? = null,
        @SerialName("month") val month: Int? = null,
        @SerialName("day") val day: Int? = null,
    )

    @Serializable
    data class Title(
        @SerialName("english") val english: String? = null,
        @SerialName("romaji") val romaji: String? = null,
    )

    @Serializable
    data class CoverImage(
        @SerialName("medium") val medium: String? = null,
        @SerialName("large") val large: String? = null,
        @SerialName("extraLarge") val extraLarge: String? = null,
    )

    @Serializable
    data class Media(
        @SerialName("id") val id: Int,
        @SerialName("idMal") val idMal: Int? = null,
        @SerialName("season") val season: String? = null,
        @SerialName("seasonYear") val seasonYear: Int? = null,
        @SerialName("format") val format: String? = null,
        @SerialName("episodes") val episodes: Int? = null,
        @SerialName("title") val title: Title? = null,
        @SerialName("description") val description: String? = null,
        @SerialName("coverImage") val coverImage: CoverImage? = null,
        @SerialName("synonyms") val synonyms: List<String> = emptyList(),
        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode? = null,
    )

    @Serializable
    data class Entries(
        @SerialName("status") val status: String? = null,
        @SerialName("completedAt") val completedAt: CompletedAt? = null,
        @SerialName("startedAt") val startedAt: StartedAt? = null,
        @SerialName("updatedAt") val updatedAt: Long? = null,
        @SerialName("progress") val progress: Int? = null,
        @SerialName("score") val score: Double? = null,
        @SerialName("private") val private: Boolean? = null,
        @SerialName("media") val media: Media,
    ) {
        fun toLibraryItem(): SyncAPI.LibraryItem {
            val titleName = this.media.title?.english ?: this.media.title?.romaji
            ?: this.media.synonyms.firstOrNull()
            ?: ""
            val poster = this.media.coverImage?.extraLarge ?: this.media.coverImage?.large
            ?: this.media.coverImage?.medium
            val scoreVal = this.score?.toInt() ?: 0
            return SyncAPI.LibraryItem(
                name = titleName,
                url = "https://anilist.co/anime/${this.media.id}/",
                syncId = this.media.id.toString(),
                episodesCompleted = this.progress ?: 0,
                episodesTotal = this.media.episodes,
                personalRating = Score.from100(scoreVal),
                lastUpdatedUnixTime = this.updatedAt ?: 0L,
                apiName = "AniList",
                type = TvType.Anime,
                posterUrl = poster,
                posterHeaders = null,
                quality = null,
                releaseDate = this.media.seasonYear?.toYear(),
                plot = this.media.description,
            )
        }
    }

    @Serializable
    data class Lists(
        @SerialName("status") val status: String?,
        @SerialName("entries") val entries: List<Entries>,
    )

    @Serializable
    data class MediaListCollection(
        @SerialName("lists") val lists: List<Lists>,
    )

    @Serializable
    data class Data(
        @SerialName("MediaListCollection") val mediaListCollection: MediaListCollection,
    )

    private suspend fun getAniListAnimeListSmart(auth: AuthData): Array<Lists>? {
        val prefKey = "$ANILIST_CACHED_LIST/${auth.user.id}"
        return if (requireLibraryRefresh) {
            val list = getFullAniListList(auth)?.data?.mediaListCollection?.lists?.toTypedArray()
            if (list != null) {
                AppPreferenceManager.setStringSync(prefKey, list.toJson())
            }
            list
        } else {
            val raw = AppPreferenceManager.getStringSync(prefKey) ?: return null
            tryParseJson<Array<Lists>>(raw)
        }
    }

    override suspend fun library(auth: AuthData?): SyncAPI.LibraryMetadata? {
        val list = getAniListAnimeListSmart(auth ?: return null)?.groupBy {
            convertAniListStringToStatus(it.status ?: "").stringRes
        }?.mapValues { group ->
            group.value.map { it.entries.map { entry -> entry.toLibraryItem() } }.flatten()
        } ?: emptyMap()

        val baseMap =
            AniListStatusType.entries.filter { it.value >= 0 }.associate {
                it.stringRes to emptyList<SyncAPI.LibraryItem>()
            }

        return SyncAPI.LibraryMetadata(
            (baseMap + list).map { SyncAPI.LibraryList(txt(it.key), it.value) },
            setOf(
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

    private suspend fun getFullAniListList(auth: AuthData): FullAnilistList? {
        val userID = auth.user.id
        val mediaType = "ANIME"
        val query = """
                query (${'$'}userID: Int = $userID, ${'$'}MEDIA: MediaType = $mediaType) {
                    MediaListCollection (userId: ${'$'}userID, type: ${'$'}MEDIA) { 
                        lists {
                            status
                            entries
                            {
                                status
                                completedAt { year month day }
                                startedAt { year month day }
                                updatedAt
                                progress
                                score (format: POINT_100)
                                private
                                media
                                {
                                    id
                                    idMal
                                    season
                                    seasonYear
                                    format
                                    episodes
                                    chapters
                                    title
                                    {
                                        english
                                        romaji
                                    }
                                    coverImage { extraLarge large medium }
                                    synonyms
                                    nextAiringEpisode {
                                        timeUntilAiring
                                        episode
                                    }
                                }
                            }
                        }
                    }
                }
            """
        val text = postApi(auth.token, query)
        return tryParseJson<FullAnilistList>(text)
    }

    suspend fun toggleLike(auth: AuthData, id: Int): Boolean {
        val q = """mutation (${'$'}animeId: Int = $id) {
                ToggleFavourite (animeId: ${'$'}animeId) {
                    anime {
                        nodes {
                            id
                            title {
                                romaji
                            }
                        }
                    }
                }
            }"""
        val data = postApi(auth.token, q)
        return data != ""
    }

    @Serializable
    data class MediaListItemRoot(
        @SerialName("data") val data: MediaListItem? = null,
    )

    @Serializable
    data class MediaListItem(
        @SerialName("MediaList") val mediaList: MediaListId? = null,
    )

    @Serializable
    data class MediaListId(
        @SerialName("id") val id: Long? = null,
    )

    private suspend fun postDataAboutId(
        auth: AuthData,
        id: Int,
        type: AniListStatusType,
        score: Score?,
        progress: Int?
    ): Boolean {
        val userID = auth.user.id
        val q =
            if (type == AniListStatusType.None) {
                val idQuery = """
                  query MediaList(${'$'}userId: Int = $userID, ${'$'}mediaId: Int = $id) {
                    MediaList(userId: ${'$'}userId, mediaId: ${'$'}mediaId) {
                      id
                    }
                  }
                """
                val response = postApi(auth.token, idQuery)
                val listId =
                    tryParseJson<MediaListItemRoot>(response)?.data?.mediaList?.id ?: return false
                """
                    mutation(${'$'}id: Int = $listId) {
                        DeleteMediaListEntry(id: ${'$'}id) {
                            deleted
                        }
                    }
                """
            } else {
                """mutation (${'$'}id: Int = $id, ${'$'}status: MediaListStatus = ${
                    aniListStatusString[maxOf(
                        0,
                        type.value
                    )]
                }, ${if (score != null) "${'$'}scoreRaw: Int = ${score.toInt(100)}" else ""} , ${if (progress != null) "${'$'}progress: Int = $progress" else ""}) {
                    SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                        id
                        status
                        progress
                        score
                    }
                }"""
            }

        val data = postApi(auth.token, q)
        return data != ""
    }

    private suspend fun getUser(token: AuthToken): AniListUser? {
        val q = """
            {
                Viewer {
                    id
                    name
                    avatar {
                        large
                    }
                    favourites {
                        anime {
                            nodes {
                                id
                            }
                        }
                    }
                }
            }"""
        val data = postApi(token, q)
        if (data.isNullOrBlank()) return null
        val userData = parseJson<AniListRoot>(data)
        val u = userData.data?.viewer ?: return null
        val user = AniListUser(
            u.id,
            u.name,
            u.avatar?.large,
        )
        return user
    }

    suspend fun getAllSeasons(id: Int): List<SeasonResponse?> {
        val seasons = mutableListOf<SeasonResponse?>()
        suspend fun getSeasonRecursive(id: Int) {
            val season = getSeason(id)
            seasons.add(season)
            if (season.data.media.format?.startsWith("TV") == true) {
                season.data.media.relations?.edges?.forEach {
                    if (it.node?.format != null) {
                        if (it.relationType == "SEQUEL" && it.node.format.startsWith("TV")) {
                            getSeasonRecursive(it.node.id)
                            return@forEach
                        }
                    }
                }
            }
        }
        getSeasonRecursive(id)
        return seasons.toList()
    }

    @Serializable
    data class SeasonResponse(
        @SerialName("data") val data: SeasonData,
    )

    @Serializable
    data class SeasonData(
        @SerialName("Media") val media: SeasonMedia,
    )

    @Serializable
    data class RecommendedMedia(
        @SerialName("id") val id: Int?,
        @SerialName("title") val title: MediaTitle?,
        @SerialName("coverImage") val coverImage: MediaCoverImage?,
    )

    @Serializable
    data class CharacterMedia(
        @SerialName("id") val id: Int?,
        @SerialName("title") val title: MediaTitle?,
        @SerialName("coverImage") val coverImage: MediaCoverImage?,
    )

    @Serializable
    data class SeasonMedia(
        @SerialName("id") val id: Int?,
        @SerialName("title") val title: MediaTitle?,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("format") val format: String?,
        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @SerialName("relations") val relations: SeasonEdges?,
        @SerialName("coverImage") val coverImage: MediaCoverImage?,
        @SerialName("duration") val duration: Int?,
        @SerialName("episodes") val episodes: Int?,
        @SerialName("genres") val genres: List<String>?,
        @SerialName("synonyms") val synonyms: List<String>?,
        @SerialName("averageScore") val averageScore: Int?,
        @SerialName("isAdult") val isAdult: Boolean?,
        @SerialName("trailer") val trailer: MediaTrailer?,
        @SerialName("description") val description: String?,
        @SerialName("characters") val characters: CharacterConnection?,
        @SerialName("recommendations") val recommendations: RecommendationConnection?,
    )

    @Serializable
    data class RecommendationConnection(
        @SerialName("edges") val edges: List<RecommendationEdge> = emptyList(),
        @SerialName("nodes") val nodes: List<Recommendation> = emptyList(),
    )

    @Serializable
    data class RecommendationEdge(
        @SerialName("node") val node: Recommendation,
    )

    @Serializable
    data class Recommendation(
        @SerialName("mediaRecommendation") val mediaRecommendation: RecommendedMedia?,
    )

    @Serializable
    data class CharacterName(
        @SerialName("name") val first: String?,
        @SerialName("middle") val middle: String?,
        @SerialName("last") val last: String?,
        @SerialName("full") val full: String?,
        @SerialName("native") val native: String?,
        @SerialName("alternative") val alternative: List<String>?,
        @SerialName("alternativeSpoiler") val alternativeSpoiler: List<String>?,
        @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class CharacterImage(
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
    )

    @Serializable
    data class Character(
        @SerialName("name") val name: CharacterName?,
        @SerialName("age") val age: String?,
        @SerialName("image") val image: CharacterImage?,
    )

    @Serializable
    data class CharacterEdge(
        @SerialName("id") val id: Int?,
        @SerialName("role") val role: String?,
        @SerialName("name") val name: String?,
        @SerialName("voiceActors") val voiceActors: List<Staff>?,
        @SerialName("favouriteOrder") val favouriteOrder: Int?,
        @SerialName("media") val media: List<CharacterMedia>?,
        @SerialName("node") val node: Character?,
    )

    @Serializable
    data class StaffImage(
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
    )

    @Serializable
    data class StaffName(
        @SerialName("name") val first: String?,
        @SerialName("middle") val middle: String?,
        @SerialName("last") val last: String?,
        @SerialName("full") val full: String?,
        @SerialName("native") val native: String?,
        @SerialName("alternative") val alternative: List<String>?,
        @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class Staff(
        @SerialName("image") val image: StaffImage?,
        @SerialName("name") val name: StaffName?,
        @SerialName("age") val age: Int?,
    )

    @Serializable
    data class CharacterConnection(
        @SerialName("edges") val edges: List<CharacterEdge>?,
        @SerialName("nodes") val nodes: List<Character>?,
    )

    @Serializable
    data class MediaTrailer(
        @SerialName("id") val id: String?,
        @SerialName("site") val site: String?,
        @SerialName("thumbnail") val thumbnail: String?,
    )

    @Serializable
    data class MediaCoverImage(
        @SerialName("extraLarge") val extraLarge: String?,
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
        @SerialName("color") val color: String?,
    )

    @Serializable
    data class SeasonNextAiringEpisode(
        @SerialName("episode") val episode: Int?,
        @SerialName("timeUntilAiring") val timeUntilAiring: Int?,
    )

    @Serializable
    data class SeasonEdges(
        @SerialName("edges") val edges: List<SeasonEdge>?,
    )

    @Serializable
    data class SeasonEdge(
        @SerialName("id") val id: Int?,
        @SerialName("relationType") val relationType: String?,
        @SerialName("node") val node: SeasonNode?,
    )

    @Serializable
    data class AniListFavoritesMediaConnection(
        @SerialName("nodes") val nodes: List<LikeNode>,
    )

    @Serializable
    data class AniListFavourites(
        @SerialName("anime") val anime: AniListFavoritesMediaConnection,
    )

    @Serializable
    data class MediaTitle(
        @SerialName("romaji") val romaji: String?,
        @SerialName("english") val english: String?,
        @SerialName("native") val native: String?,
        @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class SeasonNode(
        @SerialName("id") val id: Int,
        @SerialName("format") val format: String?,
        @SerialName("title") val title: Title?,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("coverImage") val coverImage: CoverImage?,
        @SerialName("averageScore") val averageScore: Int?,
    )

    @Serializable
    data class AniListAvatar(
        @SerialName("large") val large: String?,
    )

    @Serializable
    data class AniListViewer(
        @SerialName("id") val id: Int,
        @SerialName("name") val name: String,
        @SerialName("avatar") val avatar: AniListAvatar?,
        @SerialName("favourites") val favourites: AniListFavourites?,
    )

    @Serializable
    data class AniListData(
        @SerialName("Viewer") val viewer: AniListViewer?,
    )

    @Serializable
    data class AniListRoot(
        @SerialName("data") val data: AniListData?,
    )

    @Serializable
    data class AniListUser(
        @SerialName("id") val id: Int,
        @SerialName("name") val name: String,
        @SerialName("picture") val picture: String?,
    )

    @Serializable
    data class LikeNode(
        @SerialName("id") val id: Int?,
    )

    @Serializable
    data class LikePageInfo(
        @SerialName("total") val total: Int?,
        @SerialName("currentPage") val currentPage: Int?,
        @SerialName("lastPage") val lastPage: Int?,
        @SerialName("perPage") val perPage: Int?,
        @SerialName("hasNextPage") val hasNextPage: Boolean?,
    )

    @Serializable
    data class LikeAnime(
        @SerialName("nodes") val nodes: List<LikeNode>?,
        @SerialName("pageInfo") val pageInfo: LikePageInfo?,
    )

    @Serializable
    data class LikeFavourites(
        @SerialName("anime") val anime: LikeAnime?,
    )

    @Serializable
    data class LikeViewer(
        @SerialName("favourites") val favourites: LikeFavourites?,
    )

    @Serializable
    data class LikeData(
        @SerialName("Viewer") val viewer: LikeViewer?,
    )

    @Serializable
    data class LikeRoot(
        @SerialName("data") val data: LikeData?,
    )

    @Serializable
    data class AniListTitleHolder(
        @SerialName("title") val title: Title?,
        @SerialName("isFavourite") val isFavourite: Boolean?,
        @SerialName("id") val id: Int?,
        @SerialName("progress") val progress: Int?,
        @SerialName("episodes") val episodes: Int?,
        @SerialName("score") val score: Int?,
        @SerialName("type") val type: AniListStatusType?,
    )

    @Serializable
    data class GetDataMediaListEntry(
        @SerialName("progress") val progress: Int?,
        @SerialName("status") val status: String?,
        @SerialName("score") val score: Int?,
    )

    @Serializable
    data class Nodes(
        @SerialName("id") val id: Int?,
        @SerialName("mediaRecommendation") val mediaRecommendation: MediaRecommendation?,
    )

    @Serializable
    data class GetDataMedia(
        @SerialName("isFavourite") val isFavourite: Boolean?,
        @SerialName("episodes") val episodes: Int?,
        @SerialName("title") val title: Title?,
        @SerialName("mediaListEntry") val mediaListEntry: GetDataMediaListEntry?,
    )

    @Serializable
    data class Recommendations(
        @SerialName("nodes") val nodes: List<Nodes>?,
    )

    @Serializable
    data class GetDataData(
        @SerialName("Media") val media: GetDataMedia?,
    )

    @Serializable
    data class GetDataRoot(
        @SerialName("data") val data: GetDataData?,
    )

    @Serializable
    data class GetSearchTitle(
        @SerialName("romaji") val romaji: String?,
    )

    @Serializable
    data class TrailerObject(
        @SerialName("id") val id: String?,
        @SerialName("thumbnail") val thumbnail: String?,
        @SerialName("site") val site: String?,
    )

    @Serializable
    data class GetSearchMedia(
        @SerialName("id") val id: Int,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("seasonYear") val seasonYear: Int,
        @SerialName("title") val title: GetSearchTitle,
        @SerialName("startDate") val startDate: StartedAt,
        @SerialName("averageScore") val averageScore: Int?,
        @SerialName("meanScore") val meanScore: Int?,
        @SerialName("bannerImage") val bannerImage: String?,
        @SerialName("trailer") val trailer: TrailerObject?,
        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @SerialName("recommendations") val recommendations: Recommendations?,
        @SerialName("relations") val relations: SeasonEdges?,
    )

    @Serializable
    data class GetSearchPage(
        @SerialName("Page") val page: GetSearchData?,
    )

    @Serializable
    data class GetSearchData(
        @SerialName("media") val media: List<GetSearchMedia>?,
    )

    @Serializable
    data class GetSearchRoot(
        @SerialName("data") val data: GetSearchPage?,
    )
}
