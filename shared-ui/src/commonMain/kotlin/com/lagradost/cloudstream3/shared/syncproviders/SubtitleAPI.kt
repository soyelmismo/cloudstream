package com.lagradost.cloudstream3.shared.syncproviders

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource

/**
 * Stateless subtitle class for external subtitles.
 *
 * All non-null `AuthToken` will be non-expired when each function is called.
 */
abstract class SubtitleAPI : AuthAPI() {
    @Throws
    open suspend fun search(auth: AuthData?, query: SubtitleSearch): List<SubtitleEntity>? =
        throw NotImplementedError()

    @Throws
    open suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? =
        throw NotImplementedError()

    @Throws
    open suspend fun SubtitleResource.getResources(auth: AuthData?, subtitle: SubtitleEntity) {
        this.addUrl(load(auth, subtitle))
    }

    @Throws
    suspend fun resource(auth: AuthData?, subtitle: SubtitleEntity): SubtitleResource {
        return SubtitleResource().apply {
            this.getResources(auth, subtitle)
        }
    }
}
