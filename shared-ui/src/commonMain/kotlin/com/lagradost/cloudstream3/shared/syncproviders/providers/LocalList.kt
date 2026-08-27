package com.lagradost.cloudstream3.shared.syncproviders.providers

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.models.ListSorting
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.SyncAPI
import com.lagradost.cloudstream3.shared.syncproviders.toYear
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class LocalList : SyncAPI() {
    override val name = "Local"
    override val idPrefix = "local"

    override val icon = Res.drawable.ic_baseline_storage_24
    override val requiresLogin = false
    override val createAccountUrl = null
    override var requireLibraryRefresh = true
    override val syncIdName = SyncIdName.LocalList

    companion object {
        private fun createLibraryItem(
            id: Int,
            name: String,
            url: String,
            apiName: String,
            type: com.lagradost.cloudstream3.TvType?,
            posterUrl: String?,
            latestUpdatedTime: Long?,
            score: Double? = null,
            quality: com.lagradost.cloudstream3.SearchQuality? = null,
            year: Int? = null,
            plot: String? = null,
        ): SyncAPI.LibraryItem = SyncAPI.LibraryItem(
            name = name,
            url = url,
            syncId = id.toString(),
            episodesCompleted = null,
            episodesTotal = null,
            personalRating = score?.let { Score.from10(it) },
            lastUpdatedUnixTime = latestUpdatedTime,
            apiName = apiName,
            type = type,
            posterUrl = posterUrl,
            posterHeaders = null,
            quality = quality,
            releaseDate = year?.toYear(),
            id = id,
            plot = plot,
            score = score?.let { Score.from10(it) }
        )

        fun BookmarkEntity.toLibraryItem(): SyncAPI.LibraryItem = createLibraryItem(
            id = id,
            name = name,
            url = url,
            apiName = apiName,
            type = type,
            posterUrl = posterUrl,
            latestUpdatedTime = latestUpdatedTime,
            score = score,
            quality = quality,
            year = year,
            plot = plot
        )

        fun FavoriteEntity.toLibraryItem(): SyncAPI.LibraryItem = createLibraryItem(
            id = id,
            name = name,
            url = url,
            apiName = apiName,
            type = type,
            posterUrl = posterUrl,
            latestUpdatedTime = favoritesTime
        )

        fun SubscriptionEntity.toLibraryItem(): SyncAPI.LibraryItem = createLibraryItem(
            id = id,
            name = name,
            url = url,
            apiName = apiName,
            type = type,
            posterUrl = posterUrl,
            latestUpdatedTime = latestUpdatedTime,
            year = year
        )
    }

    override suspend fun library(auth: AuthData?): SyncAPI.LibraryMetadata? = withContext(Dispatchers.IO) {
        val accountId = AccountManager.currentAccount().toIntOrNull() ?: 0
        val db = DatabaseDriverFactory.getDatabase()

        val bookmarks = db.bookmarkDao().getAllBookmarks(accountId)
        val favorites = db.favoriteDao().getAllFavorites(accountId)
        val subscriptions = db.subscriptionDao().getAllSubscriptions(accountId)

        val baseMap = WatchType.entries.filter { it != WatchType.NONE }.associate {
            it.stringRes to emptyList<SyncAPI.LibraryItem>()
        } + mapOf(
            Res.string.favorites_list_name to emptyList(),
            Res.string.subscription_list_name to emptyList(),
        )

        val watchStatusMap = bookmarks.groupBy { WatchType.fromInternalId(it.watchType).stringRes }
            .mapValues { group ->
                group.value.map { it.toLibraryItem() }
            }

        val favoritesMap = mapOf(
            Res.string.favorites_list_name to favorites.map { it.toLibraryItem() }
        )

        val subscriptionsMap = mapOf(
            Res.string.subscription_list_name to subscriptions.map { it.toLibraryItem() }
        )

        val list = baseMap + watchStatusMap + favoritesMap + subscriptionsMap

        SyncAPI.LibraryMetadata(
            list.map { SyncAPI.LibraryList(txt(it.key), it.value) },
            setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.ReleaseDateNew,
                ListSorting.ReleaseDateOld,
            )
        )
    }
}
