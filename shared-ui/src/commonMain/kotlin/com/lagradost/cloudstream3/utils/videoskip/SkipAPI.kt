package com.lagradost.cloudstream3.utils.videoskip

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.safeAsync
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import org.jetbrains.compose.resources.StringResource
import java.util.concurrent.ConcurrentHashMap


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

data class SkipStamp(
    val type: SkipType,
    /** Start position in milliseconds of the skip, where it should start showing up */
    val startMs: Long,
    /** End position in milliseconds of the skip, where it will skip to */
    val endMs: Long,
    /** Custom visual label instead of using the type. Only use this for content not covered by SkipType */
    val label: String? = null,
)

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

    /** On what types SkipAPI should trigger on */
    abstract val supportedTypes: Set<TvType>

    /** Get all video skip stamps of the associated episode */
    @Throws
    open suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long,
    ): List<SkipStamp>? {
        throw NotImplementedError()
    }

    companion object {
        private val skipApis: List<SkipAPI> = listOf(AniSkip(), TheIntroDBSkip(), IntroDbSkip(), AnimeSkip())
        private val cachedStamps = ConcurrentHashMap<Int, List<VideoSkipStamp>>()

        /** Get all video timestamps from an episode */
        suspend fun videoStamps(
            data: LoadResponse,
            episode: ResultEpisode,
            episodeDurationMs: Long,
            hasNextEpisode: Boolean,
        ): List<VideoSkipStamp> {
            cachedStamps[episode.id]?.let { list ->
                return list
            }

            for (api in skipApis) {
                /** Unsupported type, so we do not waste a get call */
                if (!api.supportedTypes.contains(data.type)) {
                    continue
                }

                /** Find first non-empty stamps */
                val stamps = safeAsync { api.stamps(data, episode, episodeDurationMs) }
                if (stamps.isNullOrEmpty()) {
                    continue
                }

                return stamps.map { stamp ->
                    VideoSkipStamp(
                        timestamp = stamp,
                        skipToNextEpisode = hasNextEpisode && episodeDurationMs - stamp.endMs < 20_000L,
                        source = api.name
                    )
                }.also { stamps ->
                    /** Put in cache, this is such small data, it should be fine to never clear it */
                    cachedStamps[episode.id] = stamps
                }
            }
            return emptyList()
        }
    }
}

