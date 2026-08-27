package com.lagradost.cloudstream3.actions.temp

import android.content.Context
import android.content.Intent
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.makeTempM3U8Intent
import com.lagradost.cloudstream3.models.LinkLoadingResult
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.utils.txt

class ViewM3U8Action: VideoClickAction() {
    override val name = txt(Res.string.episode_action_play_in_format, "m3u8 player")

    override val isPlayer = true

    override fun shouldShow(context: Context?, video: ResultEpisode?) = true

    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        if (context == null) return
        val i = Intent(Intent.ACTION_VIEW)
        makeTempM3U8Intent(context, i, result)
        launch(i)
    }
}