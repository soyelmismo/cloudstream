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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.ui.components.MediaBadge
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class AudioCategory(val labelRes: StringResource) {
    ALL(Res.string.audio_filter_all),
    LATINO(Res.string.audio_filter_latino),
    CASTELLANO(Res.string.audio_filter_castellano),
    ENGLISH(Res.string.audio_filter_english),
    SUBTITLED(Res.string.audio_filter_subbed);
}

private data class AudioMatcher(
    val category: AudioCategory,
    val keywords: List<String>,
    val regex: Regex
)

private val AUDIO_MATCHERS: List<AudioMatcher> = listOf(
    AudioMatcher(
        AudioCategory.LATINO,
        listOf("latino", "latam"),
        Regex("""\b(lat)\b""", RegexOption.IGNORE_CASE)
    ),
    AudioMatcher(
        AudioCategory.CASTELLANO,
        listOf("castellano", "español", "espanol"),
        Regex("""\b(cast|spa|esp)\b""", RegexOption.IGNORE_CASE)
    ),
    AudioMatcher(
        AudioCategory.ENGLISH,
        listOf("english"),
        Regex("""\b(eng|dub|dubbed)\b""", RegexOption.IGNORE_CASE)
    ),
    AudioMatcher(
        AudioCategory.SUBTITLED,
        listOf("subtitulado", "subbed", "vose"),
        Regex("""\b(sub|vos)\b""", RegexOption.IGNORE_CASE)
    )
)

private fun matchesAudio(content: String, matcher: AudioMatcher): Boolean {
    return matcher.keywords.any { content.contains(it) } || matcher.regex.containsMatchIn(content)
}

private fun buildSearchContent(link: ExtractorLink): String {
    return buildString {
        append(link.name).append(' ')
        append(link.source).append(' ')
        for (track in link.audioTracks) {
            append(track.url).append(' ')
        }
    }.lowercase()
}

fun ExtractorLink.detectAudioCategory(): AudioCategory {
    val searchContent = buildSearchContent(this)
    return AUDIO_MATCHERS.firstOrNull { matchesAudio(searchContent, it) }?.category ?: AudioCategory.ALL
}

fun computeAvailableAudioCategories(links: ImmutableList<ExtractorLink>): ImmutableList<AudioCategory> {
    if (links.isEmpty()) return persistentListOf()
    val detected = links.map { it.detectAudioCategory() }.toSet()
    val hasSpecific = detected.any { it != AudioCategory.ALL }
    if (!hasSpecific && detected.size <= 1) return persistentListOf()

    val specificCategories = AudioCategory.entries.filter { it != AudioCategory.ALL && detected.contains(it) }
    return (persistentListOf(AudioCategory.ALL) + specificCategories).toImmutableList()
}

fun getExtractorQualityColor(quality: Int): Color = when {
    quality >= 2160 -> CloudStreamColors.Quality4K
    quality >= 1080 -> CloudStreamColors.QualityHD
    quality >= 720 -> CloudStreamColors.QualityHQ
    else -> CloudStreamColors.QualitySD
}

@Composable
fun getExtractorQualityText(quality: Int): String = when {
    quality >= 2160 -> "4K"
    quality > 0 -> "${quality}p"
    else -> stringResource(Res.string.quality_auto)
}

private fun buildResultLinksSubtitle(
    targetEpisode: ResultEpisode?,
    isMovie: Boolean,
    movieTitle: String,
    selectLinkText: String,
    episodeText: String
): String {
    if (targetEpisode != null) return targetEpisode.name ?: "$episodeText ${targetEpisode.episode}"
    if (isMovie) return movieTitle
    return selectLinkText
}

@Composable
fun ResultLinksDialog(
    state: ResultState,
    targetEpisode: ResultEpisode?,
    onPlayLink: (ExtractorLink, ImmutableList<ExtractorLink>, ImmutableList<SubtitleFile>, SubtitleFile?) -> Unit,
    onEvent: (ResultEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAudioCategory by remember { mutableStateOf(AudioCategory.ALL) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleFile?>(null) }

    val availableAudioCategories = remember(state.extractedLinks) {
        computeAvailableAudioCategories(state.extractedLinks)
    }

    val filteredLinks = remember(state.extractedLinks, selectedAudioCategory) {
        if (selectedAudioCategory == AudioCategory.ALL) {
            state.extractedLinks
        } else {
            state.extractedLinks.filter { it.detectAudioCategory() == selectedAudioCategory }.toImmutableList()
        }
    }

    val selectLinkText = stringResource(Res.string.selectLink)
    val episodeText = stringResource(Res.string.episode)
    val subtitleText = buildResultLinksSubtitle(targetEpisode, state.isMovie, state.title, selectLinkText, episodeText)

    ActionDialog(
        onDismissRequest = onDismiss,
        title = selectLinkText,
        subtitle = subtitleText,
        showCloseButton = true,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                if (state.isExtractingLinks) {
                    ExtractionLoadingBanner(progressCount = state.linksLoadingProgress)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!state.linksLoadingError.isNullOrBlank()) {
                    ExtractionErrorBanner(
                        error = state.linksLoadingError,
                        onRetry = { onEvent(ResultEvent.ReloadLinks(targetEpisode)) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (availableAudioCategories.size > 1 && state.extractedLinks.isNotEmpty()) {
                    AudioCategorySelector(
                        categories = availableAudioCategories,
                        selectedCategory = selectedAudioCategory,
                        onSelectCategory = { selectedAudioCategory = it }
                    )
                }

                if (state.extractedLinks.isNotEmpty()) {
                    ExtractedLinksList(
                        links = filteredLinks,
                        onLinkClick = { link ->
                            onPlayLink(link, state.extractedLinks, state.extractedSubtitles, selectedSubtitle)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else if (!state.isExtractingLinks && state.linksLoadingError.isNullOrBlank()) {
                    EmptyLinksBanner(modifier = Modifier.padding(vertical = 24.dp))
                }

                if (state.extractedSubtitles.isNotEmpty()) {
                    SubtitleSelector(
                        subtitles = state.extractedSubtitles,
                        selectedSubtitle = selectedSubtitle,
                        onSelectSubtitle = { selectedSubtitle = it }
                    )
                }
            }
        },
        buttons = {
            ResultLinksDialogButtons(
                isExtracting = state.isExtractingLinks,
                onCancelExtraction = { onEvent(ResultEvent.ClearLinks) },
                onDismiss = onDismiss
            )
        }
    )
}

@Composable
fun AudioCategorySelector(
    categories: ImmutableList<AudioCategory>,
    selectedCategory: AudioCategory,
    onSelectCategory: (AudioCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        items(categories) { category ->
            CloudStreamFilterChip(
                labelRes = category.labelRes,
                isSelected = selectedCategory == category,
                onClick = { onSelectCategory(category) }
            )
        }
    }
}

@Composable
fun ExtractedLinksList(
    links: ImmutableList<ExtractorLink>,
    onLinkClick: (ExtractorLink) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${stringResource(Res.string.selectLink)} (${links.size})",
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.Bold,
                color = CloudStreamColors.TextSecondary
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (links.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(links) { link ->
                    ExtractorLinkItem(
                        link = link,
                        onClick = { onLinkClick(link) }
                    )
                }
            }
        } else {
            EmptyLinksBanner()
        }
    }
}

@Composable
fun EmptyLinksBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        BodyMutedText(text = stringResource(Res.string.noLinksFound))
    }
}

@Composable
fun SubtitleSelector(
    subtitles: ImmutableList<SubtitleFile>,
    selectedSubtitle: SubtitleFile?,
    onSelectSubtitle: (SubtitleFile?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                    onClick = { onSelectSubtitle(null) }
                )
            }
            items(subtitles) { sub ->
                CloudStreamFilterChip(
                    label = sub.lang,
                    isSelected = selectedSubtitle == sub,
                    onClick = { onSelectSubtitle(sub) }
                )
            }
        }
    }
}

@Composable
private fun ResultLinksDialogButtons(
    isExtracting: Boolean,
    onCancelExtraction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExtracting) {
            GhostButton(
                text = stringResource(Res.string.cancel),
                onClick = onCancelExtraction,
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
    SelectableOptionCard(
        title = link.name.ifBlank { link.source },
        subtitle = "${link.source} • ${link.type.name}",
        isSelected = false,
        onClick = onClick,
        leadingContent = {
            MediaBadge(
                text = getExtractorQualityText(link.quality),
                backgroundColor = getExtractorQualityColor(link.quality)
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

@Preview
@Composable
private fun ResultLinksDialogPreview() {
    CloudStreamTheme {
        ResultLinksDialog(
            state = ResultState(
                title = "Inception",
                isMovie = true,
                extractedLinks = persistentListOf(
                    ExtractorLink(
                        source = "Server 1",
                        name = "Server 1 HD",
                        url = "https://example.com/1",
                        referer = "",
                        quality = 1080,
                        type = ExtractorLinkType.VIDEO
                    )
                )
            ),
            targetEpisode = null,
            onPlayLink = { _, _, _, _ -> },
            onEvent = {},
            onDismiss = {}
        )
    }
}


