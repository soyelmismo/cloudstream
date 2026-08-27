package com.lagradost.cloudstream3.shared.syncproviders

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.syncproviders.providers.Addic7ed
import com.lagradost.cloudstream3.shared.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.shared.syncproviders.providers.AnimeSkipAuth
import com.lagradost.cloudstream3.shared.syncproviders.providers.KitsuApi
import com.lagradost.cloudstream3.shared.syncproviders.providers.LocalList
import com.lagradost.cloudstream3.shared.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.shared.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.shared.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.shared.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.shared.syncproviders.providers.SubSourceApi
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

abstract class AccountManager {
    companion object {
        const val NONE_ID: Int = -1
        val malApi = MALApi()
        val kitsuApi = KitsuApi()
        val aniListApi = AniListApi()
        val simklApi = SimklApi()
        val localListApi = LocalList()

        val openSubtitlesApi = OpenSubtitlesApi()
        val addic7ed = Addic7ed()
        val subDlApi = SubDlApi()
        val subSourceApi = SubSourceApi()
        val animeSkipApi = AnimeSkipAuth()

        var cachedAccounts: MutableMap<String, Array<AuthData>> = mutableMapOf()
        var cachedAccountIds: MutableMap<String, Int> = mutableMapOf()

        private val _accountsState = MutableStateFlow<Map<String, AuthData?>>(emptyMap())
        val accountsState: StateFlow<Map<String, AuthData?>> = _accountsState.asStateFlow()

        const val ACCOUNT_TOKEN = "auth_tokens"
        const val ACCOUNT_IDS = "auth_ids"

        fun currentAccount(): String =
            AppPreferenceManager.getIntSync("data_store_helper/account_key_index", 0).toString()

        fun accounts(prefix: String): Array<AuthData> {
            require(prefix != "NONE")
            val key = "$ACCOUNT_TOKEN/$prefix/${currentAccount()}"
            val json = AppPreferenceManager.getStringSync(key) ?: return arrayOf()
            return tryParseJson<Array<AuthData>>(json) ?: arrayOf()
        }

        fun getAccounts(prefix: String): List<AuthData> {
            if (prefix == "NONE") return emptyList()
            return synchronized(cachedAccounts) {
                cachedAccounts[prefix]?.toList()
            } ?: accounts(prefix).toList()
        }

        fun getActiveAccount(prefix: String): AuthData? {
            if (prefix == "NONE") return null
            val activeId = synchronized(cachedAccountIds) {
                cachedAccountIds[prefix]
            } ?: AppPreferenceManager.getIntSync("$ACCOUNT_IDS/$prefix/${currentAccount()}", NONE_ID)

            if (activeId == NONE_ID) return null

            val accs = synchronized(cachedAccounts) {
                cachedAccounts[prefix]
            } ?: accounts(prefix)

            return accs.firstOrNull { it.user.id == activeId }
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            require(prefix != "NONE")
            val key = "$ACCOUNT_TOKEN/$prefix/${currentAccount()}"
            AppPreferenceManager.setStringSync(key, array.toJson())
            synchronized(cachedAccounts) {
                cachedAccounts[prefix] = array
            }
            val activeId = synchronized(cachedAccountIds) {
                cachedAccountIds[prefix]
            } ?: AppPreferenceManager.getIntSync("$ACCOUNT_IDS/$prefix/${currentAccount()}", NONE_ID)
            val activeAuth = array.firstOrNull { it.user.id == activeId }
            _accountsState.update { current ->
                current + (prefix to activeAuth)
            }
        }

        fun updateAccountsId(prefix: String, id: Int) {
            require(prefix != "NONE")
            val key = "$ACCOUNT_IDS/$prefix/${currentAccount()}"
            AppPreferenceManager.setIntSync(key, id)
            synchronized(cachedAccountIds) {
                cachedAccountIds[prefix] = id
            }
            val accs = synchronized(cachedAccounts) {
                cachedAccounts[prefix]
            } ?: accounts(prefix)
            val activeAuth = accs.firstOrNull { it.user.id == id }
            _accountsState.update { current ->
                current + (prefix to activeAuth)
            }
        }

        val allApis = arrayOf(
            SyncRepo(malApi),
            SyncRepo(kitsuApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi),
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            AuthRepo(animeSkipApi),
            SubtitleRepo(subSourceApi)
        )

        fun loadAccounts() {
            val data = mutableMapOf<String, Array<AuthData>>()
            val ids = mutableMapOf<String, Int>()
            val activeMap = mutableMapOf<String, AuthData?>()
            val acc = currentAccount()
            for (api in allApis) {
                val prefix = api.idPrefix
                val accs = accounts(prefix)
                val key = "$ACCOUNT_IDS/$prefix/$acc"
                val activeId = AppPreferenceManager.getIntSync(key, NONE_ID)
                data[prefix] = accs
                ids[prefix] = activeId
                activeMap[prefix] = accs.firstOrNull { it.user.id == activeId }
            }
            synchronized(cachedAccounts) {
                cachedAccounts = data
            }
            synchronized(cachedAccountIds) {
                cachedAccountIds = ids
            }
            _accountsState.update { activeMap }
        }

        fun updateAccountIds() {
            loadAccounts()
        }

        init {
            loadAccounts()
        }

        fun initMainAPI() {
            LoadResponse.malIdPrefix = malApi.idPrefix
            LoadResponse.kitsuIdPrefix = kitsuApi.idPrefix
            LoadResponse.aniListIdPrefix = aniListApi.idPrefix
            LoadResponse.simklIdPrefix = simklApi.idPrefix
        }

        val subtitleProviders = arrayOf(
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            SubtitleRepo(subSourceApi)
        )
        val syncApis = arrayOf(
            SyncRepo(malApi),
            SyncRepo(kitsuApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi)
        )

        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"
        const val APP_STRING_SEARCH = "cloudstreamsearch"
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"
        const val APP_STRING_SHARE = "csshare"

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            if (seconds < 0) return completedValue
            val totalSeconds = seconds.toLong()
            val days = TimeUnit.SECONDS.toDays(totalSeconds)
            val hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24
            val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                append("${minutes}m")
            }
        }
    }
}
