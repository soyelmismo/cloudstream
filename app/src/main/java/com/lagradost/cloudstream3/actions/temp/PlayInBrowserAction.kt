package com.lagradost.cloudstream3.actions.temp

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.models.LinkLoadingResult
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.ExtractorLinkType

class PlayInBrowserAction: VideoClickAction() {
    override val name = txt(Res.string.episode_action_play_in_format, txt(Res.string.browser))

    override val oneSource = true

    override val isPlayer = true

    override val sourceTypes: Set<ExtractorLinkType> = setOf(
        ExtractorLinkType.VIDEO,
        ExtractorLinkType.DASH,
        ExtractorLinkType.M3U8
    )

    override fun shouldShow(context: Context?, video: ResultEpisode?) = true

    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        val link = result.links.getOrNull(index ?: 0) ?: return
        val i = Intent(Intent.ACTION_VIEW)
        i.data = link.url.toUri()
        launch(i)
    }
}