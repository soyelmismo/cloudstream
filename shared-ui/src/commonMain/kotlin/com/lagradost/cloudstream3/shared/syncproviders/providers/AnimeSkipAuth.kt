package com.lagradost.cloudstream3.shared.syncproviders.providers

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.shared.syncproviders.AuthAPI
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.shared.syncproviders.AuthToken
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.MessageDigest

class AnimeSkipAuth : AuthAPI() {
    override val name = "AnimeSkip"
    override val inAppLoginRequirement: AuthLoginRequirement =
        AuthLoginRequirement(password = true, username = true)
    override val idPrefix = "anime-skip"
    override val hasInApp = true
    override val icon = Res.drawable.animeskip
    override val createAccountUrl = "https://anime-skip.com/account"
    val baseClientId = "as1JgiMbW4wKfmTLWXS79iTDQFll76pk"

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    @Serializable
    data class LoginRoot(
        @SerialName("data") val data: LoginData,
    )

    @Serializable
    data class LoginData(
        @SerialName("login") val login: Login,
    )

    @Serializable
    data class Login(
        @SerialName("authToken") val authToken: String,
        @SerialName("refreshToken") val refreshToken: String,
        @SerialName("account") val account: Account,
    )

    @Serializable
    data class ApiRoot(
        @SerialName("data") val data: ApiData,
    )

    @Serializable
    data class ApiData(
        @SerialName("myApiClients") val myApiClients: List<MyApiClient>,
    )

    @Serializable
    data class MyApiClient(
        @SerialName("id") val id: String,
    )

    @Serializable
    data class Account(
        @SerialName("profileUrl") val profileUrl: String,
        @SerialName("username") val username: String,
        @SerialName("email") val email: String,
    )

    @Serializable
    data class Payload(
        @SerialName("profileUrl") val profileUrl: String,
        @SerialName("username") val username: String,
        @SerialName("email") val email: String,
        @SerialName("clientId") val clientId: String,
    )

    override suspend fun user(token: AuthToken?): AuthUser? {
        val payload = parseJson<Payload>(token?.payload ?: return null)
        return AuthUser(
            name = payload.username,
            id = payload.email.hashCode(),
            profilePicture = payload.profileUrl
        )
    }

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val hash = md5(form.password ?: return null)
        val emailOrUserName = form.email ?: form.username ?: return null

        val loginQuery = """
    {
      login(usernameEmail: "$emailOrUserName", passwordHash: "$hash") {
        authToken
        refreshToken
        account {
          profileUrl
          username
          email
        }
      }
    }
"""
        val loginRoot = app.post(
            "https://api.anime-skip.com/graphql",
            json = mapOf("query" to loginQuery),
            headers = mapOf(
                "Accept" to "*/*",
                "content-type" to "application/json",
                "X-Client-ID" to baseClientId
            )
        ).parsed<LoginRoot>()

        val authToken = loginRoot.data.login.authToken
        val refreshToken = loginRoot.data.login.refreshToken
        val account = loginRoot.data.login.account

        val clientQuery = """
            {
              myApiClients {
                id
              }
            }
        """.trimIndent()

        val apiRoot = app.post(
            "https://api.anime-skip.com/graphql",
            json = mapOf("query" to clientQuery),
            headers = mapOf(
                "Accept" to "*/*",
                "content-type" to "application/json",
                "Authorization" to "Bearer $authToken",
                "X-Client-ID" to baseClientId
            )
        ).parsed<ApiRoot>()

        val clientId = apiRoot.data.myApiClients.getOrNull(0)?.id
            ?: throw ErrorLoadingException("No API token found")

        val payload = Payload(
            profileUrl = account.profileUrl,
            username = account.username,
            email = account.email,
            clientId = clientId,
        )
        return AuthToken(
            accessToken = authToken,
            refreshToken = refreshToken,
            payload = payload.toJson()
        )
    }
}
