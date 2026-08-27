package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.ui.components.MediaBadge
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Categorization of audio language / dubbing for stream links.
 */
enum class AudioCategory(val labelRes: StringResource) {
    ALL(Res.string.audio_filter_all),
    LATINO(Res.string.audio_filter_latino),
    CASTELLANO(Res.string.audio_filter_castellano),
    ENGLISH(Res.string.audio_filter_english),
    SUBTITLED(Res.string.audio_filter_subbed);
}

/**
 * Detects the [AudioCategory] of an [ExtractorLink] based on its name, source, and audio track URLs.
 */
fun ExtractorLink.detectAudioCategory(): AudioCategory {
    val searchContent = buildString {
        append(name).append(' ')
        append(source).append(' ')
        for (track in audioTracks) {
            append(track.url).append(' ')
        }
    }.lowercase()

    return when {
        searchContent.contains("latino") ||
                searchContent.contains("latam") ||
                Regex("""\b(lat)\b""", RegexOption.IGNORE_CASE).containsMatchIn(searchContent) -> AudioCategory.LATINO

        searchContent.contains("castellano") ||
                searchContent.contains("español") ||
                searchContent.contains("espanol") ||
                Regex("""\b(cast|spa|esp)\b""", RegexOption.IGNORE_CASE).containsMatchIn(searchContent) -> AudioCategory.CASTELLANO

        searchContent.contains("english") ||
                Regex("""\b(eng|dub|dubbed)\b""", RegexOption.IGNORE_CASE).containsMatchIn(searchContent) -> AudioCategory.ENGLISH

        searchContent.contains("subtitulado") ||
                searchContent.contains("subbed") ||
                searchContent.contains("vose") ||
                Regex("""\b(sub|vos)\b""", RegexOption.IGNORE_CASE).containsMatchIn(searchContent) -> AudioCategory.SUBTITLED

        else -> AudioCategory.ALL
    }
}

/**
 * Modal dialog for Link Extraction and streaming sources selection.
 * Handles loading progress, quality badges, audio category filters, subtitle selection,
 * error retries, and direct playback initiation using [ActionDialog].
 */
@Composable
fun ResultLinksDialog(
    state: ResultState,
    targetEpisode: ResultEpisode?,
    onPlayLink: (ExtractorLink, List<ExtractorLink>, List<SubtitleFile>, SubtitleFile?) -> Unit,
    onEvent: (ResultEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAudioCategory by remember { mutableStateOf(AudioCategory.ALL) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleFile?>(null) }

    val availableAudioCategories = remember(state.extractedLinks) {
        val detected = state.extractedLinks.map { it.detectAudioCategory() }.toSet()
        if (detected.any { it != AudioCategory.ALL } || detected.size > 1) {
            listOf(AudioCategory.ALL) + AudioCategory.entries.filter { it != AudioCategory.ALL && detected.contains(it) }
        } else {
            emptyList()
        }
    }

    val filteredLinks = remember(state.extractedLinks, selectedAudioCategory) {
        if (selectedAudioCategory == AudioCategory.ALL) {
            state.extractedLinks
        } else {
            state.extractedLinks.filter { it.detectAudioCategory() == selectedAudioCategory }
        }
    }

    val selectLinkText = stringResource(Res.string.selectLink)
    val episodeText = stringResource(Res.string.episode)
    val titleText = when {
        targetEpisode != null -> targetEpisode.name ?: "$episodeText ${targetEpisode.episode}"
        state.isMovie -> state.title
        else -> selectLinkText
    }

    ActionDialog(
        onDismissRequest = onDismiss,
        title = selectLinkText,
        subtitle = titleText,
        showCloseButton = true,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Loading Progress State
                if (state.isExtractingLinks) {
                    ExtractionLoadingBanner(progressCount = state.linksLoadingProgress)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Extraction Error State
                if (!state.linksLoadingError.isNullOrBlank()) {
                    ExtractionErrorBanner(
                        error = state.linksLoadingError,
                        onRetry = {
                            onEvent(ResultEvent.ReloadLinks(targetEpisode))
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Audio Categories Filter Chips (if multiple categories exist)
                if (availableAudioCategories.size > 1 && state.extractedLinks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(availableAudioCategories) { category ->
                            CloudStreamFilterChip(
                                labelRes = category.labelRes,
                                isSelected = selectedAudioCategory == category,
                                onClick = { selectedAudioCategory = category }
                            )
                        }
                    }
                }

                // Extracted Links List
                if (state.extractedLinks.isNotEmpty()) {
                    Text(
                        text = "${stringResource(Res.string.selectLink)} (${filteredLinks.size})",
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextSecondary
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (filteredLinks.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(filteredLinks) { link ->
                                ExtractorLinkItem(
                                    link = link,
                                    onClick = {
                                        onPlayLink(link, state.extractedLinks, state.extractedSubtitles, selectedSubtitle)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BodyMutedText(
                                text = stringResource(Res.string.noLinksFound)
                            )
                        }
                    }
                } else if (!state.isExtractingLinks && state.linksLoadingError.isNullOrBlank()) {
                    // Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BodyMutedText(
                            text = stringResource(Res.string.noLinksFound)
                        )
                    }
                }

                // Subtitles Information & Initial Subtitle Selector
                if (state.extractedSubtitles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = CloudStreamColors.SurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_outline_subtitles_24),
                            contentDescription = stringResource(Res.string.subtitles),
                            tint = CloudStreamColors.SubBadge,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(Res.string.initial_subtitle),
                            style = MaterialTheme.typography.caption.copy(
                                color = CloudStreamColors.TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            CloudStreamFilterChip(
                                label = stringResource(Res.string.subtitle_none),
                                isSelected = selectedSubtitle == null,
                                onClick = { selectedSubtitle = null }
                            )
                        }
                        items(state.extractedSubtitles) { sub ->
                            CloudStreamFilterChip(
                                label = sub.lang,
                                isSelected = selectedSubtitle == sub,
                                onClick = { selectedSubtitle = sub }
                            )
                        }
                    }
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isExtractingLinks) {
                    GhostButton(
                        text = stringResource(Res.string.cancel),
                        onClick = { onEvent(ResultEvent.ClearLinks) },
                        contentColor = CloudStreamColors.Error
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                SecondaryButton(
                    text = stringResource(Res.string.close),
                    onClick = onDismiss
                )
            }
        }
    )
}

@Composable
fun ExtractionLoadingBanner(
    progressCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        backgroundColor = CloudStreamColors.Primary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.4f)),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = CloudStreamColors.Primary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )

            Column {
                Text(
                    text = stringResource(Res.string.extractingLinks),
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface
                    )
                )
                BodyMutedText(
                    text = "$progressCount ${stringResource(Res.string.resultsFound)}",
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ExtractionErrorBanner(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorLabel = stringResource(Res.string.error)
    val retryLabel = stringResource(Res.string.retry)

    Card(
        shape = RoundedCornerShape(8.dp),
        backgroundColor = CloudStreamColors.Error.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, CloudStreamColors.Error.copy(alpha = 0.4f)),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = errorLabel,
                    tint = CloudStreamColors.Error,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = error,
                    style = MaterialTheme.typography.caption.copy(
                        color = MaterialTheme.colors.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = retryLabel,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ExtractorLinkItem(
    link: ExtractorLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qualityBadgeColor = when {
        link.quality >= 2160 -> CloudStreamColors.Quality4K
        link.quality >= 1080 -> CloudStreamColors.QualityHD
        link.quality >= 720 -> CloudStreamColors.QualityHQ
        link.quality >= 480 -> CloudStreamColors.QualitySD
        else -> CloudStreamColors.QualitySD
    }

    val qualityText = when {
        link.quality >= 2160 -> "4K"
        link.quality > 0 -> "${link.quality}p"
        else -> stringResource(Res.string.quality_auto)
    }

    SelectableOptionCard(
        title = link.name.ifBlank { link.source },
        subtitle = "${link.source} • ${link.type.name}",
        isSelected = false,
        onClick = onClick,
        leadingContent = {
            MediaBadge(
                text = qualityText,
                backgroundColor = qualityBadgeColor
            )
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(CloudStreamColors.Primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                    contentDescription = stringResource(Res.string.action_play),
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        modifier = modifier
    )
}

