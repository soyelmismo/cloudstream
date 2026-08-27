package com.lagradost.cloudstream3.shared.ui

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginItem
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Typed navigation destinations for CloudStream KMP.
 */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Downloads : Screen
    data class Details(val url: String, val apiName: String, val autoResume: Boolean = false) : Screen
    data class Player(
        val title: String,
        val url: String,
        val episodeIndex: Int? = null,
        val seasonIndex: Int? = null,
        val subtitles: List<SubtitleFile> = emptyList(),
        val availableLinks: List<ExtractorLink> = emptyList()
    ) : Screen
    data object Settings : Screen
    data object Plugins : Screen
    data class PluginDetails(val plugin: PluginItem) : Screen
    data object Onboarding : Screen
    data object AccountSelect : Screen
}

