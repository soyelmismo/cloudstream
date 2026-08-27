package com.lagradost.cloudstream3.shared.syncproviders.providers

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.shared.syncproviders.AuthToken
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.shared.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SubDlApi : SubtitleAPI() {
    override val name = "SubDL"
    override val idPrefix = "subdl"

    override val icon = Res.drawable.subdl_logo_big
    override val hasInApp = true
    override val inAppLoginRequirement = AuthLoginRequirement(password = true, email = true)
    override val requiresLogin = true
    override val createAccountUrl = "https://subdl.com/panel/register"

    companion object {
        const val APIURL = "https://api.subdl.com"
        const val APIENDPOINT = "$APIURL/api/v1/subtitles"
        const val DOWNLOADENDPOINT = "https://dl.subdl.com"
    }

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val email = form.email ?: return null
        val password = form.password ?: return null
        val tokenResponse = app.post(
            url = "$APIURL/login",
            json = mapOf(
                "email" to email,
                "password" to password
            )
        ).parsed<OAuthTokenResponse>()

        val apiResponse = app.get(
            url = "$APIURL/user/userApi",
            headers = mapOf(
                "Authorization" to "Bearer ${tokenResponse.token}"
            )
        ).parsed<ApiKeyResponse>()

        return AuthToken(accessToken = apiResponse.apiKey, payload = email)
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val name = token?.payload ?: return null
        return AuthUser(id = name.hashCode(), name = name)
    }

    private val subdl2LangTagIETF: Map<String, String> by lazy {
        langTagIETF2subdl.entries.associate { (k, v) -> v to k }
    }

    private fun buildQueryParams(query: AbstractSubtitleEntities.SubtitleSearch): String {
        val builder = StringBuilder()
        query.epNumber?.takeIf { it > 0 }?.let { builder.append("&episode_number=$it") }
        query.seasonNumber?.takeIf { it > 0 }?.let { builder.append("&season_number=$it") }
        query.year?.takeIf { it > 0 }?.let { builder.append("&year=$it") }
        return builder.toString()
    }

    private fun buildSearchUrl(apiKey: String, query: AbstractSubtitleEntities.SubtitleSearch): String {
        val langSubdlCode = langTagIETF2subdl[query.lang.toString()] ?: query.lang.orEmpty()
        val queryParams = buildQueryParams(query)
        val targetQuery = when {
            query.imdbId != null -> "&imdb_id=${query.imdbId}"
            query.tmdbId != null -> "&tmdb_id=${query.tmdbId}"
            else -> "&film_name=${query.query.orEmpty()}"
        }
        return "$APIENDPOINT?api_key=$apiKey$targetQuery&languages=$langSubdlCode$queryParams"
    }

    private fun mapSubtitleEntity(
        subtitle: Subtitle,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): AbstractSubtitleEntities.SubtitleEntity {
        val langTagIETF = subdl2LangTagIETF[subtitle.lang] ?: subtitle.lang
        val resEpNum = subtitle.episode ?: query.epNumber
        val resSeasonNum = subtitle.season ?: query.seasonNumber
        val type = if ((resSeasonNum ?: 0) > 0) TvType.TvSeries else TvType.Movie

        return AbstractSubtitleEntities.SubtitleEntity(
            idPrefix = this.idPrefix,
            name = subtitle.releaseName,
            lang = langTagIETF,
            data = "$DOWNLOADENDPOINT${subtitle.url.orEmpty()}",
            type = type,
            source = this.name,
            epNumber = resEpNum,
            seasonNumber = resSeasonNum,
            isHearingImpaired = subtitle.hearingImpaired ?: false,
        )
    }

    override suspend fun search(
        auth: AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {
        val apiKey = auth?.token?.accessToken ?: return null
        val searchQueryUrl = buildSearchUrl(apiKey, query)

        val req = app.get(
            url = searchQueryUrl,
            headers = mapOf(
                "Accept" to "application/json"
            )
        )

        return req.parsedSafe<ApiResponse>()?.subtitles?.map { subtitle ->
            mapSubtitleEntity(subtitle, query)
        }
    }

    override suspend fun SubtitleResource.getResources(
        auth: AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ) {
        this.addZipUrl(subtitle.data) { name, _ ->
            name
        }
    }

    @Serializable
    data class SubtitleOAuthEntity(
        @SerialName("userEmail") var userEmail: String,
        @SerialName("pass") var pass: String,
        @SerialName("name") var name: String? = null,
        @SerialName("accessToken") var accessToken: String? = null,
        @SerialName("apiKey") var apiKey: String? = null,
    )

    @Serializable
    data class OAuthTokenResponse(
        @SerialName("token") val token: String,
        @SerialName("userData") val userData: UserData? = null,
        @SerialName("status") val status: Boolean? = null,
        @SerialName("message") val message: String? = null,
    )

    @Serializable
    data class UserData(
        @SerialName("email") val email: String,
        @SerialName("name") val name: String,
        @SerialName("country") val country: String,
        @SerialName("scStepCode") val scStepCode: String,
        @SerialName("scVerified") val scVerified: Boolean,
        @SerialName("username") val username: String? = null,
        @SerialName("scUsername") val scUsername: String,
    )

    @Serializable
    data class ApiKeyResponse(
        @SerialName("ok") val ok: Boolean? = false,
        @SerialName("api_key") val apiKey: String,
        @SerialName("usage") val usage: Usage? = null,
    )

    @Serializable
    data class Usage(
        @SerialName("total") val total: Long? = 0,
        @SerialName("today") val today: Long? = 0,
    )

    @Serializable
    data class ApiResponse(
        @SerialName("status") val status: Boolean? = null,
        @SerialName("results") val results: List<Result>? = null,
        @SerialName("subtitles") val subtitles: List<Subtitle>? = null,
    )

    @Serializable
    data class Result(
        @SerialName("sd_id") val sdId: Int? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("tmdb_id") val tmdbId: Long? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("year") val year: Int? = null,
    )

    @Serializable
    data class Subtitle(
        @SerialName("release_name") val releaseName: String,
        @SerialName("name") val name: String,
        @SerialName("lang") val lang: String,
        @SerialName("author") val author: String? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("subtitlePage") val subtitlePage: String? = null,
        @SerialName("season") val season: Int? = null,
        @SerialName("episode") val episode: Int? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("hi") val hearingImpaired: Boolean? = null,
    )

    // https://subdl.com/api-files/language_list.json
    // most of it is IETF BPC 47 conformant tag
    // but there are some exceptions
    private val langTagIETF2subdl = mapOf(
        "en-bg" to "BG_EN", // "Bulgarian_English"
        "en-de" to "EN_DE", // "English_German"
        "en-hu" to "HU_EN", // "Hungarian_English"
        "en-nl" to "NL_EN", // "Dutch_English"
        "pt-br" to "BR_PT", // "Brazillian Portuguese"
        "zh-hant" to "ZH_BG", // "Big 5 code" -> traditional Chinese (?_?)
    )
}
