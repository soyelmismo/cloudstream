package com.lagradost.cloudstream3.utils.videoskip

import androidx.compose.runtime.Immutable
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.mvvm.safeAsync
import com.lagradost.cloudstream3.utils.txt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import java.util.concurrent.ConcurrentHashMap

internal class BoundedCache<K : Any, V : Any>(private val maxSize: Int = 128) {
    private val map = ConcurrentHashMap<K, V>()

    operator fun get(key: K): V? = map[key]

    operator fun set(key: K, value: V) {
        if (map.size >= maxSize) {
            val keysToRemove = map.keys().toList().take(maxSize / 4)
            keysToRemove.forEach { map.remove(it) }
        }
        map[key] = value
    }

    fun clear() = map.clear()
}

enum class SkipType(val res: StringResource) {
    Opening(Res.string.skip_type_op),
    Ending(Res.string.skip_type_ed),
    Recap(Res.string.skip_type_recap),
    MixedOpening(Res.string.skip_type_mixed_op),
    MixedEnding(Res.string.skip_type_mixed_ed),
    Credits(Res.string.skip_type_credits),
    Intro(Res.string.skip_type_intro),
    Preview(Res.string.skip_type_preview),
}

@Immutable
data class SkipStamp(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
    val label: String? = null,
)

@Immutable
data class VideoSkipStamp(
    val timestamp: SkipStamp,
    val skipToNextEpisode: Boolean,
    val source: String,
) {
    val uiText =
        if (skipToNextEpisode) txt(Res.string.next_episode) else
            txt(
                Res.string.skip_type_format,
                timestamp.label?.let { txt(it) } ?: txt(timestamp.type.res)
            )
}

abstract class SkipAPI {
    open val name: String = "NONE"

    abstract val supportedTypes: Set<TvType>

    @Throws
    open suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long,
    ): ImmutableList<SkipStamp>? {
        throw NotImplementedError()
    }

    companion object {
        private const val NEAR_END_THRESHOLD_MS = 20_000L
        private val skipApis: List<SkipAPI> = listOf(AniSkip(), TheIntroDBSkip(), IntroDbSkip(), AnimeSkip())
        private val cachedStamps = BoundedCache<Int, ImmutableList<VideoSkipStamp>>()

        private fun isNearEpisodeEnd(stampEndMs: Long, durationMs: Long): Boolean =
            durationMs - stampEndMs < NEAR_END_THRESHOLD_MS

        suspend fun videoStamps(
            data: LoadResponse,
            episode: ResultEpisode,
            episodeDurationMs: Long,
            hasNextEpisode: Boolean,
        ): ImmutableList<VideoSkipStamp> {
            cachedStamps[episode.id]?.let { return it }

            val (sourceName, matchingStamps) = skipApis
                .asSequence()
                .filter { data.type in it.supportedTypes }
                .firstNotNullOfOrNull { api ->
                    val stamps = safeAsync { api.stamps(data, episode, episodeDurationMs) }
                    if (stamps.isNullOrEmpty()) null else (api.name to stamps)
                } ?: return persistentListOf()

            val videoStamps = matchingStamps.map { stamp ->
                VideoSkipStamp(
                    timestamp = stamp,
                    skipToNextEpisode = hasNextEpisode && isNearEpisodeEnd(stamp.endMs, episodeDurationMs),
                    source = sourceName
                )
            }.toImmutableList()

            cachedStamps[episode.id] = videoStamps
            return videoStamps
        }
    }
}
