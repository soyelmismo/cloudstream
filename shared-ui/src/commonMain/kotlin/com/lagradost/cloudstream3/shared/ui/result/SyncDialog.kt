package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.DangerButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncEntry
import com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncStatus
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.shared.viewmodels.result.SmileyRating
import com.lagradost.cloudstream3.shared.viewmodels.result.SyncService
import com.lagradost.cloudstream3.shared.viewmodels.result.TrackerScoreScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Modern External Synchronization Dialog for Compose Multiplatform (Desktop / Android / TV)
 * utilizing standardized [ActionDialog], [PrimaryButton], [SecondaryButton], [CloudStreamTextField],
 * and semantic typography.
 *
 * Features:
 * 1. Tracking Service Selector: AniList, MyAnimeList, Simkl, Kitsu tabs with visual brand indicators.
 * 2. Sync Status Selector: Watching, Completed, Plan to Watch, Paused, Dropped, Unlinked with themed badges.
 * 3. Interactive Multi-Scale Score / Rating Engine:
 *    - 10-point decimal (e.g. 8.5/10 with slider & step presets)
 *    - 100-point integer (e.g. 85/100 with slider & step presets)
 *    - 5-star rating (1 to 5 stars)
 *    - 3-point smiley rating (Sad, Neutral, Happy)
 * 4. Episode Watched Counter: [-] and [+] step buttons, direct numeric [CloudStreamTextField], and total episode bounds.
 * 5. Tracking ID & Link Management: view and customize remote IDs for precise metadata sync.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncDialog(
    state: ResultState,
    onEvent: (ResultEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeService by remember(state.selectedSyncService) {
        mutableStateOf(state.selectedSyncService)
    }

    val currentEntry = state.externalSyncStates[activeService] ?: ExternalSyncEntry(activeService)

    // Local form state
    var selectedStatus by remember(activeService, currentEntry.status) {
        mutableStateOf(currentEntry.status)
    }
    var selectedScale by remember(activeService, currentEntry.scoreScale) {
        mutableStateOf(currentEntry.scoreScale)
    }
    var currentScore by remember(activeService, currentEntry.effectiveScore) {
        mutableStateOf(currentEntry.effectiveScore)
    }
    var watchedEpisodes by remember(activeService, currentEntry.watchedEpisodes) {
        mutableStateOf(currentEntry.watchedEpisodes)
    }
    var episodeText by remember(activeService, currentEntry.watchedEpisodes) {
        mutableStateOf(currentEntry.watchedEpisodes.toString())
    }
    var customSyncId by remember(activeService, currentEntry.syncId) {
        mutableStateOf(currentEntry.syncId ?: "")
    }
    var isAdvancedExpanded by remember(activeService) {
        mutableStateOf(false)
    }

    val maxEpisodes = currentEntry.maxEpisodes ?: state.episodes.size.takeIf { it > 0 }

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.syncTitle,
        subtitle = state.title.ifBlank { null },
        icon = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(activeService.brandColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.baseline_sync_24),
                    contentDescription = stringResource(Res.string.syncButton),
                    tint = activeService.brandColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        maxWidth = 520.dp,
        showCloseButton = true,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =============================================================
                // 1. TRACKING SERVICE SELECTOR (AniList, MAL, Simkl, Kitsu)
                // =============================================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.syncService),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SyncService.entries.forEach { service ->
                            val isSelected = activeService == service
                            val serviceEntry = state.externalSyncStates[service]
                            val isServiceLinked = serviceEntry?.hasTracking == true

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = if (isSelected) {
                                    service.brandColor.copy(alpha = 0.22f)
                                } else {
                                    CloudStreamColors.SurfaceVariant
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, service.brandColor)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                                },
                                elevation = 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeService = service
                                        onEvent(ResultEvent.SelectSyncService(service))
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = service.serviceName,
                                            style = MaterialTheme.typography.caption.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) service.brandColor else CloudStreamColors.TextPrimary,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (isServiceLinked) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(CloudStreamColors.Success, CircleShape)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = if (isServiceLinked) stringResource(Res.string.syncLinked) else stringResource(Res.string.syncNotLinked),
                                        style = MaterialTheme.typography.caption.copy(
                                            color = if (isServiceLinked) CloudStreamColors.Success else CloudStreamColors.TextMuted,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 2. SYNC STATUS SELECTOR
                // (Watching, Completed, Plan to Watch, Paused, Dropped, None)
                // =============================================================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.syncStatus),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusOptions = listOf(
                            ExternalSyncStatus.Watching to stringResource(Res.string.syncStatusWatching),
                            ExternalSyncStatus.Completed to stringResource(Res.string.syncStatusCompleted),
                            ExternalSyncStatus.PlanToWatch to stringResource(Res.string.syncStatusPlanToWatch),
                            ExternalSyncStatus.Paused to stringResource(Res.string.syncStatusPaused),
                            ExternalSyncStatus.Dropped to stringResource(Res.string.syncStatusDropped),
                            ExternalSyncStatus.None to stringResource(Res.string.syncStatusNone)
                        )

                        statusOptions.forEach { (status, label) ->
                            val isSelected = selectedStatus == status
                            val chipColor = status.color

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) chipColor.copy(alpha = 0.22f) else CloudStreamColors.SurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) chipColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier.clickable {
                                    selectedStatus = status
                                    if (status == ExternalSyncStatus.Completed && maxEpisodes != null && maxEpisodes > 0) {
                                        watchedEpisodes = maxEpisodes
                                        episodeText = maxEpisodes.toString()
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(chipColor, CircleShape)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.caption.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) chipColor else CloudStreamColors.TextPrimary,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 3. SCORE / RATING MULTI-SCALE SELECTOR
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CloudStreamColors.SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header & Active Score Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.syncScore),
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (currentScore != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CloudStreamColors.Warning.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, CloudStreamColors.Warning.copy(alpha = 0.6f))
                                ) {
                                    val badgeText = when (selectedScale) {
                                        TrackerScoreScale.Point10Decimal -> {
                                            val value = selectedScale.toDisplayValue(currentScore) ?: 0.0
                                            val formatted = if (value % 1.0 == 0.0) "${value.toInt()}" else "$value"
                                            "★ $formatted ${stringResource(Res.string.sync_score_out_of_ten)}"
                                        }
                                        TrackerScoreScale.Point100 -> {
                                            val value = selectedScale.toDisplayValue(currentScore)?.roundToInt() ?: 0
                                            "★ $value ${stringResource(Res.string.sync_score_out_of_hundred)}"
                                        }
                                        TrackerScoreScale.Point5Star -> {
                                            val value = selectedScale.toDisplayValue(currentScore)?.roundToInt() ?: 0
                                            "★ $value ${stringResource(Res.string.sync_score_out_of_five)}"
                                        }
                                        TrackerScoreScale.Point3Smiley -> {
                                            val smiley = SmileyRating.fromScore(currentScore)
                                            if (smiley != null) {
                                                "${smiley.emoji} ${stringResource(smiley.stringRes)}"
                                            } else {
                                                stringResource(Res.string.sync_score_smiley_rating)
                                            }
                                        }
                                    }

                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.caption.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = CloudStreamColors.Warning,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = stringResource(Res.string.clear),
                                    style = MaterialTheme.typography.caption.copy(
                                        color = CloudStreamColors.TextMuted,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .clickable { currentScore = null }
                                        .padding(start = 4.dp)
                                )
                            } else {
                                BodyMutedText(
                                    text = stringResource(Res.string.syncNoScore),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Score Scale Format Selector (10-Point Decimal, 100-Point, 5-Star, 3-Point Smiley)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrackerScoreScale.entries.forEach { scale ->
                            val isScaleSelected = selectedScale == scale
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isScaleSelected) activeService.brandColor.copy(alpha = 0.25f) else CloudStreamColors.SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isScaleSelected) activeService.brandColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedScale = scale
                                    }
                            ) {
                                Text(
                                    text = stringResource(scale.stringRes),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = if (isScaleSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isScaleSelected) activeService.brandColor else CloudStreamColors.TextSecondary,
                                        fontSize = 10.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    // Dynamic Scale Controls
                    when (selectedScale) {
                        TrackerScoreScale.Point10Decimal -> {
                            val currentDecimal = (currentScore?.toDouble(10)?.let { (it * 10.0).roundToInt() / 10.0 } ?: 0.0).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Slider(
                                    value = currentDecimal,
                                    onValueChange = { value ->
                                        val rounded = ((value * 10.0).roundToInt() / 10.0)
                                        currentScore = if (rounded <= 0.0) null else Score.from(rounded, 10)
                                    },
                                    valueRange = 0f..10f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(
                                        thumbColor = activeService.brandColor,
                                        activeTrackColor = activeService.brandColor,
                                        inactiveTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CloudStreamColors.SurfaceElevated,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val prev = ((currentDecimal - 0.5f).coerceAtLeast(0f) * 10f).roundToInt() / 10.0
                                                currentScore = if (prev <= 0.0) null else Score.from(prev, 10)
                                            }
                                    ) {
                                        Text(
                                            text = "-0.5",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = CloudStreamColors.TextSecondary,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(vertical = 5.dp)
                                        )
                                    }

                                    listOf(5.0, 7.0, 8.0, 9.0, 10.0).forEach { preset ->
                                        val isPresetActive = currentScore != null && kotlin.math.abs(currentDecimal - preset.toFloat()) < 0.05f
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isPresetActive) activeService.brandColor.copy(alpha = 0.25f) else CloudStreamColors.SurfaceElevated,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isPresetActive) activeService.brandColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    currentScore = if (isPresetActive) null else Score.from(preset, 10)
                                                }
                                        ) {
                                            Text(
                                                text = if (preset % 1.0 == 0.0) "${preset.toInt()}" else "$preset",
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.caption.copy(
                                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isPresetActive) activeService.brandColor else CloudStreamColors.TextPrimary,
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.padding(vertical = 5.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CloudStreamColors.SurfaceElevated,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val next = ((currentDecimal + 0.5f).coerceAtMost(10f) * 10f).roundToInt() / 10.0
                                                currentScore = Score.from(next, 10)
                                            }
                                    ) {
                                        Text(
                                            text = "+0.5",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = CloudStreamColors.TextSecondary,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                        TrackerScoreScale.Point100 -> {
                            val current100 = (currentScore?.toDouble(100)?.roundToInt() ?: 0).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Slider(
                                    value = current100,
                                    onValueChange = { value ->
                                        val scoreInt = value.roundToInt()
                                        currentScore = if (scoreInt <= 0) null else Score.from100(scoreInt)
                                    },
                                    valueRange = 0f..100f,
                                    steps = 99,
                                    colors = SliderDefaults.colors(
                                        thumbColor = activeService.brandColor,
                                        activeTrackColor = activeService.brandColor,
                                        inactiveTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CloudStreamColors.SurfaceElevated,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val prev = (current100.toInt() - 5).coerceAtLeast(0)
                                                currentScore = if (prev <= 0) null else Score.from100(prev)
                                            }
                                    ) {
                                        Text(
                                            text = "-5",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = CloudStreamColors.TextSecondary,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(vertical = 5.dp)
                                        )
                                    }

                                    listOf(50, 70, 80, 90, 100).forEach { preset ->
                                        val isPresetActive = currentScore != null && current100.toInt() == preset
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isPresetActive) activeService.brandColor.copy(alpha = 0.25f) else CloudStreamColors.SurfaceElevated,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isPresetActive) activeService.brandColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    currentScore = if (isPresetActive) null else Score.from100(preset)
                                                }
                                        ) {
                                            Text(
                                                text = "$preset",
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.caption.copy(
                                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isPresetActive) activeService.brandColor else CloudStreamColors.TextPrimary,
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.padding(vertical = 5.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CloudStreamColors.SurfaceElevated,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val next = (current100.toInt() + 5).coerceAtMost(100)
                                                currentScore = Score.from100(next)
                                            }
                                    ) {
                                        Text(
                                            text = "+5",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = CloudStreamColors.TextSecondary,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                        TrackerScoreScale.Point5Star -> {
                            val currentStar = currentScore?.toDouble(5)?.roundToInt()?.coerceIn(1, 5)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (star in 1..5) {
                                    val isFilled = currentScore != null && star <= (currentStar ?: 0)
                                    val starColor = if (isFilled) CloudStreamColors.Warning else MaterialTheme.colors.onSurface.copy(alpha = 0.25f)

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                currentScore = if (currentStar == star) null else Score.from5(star)
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isFilled) Res.drawable.ic_baseline_star_24 else Res.drawable.ic_baseline_star_border_24),
                                            contentDescription = "$star ${stringResource(Res.string.sync_score_out_of_five)}",
                                            tint = starColor,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = "$star",
                                            style = MaterialTheme.typography.caption.copy(
                                                color = if (isFilled) CloudStreamColors.Warning else CloudStreamColors.TextMuted,
                                                fontWeight = if (isFilled) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        TrackerScoreScale.Point3Smiley -> {
                            val activeSmiley = SmileyRating.fromScore(currentScore)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SmileyRating.entries.forEach { smiley ->
                                    val isSelected = activeSmiley == smiley
                                    val smileyColor = when (smiley) {
                                        SmileyRating.Sad -> CloudStreamColors.Error
                                        SmileyRating.Neutral -> CloudStreamColors.Warning
                                        SmileyRating.Happy -> CloudStreamColors.Success
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) smileyColor.copy(alpha = 0.22f) else CloudStreamColors.SurfaceElevated,
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) smileyColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                currentScore = if (isSelected) null else Score.from(smiley.scoreValue, 3)
                                            }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = smiley.emoji,
                                                fontSize = 24.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(smiley.stringRes),
                                                style = MaterialTheme.typography.caption.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) smileyColor else CloudStreamColors.TextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 4. EPISODES WATCHED COUNTER (- / + and Direct Numeric Input)
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CloudStreamColors.SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.syncEpisodes),
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )

                        if (maxEpisodes != null && maxEpisodes > 0) {
                            BodyMutedText(
                                text = "${stringResource(Res.string.syncTotalEpisodes)}: $maxEpisodes",
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // [-] Decrement Button
                        IconButton(
                            onClick = {
                                if (watchedEpisodes > 0) {
                                    val next = watchedEpisodes - 1
                                    watchedEpisodes = next
                                    episodeText = next.toString()
                                }
                            },
                            enabled = watchedEpisodes > 0,
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (watchedEpisodes > 0) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceElevated.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.baseline_skip_previous_24),
                                contentDescription = stringResource(Res.string.decrement),
                                tint = if (watchedEpisodes > 0) CloudStreamColors.TextPrimary else CloudStreamColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Direct Numeric Input Field
                        CloudStreamTextField(
                            value = episodeText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(5)
                                episodeText = filtered
                                val parsed = filtered.toIntOrNull() ?: 0
                                watchedEpisodes = if (maxEpisodes != null && maxEpisodes > 0) {
                                    parsed.coerceIn(0, maxEpisodes)
                                } else {
                                    parsed.coerceAtLeast(0)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.h6.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextPrimary,
                                fontSize = 18.sp
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        )

                        // [+] Increment Button
                        IconButton(
                            onClick = {
                                val next = watchedEpisodes + 1
                                val coerced = if (maxEpisodes != null && maxEpisodes > 0) {
                                    next.coerceAtMost(maxEpisodes)
                                } else next
                                watchedEpisodes = coerced
                                episodeText = coerced.toString()

                                if (maxEpisodes != null && coerced >= maxEpisodes && maxEpisodes > 0) {
                                    selectedStatus = ExternalSyncStatus.Completed
                                } else if (coerced > 0 && selectedStatus == ExternalSyncStatus.PlanToWatch) {
                                    selectedStatus = ExternalSyncStatus.Watching
                                }
                            },
                            enabled = maxEpisodes == null || watchedEpisodes < maxEpisodes,
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (maxEpisodes == null || watchedEpisodes < maxEpisodes) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceElevated.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Res.string.increment),
                                tint = if (maxEpisodes == null || watchedEpisodes < maxEpisodes) CloudStreamColors.TextPrimary else CloudStreamColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Quick episode shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Reset to 0
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CloudStreamColors.SurfaceElevated,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    watchedEpisodes = 0
                                    episodeText = "0"
                                }
                        ) {
                            Text(
                                text = "0",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextSecondary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // +1
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CloudStreamColors.SurfaceElevated,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val next = (watchedEpisodes + 1).let {
                                        if (maxEpisodes != null && maxEpisodes > 0) it.coerceAtMost(maxEpisodes) else it
                                    }
                                    watchedEpisodes = next
                                    episodeText = next.toString()
                                }
                        ) {
                            Text(
                                text = "+1",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextSecondary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // +5
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CloudStreamColors.SurfaceElevated,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val next = (watchedEpisodes + 5).let {
                                        if (maxEpisodes != null && maxEpisodes > 0) it.coerceAtMost(maxEpisodes) else it
                                    }
                                    watchedEpisodes = next
                                    episodeText = next.toString()
                                }
                        ) {
                            Text(
                                text = "+5",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextSecondary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // All (Max)
                        if (maxEpisodes != null && maxEpisodes > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = activeService.brandColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, activeService.brandColor.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clickable {
                                        watchedEpisodes = maxEpisodes
                                        episodeText = maxEpisodes.toString()
                                        selectedStatus = ExternalSyncStatus.Completed
                                    }
                            ) {
                                Text(
                                    text = "${stringResource(Res.string.all)} ($maxEpisodes)",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = activeService.brandColor,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 5. ADVANCED ID / URL LINK SECTION (Expandable)
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CloudStreamColors.SurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_link_24),
                                contentDescription = null,
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(Res.string.syncIdLabel),
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = CloudStreamColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = CloudStreamColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CloudStreamTextField(
                                value = customSyncId,
                                onValueChange = { customSyncId = it },
                                placeholder = stringResource(Res.string.syncIdPlaceholder),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
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
                // Left: Unlink button (if active service is linked)
                if (currentEntry.hasTracking) {
                    GhostButton(
                        text = stringResource(Res.string.syncUnlinkButton),
                        icon = Icons.Default.Delete,
                        contentColor = CloudStreamColors.Error,
                        onClick = {
                            onEvent(ResultEvent.UnlinkSyncService(activeService))
                            selectedStatus = ExternalSyncStatus.None
                            customSyncId = ""
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Right: Cancel & Save Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = stringResource(Res.string.cancel),
                        onClick = onDismiss
                    )

                    PrimaryButton(
                        onClick = {
                            onEvent(
                                ResultEvent.SaveSyncData(
                                    service = activeService,
                                    syncId = customSyncId.ifBlank { null },
                                    status = selectedStatus,
                                    score = currentScore?.toInt(10),
                                    rawScore = currentScore,
                                    scoreScale = selectedScale,
                                    watchedEpisodes = watchedEpisodes,
                                    maxEpisodes = maxEpisodes
                                )
                            )
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.syncSaveButton),
                            style = MaterialTheme.typography.button.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onPrimary
                            )
                        )
                    }
                }
            }
        }
    )
}
