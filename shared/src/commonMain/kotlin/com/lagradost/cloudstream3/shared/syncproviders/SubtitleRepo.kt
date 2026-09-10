package com.lagradost.cloudstream3.shared.syncproviders

import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf

/** Stateless safe abstraction of SubtitleAPI */
class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api) {
    companion object {
        const val CACHE_SIZE = 20

        data class SavedSearchResponse(
            val unixTime: Long,
            val response: List<SubtitleEntity>,
            val query: SubtitleSearch,
            val idPrefix: String,
        )

        data class SavedResourceResponse(
            val unixTime: Long,
            val response: SubtitleResource,
            val query: SubtitleEntity
        )

        private class RollingCache<T>(private val maxSize: Int) {
            private val list = atomicListOf<T>()
            private var index: Int = 0

            fun <R> find(predicate: (T) -> R?): R? = list.withLock {
                for (item in list) {
                    val result = predicate(item)
                    if (result != null) return@withLock result
                }
                null
            }

            fun add(item: T) = list.withLock {
                if (list.size >= maxSize) {
                    list[index] = item
                    index = (index + 1) % maxSize
                } else {
                    list.add(item)
                }
            }
        }

        private val searchCache = RollingCache<SavedSearchResponse>(CACHE_SIZE)
        private val resourceCache = RollingCache<SavedResourceResponse>(CACHE_SIZE)
    }

    suspend fun resource(data: SubtitleEntity): Result<SubtitleResource> = runCatching {
        val cached = resourceCache.find { item ->
            if (item.query == data && (unixTime - item.unixTime) < 60 * 20) item.response else null
        }
        if (cached != null) return@runCatching cached

        val returnValue = api.resource(freshAuth(), data)
        resourceCache.add(SavedResourceResponse(unixTime, returnValue, data))
        returnValue
    }

    suspend fun search(query: SubtitleSearch): Result<List<SubtitleEntity>> = runCatching {
        val cached = searchCache.find { item ->
            if (item.idPrefix == idPrefix && item.query == query && (unixTime - item.unixTime) < 60 * 120) item.response else null
        }
        if (cached != null) return@runCatching cached

        val returnValue = api.search(freshAuth(), query) ?: emptyList()
        if (returnValue.isNotEmpty()) {
            searchCache.add(SavedSearchResponse(unixTime, returnValue, query, idPrefix))
        }
        returnValue
    }
}
