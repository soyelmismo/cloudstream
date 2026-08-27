package com.lagradost.cloudstream3.shared.persistence.repository

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository abstraction for managing, querying, and observing [MainAPI] providers.
 * Decouples ViewModels and the UI layer from direct access to the static [APIHolder] singleton.
 */
interface ProviderRepository {
    /**
     * Retrieves all available and loaded [MainAPI] providers.
     */
    fun getAllProviders(): List<MainAPI>

    /**
     * Retrieves all available providers that support a homepage ([MainAPI.hasMainPage]).
     * Falls back to all providers if none have a homepage declared.
     */
    fun getHomepageProviders(): List<MainAPI> {
        val all = getAllProviders()
        return all.filter { it.hasMainPage }.ifEmpty { all }
    }

    /**
     * Finds a provider by its unique [MainAPI.name], or null if not found.
     */
    fun getApiByName(name: String?): MainAPI?

    /**
     * Finds a provider matching the beginning of the given [url], or null if not found.
     */
    fun getApiByUrl(url: String?): MainAPI?

    /**
     * Subscribes to provider list changes (e.g. plugins loaded/unloaded).
     * @return An unregister callback function `() -> Unit` to cleanly remove the listener.
     */
    fun addOnProvidersChangedListener(listener: () -> Unit): () -> Unit

    /**
     * Returns a cold [Flow] that emits the current list of providers and updates whenever providers change.
     */
    fun getProvidersFlow(): Flow<List<MainAPI>>
}

/**
 * Default implementation of [ProviderRepository] backed by [APIHolder].
 */
class ProviderRepositoryImpl : ProviderRepository {
    override fun getAllProviders(): List<MainAPI> {
        val apisList = APIHolder.apis.withLock { APIHolder.apis.toList() }
        val allList = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
        return (allList + apisList).distinctBy { it.name }
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

    override fun getProvidersFlow(): Flow<List<MainAPI>> = callbackFlow {
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
