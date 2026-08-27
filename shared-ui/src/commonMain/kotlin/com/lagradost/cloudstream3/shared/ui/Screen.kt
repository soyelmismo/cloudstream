package com.lagradost.cloudstream3.shared.ui

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginItem
import com.lagradost.cloudstream3.utils.ExtractorLink

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Typed navigation destinations for CloudStream KMP.
 */
@Immutable
sealed interface Screen {
    @Immutable
    data object Home : Screen
    @Immutable
    data object Search : Screen
    @Immutable
    data object Library : Screen
    @Immutable
    data object Downloads : Screen
    @Immutable
    data class Details(val url: String, val apiName: String, val autoResume: Boolean = false) : Screen
    @Immutable
    data class Player(
        val title: String,
        val url: String,
        val episodeIndex: Int? = null,
        val seasonIndex: Int? = null,
        val subtitles: ImmutableList<SubtitleFile> = persistentListOf(),
        val availableLinks: ImmutableList<ExtractorLink> = persistentListOf()
    ) : Screen
    @Immutable
    data object Settings : Screen
    @Immutable
    data object Plugins : Screen
    @Immutable
    data class PluginDetails(val plugin: PluginItem) : Screen
    @Immutable
    data object Onboarding : Screen
    @Immutable
    data object AccountSelect : Screen
}

