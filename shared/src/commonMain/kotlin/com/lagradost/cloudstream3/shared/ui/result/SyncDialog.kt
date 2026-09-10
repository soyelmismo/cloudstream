package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncEntry
import com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncStatus
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.shared.viewmodels.result.SmileyRating
import com.lagradost.cloudstream3.shared.viewmodels.result.SyncService
import com.lagradost.cloudstream3.shared.viewmodels.result.TrackerScoreScale
import kotlinx.collections.immutable.ImmutableMap
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

@Composable
fun SyncServiceTabs(
    activeService: SyncService,
    syncStates: ImmutableMap<SyncService, ExternalSyncEntry>,
    onSelectService: (SyncService) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                val serviceEntry = syncStates[service]
                val isServiceLinked = serviceEntry?.hasTracking == true
                val backgroundColor = if (isSelected) service.brandColor.copy(alpha = 0.22f) else CloudStreamColors.SurfaceVariant
                val border = if (isSelected) BorderStroke(1.5.dp, service.brandColor) else BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

                Card(
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = backgroundColor,
                    border = border,
                    elevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .dpadFocusable(
                            onClick = { onSelectService(service) },
                            shape = RoundedCornerShape(10.dp)
                        )
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

                        val statusRes = if (isServiceLinked) Res.string.syncLinked else Res.string.syncNotLinked
                        val statusColor = if (isServiceLinked) CloudStreamColors.Success else CloudStreamColors.TextMuted
                        Text(
                            text = stringResource(statusRes),
                            style = MaterialTheme.typography.caption.copy(
                                color = statusColor,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncStatusSelector(
    selectedStatus: ExternalSyncStatus,
    onSelectStatus: (ExternalSyncStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.syncStatus),
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.Bold,
                color = CloudStreamColors.TextSecondary,
                fontSize = 12.sp
            )
        )

        val statusOptions = listOf(
            ExternalSyncStatus.Watching to stringResource(Res.string.syncStatusWatching),
            ExternalSyncStatus.Completed to stringResource(Res.string.syncStatusCompleted),
            ExternalSyncStatus.PlanToWatch to stringResource(Res.string.syncStatusPlanToWatch),
            ExternalSyncStatus.Paused to stringResource(Res.string.syncStatusPaused),
            ExternalSyncStatus.Dropped to stringResource(Res.string.syncStatusDropped),
            ExternalSyncStatus.None to stringResource(Res.string.syncStatusNone)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statusOptions.forEach { (status, label) ->
                val isSelected = selectedStatus == status
                val chipColor = status.color
                val background = if (isSelected) chipColor.copy(alpha = 0.22f) else CloudStreamColors.SurfaceVariant
                val border = BorderStroke(1.dp, if (isSelected) chipColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = background,
                    border = border,
                    modifier = Modifier.dpadFocusable(
                        onClick = { onSelectStatus(status) },
                        shape = RoundedCornerShape(8.dp)
                    )
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
}

private fun formatScoreBadge(
    score: Score?,
    scale: TrackerScoreScale,
    outOfTenText: String,
    outOfHundredText: String,
    outOfFiveText: String,
    smileyRatingText: String
): String = when (scale) {
    TrackerScoreScale.Point10Decimal -> {
        val value = scale.toDisplayValue(score) ?: 0.0
        val formatted = if (value % 1.0 == 0.0) "${value.toInt()}" else "$value"
        "★ $formatted $outOfTenText"
    }
    TrackerScoreScale.Point100 -> {
        val value = scale.toDisplayValue(score)?.roundToInt() ?: 0
        "★ $value $outOfHundredText"
    }
    TrackerScoreScale.Point5Star -> {
        val value = scale.toDisplayValue(score)?.roundToInt() ?: 0
        "★ $value $outOfFiveText"
    }
    TrackerScoreScale.Point3Smiley -> {
        val smiley = SmileyRating.fromScore(score)
        if (smiley != null) "${smiley.emoji} ${smiley.name}" else smileyRatingText
    }
}

@Composable
fun ScoreHeaderBadge(
    currentScore: Score?,
    selectedScale: TrackerScoreScale,
    onClearScore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                    val outOfTen = stringResource(Res.string.sync_score_out_of_ten)
                    val outOfHundred = stringResource(Res.string.sync_score_out_of_hundred)
                    val outOfFive = stringResource(Res.string.sync_score_out_of_five)
                    val smileyRating = stringResource(Res.string.sync_score_smiley_rating)

                    val badgeText = when (selectedScale) {
                        TrackerScoreScale.Point3Smiley -> {
                            val smiley = SmileyRating.fromScore(currentScore)
                            if (smiley != null) "${smiley.emoji} ${stringResource(smiley.stringRes)}" else smileyRating
                        }
                        else -> formatScoreBadge(currentScore, selectedScale, outOfTen, outOfHundred, outOfFive, smileyRating)
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
                        .dpadFocusable(
                            onClick = onClearScore,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                BodyMutedText(
                    text = stringResource(Res.string.syncNoScore),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ScoreScaleTabs(
    selectedScale: TrackerScoreScale,
    brandColor: Color,
    onSelectScale: (TrackerScoreScale) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TrackerScoreScale.entries.forEach { scale ->
            val isScaleSelected = selectedScale == scale
            val background = if (isScaleSelected) brandColor.copy(alpha = 0.25f) else CloudStreamColors.SurfaceElevated
            val border = BorderStroke(1.dp, if (isScaleSelected) brandColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = background,
                border = border,
                modifier = Modifier
                    .weight(1f)
                    .dpadFocusable(
                        onClick = { onSelectScale(scale) },
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                Text(
                    text = stringResource(scale.stringRes),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = if (isScaleSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isScaleSelected) brandColor else CloudStreamColors.TextSecondary,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DecimalScoreEditor(
    currentScore: Score?,
    brandColor: Color,
    onScoreChange: (Score?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentDecimal = (currentScore?.toDouble(10)?.let { (it * 10.0).roundToInt() / 10.0 } ?: 0.0).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Slider(
            value = currentDecimal,
            onValueChange = { value ->
                val rounded = ((value * 10.0).roundToInt() / 10.0)
                onScoreChange(if (rounded <= 0.0) null else Score.from(rounded, 10))
            },
            valueRange = 0f..10f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = brandColor,
                activeTrackColor = brandColor,
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
                    .dpadFocusable(
                        onClick = {
                            val prev = ((currentDecimal - 0.5f).coerceAtLeast(0f) * 10f).roundToInt() / 10.0
                            onScoreChange(if (prev <= 0.0) null else Score.from(prev, 10))
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
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
                val background = if (isPresetActive) brandColor.copy(alpha = 0.25f) else CloudStreamColors.SurfaceElevated
                val border = BorderStroke(1.dp, if (isPresetActive) brandColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = background,
                    border = border,
                    modifier = Modifier
                        .weight(1f)
                        .dpadFocusable(
                            onClick = {
                                onScoreChange(if (isPresetActive) null else Score.from(preset, 10))
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                ) {
                    Text(
                        text = if (preset % 1.0 == 0.0) "${preset.toInt()}" else "$preset",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPresetActive) brandColor else CloudStreamColors.TextPrimary,
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
                    .dpadFocusable(
                        onClick = {
                            val next = ((currentDecimal + 0.5f).coerceAtMost(10f) * 10f).roundToInt() / 10.0
                            onScoreChange(Score.from(next, 10))
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
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

@Composable
fun HundredScoreEditor(
    currentScore: Score?,
    brandColor: Color,
    onScoreChange: (Score?) -> Unit,
    modifier: Modifier = Modifier
) {
    val current100 = (currentScore?.toDouble(100)?.roundToInt() ?: 0).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Slider(
            value = current100,
            onValueChange = { value ->
                val scoreInt = value.roundToInt()
                onScoreChange(if (scoreInt <= 0) null else Score.from100(scoreInt))
            },
            valueRange = 0f..100f,
            steps = 99,
            colors = SliderDefaults.colors(
                thumbColor = brandColor,
                activeTrackColor = brandColor,
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
                    .dpadFocusable(
                        onClick = {
                            val prev = (current100.toInt() - 5).coerceAtLeast(0)
                            onScoreChange(if (prev <= 0) null else Score.from100(prev))
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
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
                val background = if (isPresetActive) brandColor.copy(alpha = 0.25f) else CloudStreamColors.SurfaceElevated
                val border = BorderStroke(1.dp, if (isPresetActive) brandColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = background,
                    border = border,
                    modifier = Modifier
                        .weight(1f)
                        .dpadFocusable(
                            onClick = {
                                onScoreChange(if (isPresetActive) null else Score.from100(preset))
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                ) {
                    Text(
                        text = "$preset",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPresetActive) brandColor else CloudStreamColors.TextPrimary,
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
                    .dpadFocusable(
                        onClick = {
                            val next = (current100.toInt() + 5).coerceAtMost(100)
                            onScoreChange(Score.from100(next))
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
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

@Composable
fun FiveStarScoreEditor(
    currentScore: Score?,
    onScoreChange: (Score?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStar = currentScore?.toDouble(5)?.roundToInt()?.coerceIn(1, 5)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (star in 1..5) {
            val isFilled = currentScore != null && star <= (currentStar ?: 0)
            val starColor = if (isFilled) CloudStreamColors.Warning else MaterialTheme.colors.onSurface.copy(alpha = 0.25f)
            val iconRes = if (isFilled) Res.drawable.ic_baseline_star_24 else Res.drawable.ic_baseline_star_border_24

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .dpadFocusable(
                        onClick = {
                            onScoreChange(if (currentStar == star) null else Score.from5(star))
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(4.dp)
            ) {
                Icon(
                    painter = painterResource(iconRes),
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

@Composable
fun SmileyScoreEditor(
    currentScore: Score?,
    onScoreChange: (Score?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSmiley = SmileyRating.fromScore(currentScore)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmileyRating.entries.forEach { smiley ->
            val isSelected = activeSmiley == smiley
            val smileyColor = when (smiley) {
                SmileyRating.Sad -> CloudStreamColors.Error
                SmileyRating.Neutral -> CloudStreamColors.Warning
                SmileyRating.Happy -> CloudStreamColors.Success
            }
            val background = if (isSelected) smileyColor.copy(alpha = 0.22f) else CloudStreamColors.SurfaceElevated
            val border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) smileyColor else MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = background,
                border = border,
                modifier = Modifier
                    .weight(1f)
                    .dpadFocusable(
                        onClick = {
                            onScoreChange(if (isSelected) null else Score.from(smiley.scoreValue, 3))
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
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

@Composable
fun SyncScoreSelector(
    selectedScale: TrackerScoreScale,
    currentScore: Score?,
    brandColor: Color,
    onScaleChange: (TrackerScoreScale) -> Unit,
    onScoreChange: (Score?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScoreHeaderBadge(
            currentScore = currentScore,
            selectedScale = selectedScale,
            onClearScore = { onScoreChange(null) }
        )

        ScoreScaleTabs(
            selectedScale = selectedScale,
            brandColor = brandColor,
            onSelectScale = onScaleChange
        )

        when (selectedScale) {
            TrackerScoreScale.Point10Decimal -> DecimalScoreEditor(
                currentScore = currentScore,
                brandColor = brandColor,
                onScoreChange = onScoreChange
            )
            TrackerScoreScale.Point100 -> HundredScoreEditor(
                currentScore = currentScore,
                brandColor = brandColor,
                onScoreChange = onScoreChange
            )
            TrackerScoreScale.Point5Star -> FiveStarScoreEditor(
                currentScore = currentScore,
                onScoreChange = onScoreChange
            )
            TrackerScoreScale.Point3Smiley -> SmileyScoreEditor(
                currentScore = currentScore,
                onScoreChange = onScoreChange
            )
        }
    }
}

@Composable
fun SyncProgressCounter(
    watchedEpisodes: Int,
    maxEpisodes: Int?,
    brandColor: Color,
    onWatchedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember(watchedEpisodes) { mutableStateOf(watchedEpisodes.toString()) }

    Column(
        modifier = modifier
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
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (watchedEpisodes > 0) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceElevated.copy(alpha = 0.4f),
                        CircleShape
                    )
                    .dpadFocusable(
                        onClick = {
                            if (watchedEpisodes > 0) {
                                val next = watchedEpisodes - 1
                                onWatchedChange(next)
                            }
                        },
                        enabled = watchedEpisodes > 0,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.baseline_skip_previous_24),
                    contentDescription = stringResource(Res.string.decrement),
                    tint = if (watchedEpisodes > 0) CloudStreamColors.TextPrimary else CloudStreamColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            CloudStreamTextField(
                value = textInput,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(5)
                    textInput = filtered
                    val parsed = filtered.toIntOrNull() ?: 0
                    val clamped = if (maxEpisodes != null && maxEpisodes > 0) parsed.coerceIn(0, maxEpisodes) else parsed.coerceAtLeast(0)
                    onWatchedChange(clamped)
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

            val canIncrement = maxEpisodes == null || watchedEpisodes < maxEpisodes
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (canIncrement) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceElevated.copy(alpha = 0.4f),
                        CircleShape
                    )
                    .dpadFocusable(
                        onClick = {
                            val next = watchedEpisodes + 1
                            val coerced = if (maxEpisodes != null && maxEpisodes > 0) next.coerceAtMost(maxEpisodes) else next
                            onWatchedChange(coerced)
                        },
                        enabled = canIncrement,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.increment),
                    tint = if (canIncrement) CloudStreamColors.TextPrimary else CloudStreamColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CloudStreamColors.SurfaceElevated,
                modifier = Modifier
                    .weight(1f)
                    .dpadFocusable(
                        onClick = { onWatchedChange(0) },
                        shape = RoundedCornerShape(6.dp)
                    )
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

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CloudStreamColors.SurfaceElevated,
                modifier = Modifier
                    .weight(1f)
                    .dpadFocusable(
                        onClick = {
                            val next = (watchedEpisodes + 1).let {
                                if (maxEpisodes != null && maxEpisodes > 0) it.coerceAtMost(maxEpisodes) else it
                            }
                            onWatchedChange(next)
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
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

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CloudStreamColors.SurfaceElevated,
                modifier = Modifier
                    .weight(1f)
                    .dpadFocusable(
                        onClick = {
                            val next = (watchedEpisodes + 5).let {
                                if (maxEpisodes != null && maxEpisodes > 0) it.coerceAtMost(maxEpisodes) else it
                            }
                            onWatchedChange(next)
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
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

            if (maxEpisodes != null && maxEpisodes > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = brandColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, brandColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1.3f)
                        .dpadFocusable(
                            onClick = { onWatchedChange(maxEpisodes) },
                            shape = RoundedCornerShape(6.dp)
                        )
                ) {
                    Text(
                        text = "${stringResource(Res.string.all)} ($maxEpisodes)",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = brandColor,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SyncAdvancedSection(
    customSyncId: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSyncIdChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.SurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .dpadFocusable(
                    onClick = onToggleExpand,
                    shape = RoundedCornerShape(8.dp)
                ),
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
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = CloudStreamColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CloudStreamTextField(
                    value = customSyncId,
                    onValueChange = onSyncIdChange,
                    placeholder = stringResource(Res.string.syncIdPlaceholder),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SyncDialogButtons(
    isLinked: Boolean,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLinked) {
            GhostButton(
                text = stringResource(Res.string.syncUnlinkButton),
                icon = Icons.Default.Delete,
                contentColor = CloudStreamColors.Error,
                onClick = onUnlink
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss
            )

            PrimaryButton(onClick = onSave) {
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
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SyncServiceTabs(
                    activeService = activeService,
                    syncStates = state.externalSyncStates,
                    onSelectService = {
                        activeService = it
                        onEvent(ResultEvent.SelectSyncService(it))
                    }
                )

                SyncStatusSelector(
                    selectedStatus = selectedStatus,
                    onSelectStatus = { status ->
                        selectedStatus = status
                        if (status == ExternalSyncStatus.Completed && maxEpisodes != null && maxEpisodes > 0) {
                            watchedEpisodes = maxEpisodes
                        }
                    }
                )

                SyncScoreSelector(
                    selectedScale = selectedScale,
                    currentScore = currentScore,
                    brandColor = activeService.brandColor,
                    onScaleChange = { selectedScale = it },
                    onScoreChange = { currentScore = it }
                )

                SyncProgressCounter(
                    watchedEpisodes = watchedEpisodes,
                    maxEpisodes = maxEpisodes,
                    brandColor = activeService.brandColor,
                    onWatchedChange = { newCount ->
                        watchedEpisodes = newCount
                        if (maxEpisodes != null && newCount >= maxEpisodes && maxEpisodes > 0) {
                            selectedStatus = ExternalSyncStatus.Completed
                        } else if (newCount > 0 && selectedStatus == ExternalSyncStatus.PlanToWatch) {
                            selectedStatus = ExternalSyncStatus.Watching
                        }
                    }
                )

                SyncAdvancedSection(
                    customSyncId = customSyncId,
                    isExpanded = isAdvancedExpanded,
                    onToggleExpand = { isAdvancedExpanded = !isAdvancedExpanded },
                    onSyncIdChange = { customSyncId = it }
                )
            }
        },
        buttons = {
            SyncDialogButtons(
                isLinked = currentEntry.hasTracking,
                onUnlink = {
                    onEvent(ResultEvent.UnlinkSyncService(activeService))
                    selectedStatus = ExternalSyncStatus.None
                    customSyncId = ""
                },
                onDismiss = onDismiss,
                onSave = {
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
            )
        }
    )
}

@Preview
@Composable
private fun SyncDialogPreview() {
    CloudStreamTheme {
        SyncDialog(
            state = ResultState(title = "Steins;Gate"),
            onEvent = {},
            onDismiss = {}
        )
    }
}

