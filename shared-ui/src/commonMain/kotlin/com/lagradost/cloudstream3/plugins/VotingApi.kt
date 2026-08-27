package com.lagradost.cloudstream3.plugins

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

object VotingApi {
    private const val LOGKEY = "VotingApi"
    private const val API_DOMAIN = "https://api.countify.xyz"

    var showToastHandler: ((UiText) -> Unit)? = null
    var canVoteHandler: ((String) -> Boolean)? = null

    private fun transformUrl(url: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("${url}#funny-salt".toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }

    suspend fun SitePlugin.getVotes(): Int = getVotes(url)
    fun SitePlugin.hasVoted(): Boolean = hasVoted(url)
    suspend fun SitePlugin.vote(): Int = vote(url)
    fun SitePlugin.canVote(): Boolean = canVote(this.url)

    private val votesCache = mutableMapOf<String, Int>()

    private suspend fun readVote(pluginUrl: String): Int {
        val id = transformUrl(pluginUrl)
        val url = "$API_DOMAIN/get-total/$id"
        Log.d(LOGKEY, "Requesting GET: $url")
        return app.get(url).parsedSafe<CountifyResult>()?.count ?: 0
    }

    private suspend fun writeVote(pluginUrl: String): Boolean {
        val id = transformUrl(pluginUrl)
        val url = "$API_DOMAIN/increment/$id"
        Log.d(LOGKEY, "Requesting POST: $url")
        return app.post(url, emptyMap<String, String>())
            .parsedSafe<CountifyResult>()?.count != null
    }

    suspend fun getVotes(pluginUrl: String): Int =
        votesCache[pluginUrl] ?: readVote(pluginUrl).also {
            votesCache[pluginUrl] = it
        }

    fun hasVoted(pluginUrl: String): Boolean =
        AppPreferenceManager.getBooleanSync("cs3-votes/${transformUrl(pluginUrl)}", false)

    fun canVote(pluginUrl: String): Boolean =
        canVoteHandler?.invoke(pluginUrl) ?: false

    private val voteLock = Mutex()

    suspend fun vote(pluginUrl: String): Int {
        voteLock.withLock {
            if (!canVote(pluginUrl)) {
                showToastHandler?.invoke(txt(Res.string.extension_install_first))
                return getVotes(pluginUrl)
            }

            if (hasVoted(pluginUrl)) {
                showToastHandler?.invoke(txt(Res.string.already_voted))
                return getVotes(pluginUrl)
            }

            if (writeVote(pluginUrl)) {
                AppPreferenceManager.setBooleanSync("cs3-votes/${transformUrl(pluginUrl)}", true)
                votesCache[pluginUrl] = votesCache[pluginUrl]?.plus(1) ?: 1
            }

            return getVotes(pluginUrl)
        }
    }

    @Serializable
    private data class CountifyResult(
        @SerialName("id") val id: String? = null,
        @SerialName("count") val count: Int? = null,
    )
}
