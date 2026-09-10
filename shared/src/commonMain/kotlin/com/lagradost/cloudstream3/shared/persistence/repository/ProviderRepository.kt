package com.lagradost.cloudstream3.shared.persistence.repository

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface ProviderRepository {
    fun getAllProviders(): ImmutableList<MainAPI>

    fun getHomepageProviders(): ImmutableList<MainAPI> {
        val all = getAllProviders()
        return all.filter { it.hasMainPage }.ifEmpty { all }.toImmutableList()
    }

    fun getApiByName(name: String?): MainAPI?

    fun getApiByUrl(url: String?): MainAPI?

    fun addOnProvidersChangedListener(listener: () -> Unit): () -> Unit

    fun getProvidersFlow(): Flow<ImmutableList<MainAPI>>
}

class ProviderRepositoryImpl : ProviderRepository {
    override fun getAllProviders(): ImmutableList<MainAPI> {
        val apisList = APIHolder.apis.withLock { APIHolder.apis.toList() }
        val allList = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
        return (allList + apisList).distinctBy { it.name }.toImmutableList()
    }

    override fun getApiByName(name: String?): MainAPI? {
        if (name == null) return null
        return APIHolder.getApiFromNameNull(name)
    }

    override fun getApiByUrl(url: String?): MainAPI? {
        if (url == null) return null
        return APIHolder.getApiFromUrlNull(url)
    }

    override fun addOnProvidersChangedListener(listener: () -> Unit): () -> Unit {
        APIHolder.onProvidersChanged.add(listener)
        return {
            APIHolder.onProvidersChanged.remove(listener)
        }
    }

    override fun getProvidersFlow(): Flow<ImmutableList<MainAPI>> = callbackFlow {
        trySend(getAllProviders())
        val listener = {
            trySend(getAllProviders())
            Unit
        }
        APIHolder.onProvidersChanged.add(listener)
        awaitClose {
            APIHolder.onProvidersChanged.remove(listener)
        }
    }
}
