package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.models.LinkLoadingResult
import com.lagradost.cloudstream3.models.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText

/**
 * Common base class for external video player actions that accept a single streaming URI.
 */
abstract class SingleLinkExternalPlayerAction(
    appName: UiText,
    packageName: String,
    intentClass: String? = null
) : OpenInAppAction(
    appName = appName,
    packageName = packageName,
    intentClass = intentClass
) {
    override val sourceTypes: Set<ExtractorLinkType> =
        setOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8, ExtractorLinkType.DASH)

    override val oneSource: Boolean = true

    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        intent.data = result.links[index!!].url.toUri()
    }

    override fun onResult(activity: Activity, intent: Intent?) = Unit
}
