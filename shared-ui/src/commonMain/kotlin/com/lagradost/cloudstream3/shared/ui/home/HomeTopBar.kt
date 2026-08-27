package com.lagradost.cloudstream3.shared.ui.home

import com.lagradost.cloudstream3.utils.asString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.designsystem.GhostButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.HomeEvent
import com.lagradost.cloudstream3.shared.viewmodels.HomeState
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Top navigation bar for the Home screen featuring:
 * - Active provider dropdown selector with language tags.
 * - Refresh button with spinning animation during data sync.
 * - Non-intrusive connection error / warning banner with retry action.
 *
 * @param state The current [HomeState].
 * @param onEvent Callback to dispatch [HomeEvent] intents to [HomeViewModel].
 * @param modifier Optional modifier.
 * @param onSearchClick Optional callback when search icon is clicked.
 */
@Composable
fun HomeTopBar(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null
) {
    var isProviderDropdownOpen by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.Background.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Section: App Logo / Title & Provider Dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selected Provider Logo / Initial Avatar
                val selectedProvider = state.selectedProvider
                val providerIconUrl = selectedProvider?.iconUrl
                val providerInitial = selectedProvider?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "CS"

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CloudStreamColors.Primary,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (!providerIconUrl.isNullOrBlank()) {
                        AsyncImage(
                            url = providerIconUrl,
                            contentDescription = selectedProvider.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            placeholder = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(CloudStreamColors.Primary)
                                ) {
                                    Text(
                                        text = providerInitial,
                                        color = CloudStreamColors.OnMediaScrim,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            },
                            error = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(CloudStreamColors.Primary)
                                ) {
                                    Text(
                                        text = providerInitial,
                                        color = CloudStreamColors.OnMediaScrim,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = providerInitial,
                                color = CloudStreamColors.OnMediaScrim,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Provider Selector Button with Native DropdownMenu
                Box {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CloudStreamColors.SurfaceVariant,
                        modifier = Modifier
                            .dpadFocusable(
                                onClick = { isProviderDropdownOpen = true },
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val providerName = state.selectedProvider?.name ?: stringResource(Res.string.selectProvider)
                            Text(
                                text = providerName,
                                color = CloudStreamColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(140.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(Res.string.selectProvider),
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isProviderDropdownOpen,
                        onDismissRequest = { isProviderDropdownOpen = false },
                        modifier = Modifier
                            .widthIn(min = 260.dp, max = 360.dp)
                            .background(CloudStreamColors.SurfaceElevated)
                    ) {
                        // Header and Feeling Lucky Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.selectProvider),
                                color = CloudStreamColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            GhostButton(
                                textRes = Res.string.feeling_lucky,
                                icon = Icons.Default.Refresh,
                                contentColor = CloudStreamColors.Primary,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                onClick = {
                                    isProviderDropdownOpen = false
                                    if (state.availableProviders.isNotEmpty()) {
                                        val random = state.availableProviders.random()
                                        onEvent(HomeEvent.SelectProvider(random))
                                    }
                                }
                            )
                        }

                        Divider(
                            color = CloudStreamColors.Divider,
                            thickness = 1.dp
                        )

                        // Scrollable List of Providers
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (state.availableProviders.isEmpty()) {
                                DropdownMenuItem(
                                    onClick = { isProviderDropdownOpen = false }
                                ) {
                                    Text(
                                        text = stringResource(Res.string.selectProviderPrompt),
                                        color = CloudStreamColors.TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                state.availableProviders.forEach { provider ->
                                    val isSelected = provider.name == state.selectedProvider?.name
                                    DropdownMenuItem(
                                        onClick = {
                                            isProviderDropdownOpen = false
                                            if (!isSelected) {
                                                onEvent(HomeEvent.SelectProvider(provider))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Text(
                                                    text = provider.name,
                                                    color = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (provider.lang.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(CloudStreamColors.SurfaceVariant)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = provider.lang.uppercase(),
                                                            color = CloudStreamColors.TextMuted,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = CloudStreamColors.Primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
             }

             // Right Section: Refresh & Search Actions
             Row(
                 verticalAlignment = Alignment.CenterVertically,
                 horizontalArrangement = Arrangement.spacedBy(4.dp)
             ) {
                 // Refresh Button
                 IconButton(
                     onClick = {
                         if (!state.isRefreshing && !state.isLoading) {
                             onEvent(HomeEvent.RefreshHome)
                         }
                     },
                     modifier = Modifier.size(38.dp)
                 ) {
                     if (state.isRefreshing) {
                         Icon(
                             imageVector = Icons.Default.Refresh,
                             contentDescription = stringResource(Res.string.loading),
                             tint = CloudStreamColors.Primary,
                             modifier = Modifier
                                 .size(22.dp)
                                 .rotate(rotation)
                         )
                     } else {
                         Icon(
                             imageVector = Icons.Default.Refresh,
                             contentDescription = stringResource(Res.string.refresh),
                             tint = CloudStreamColors.TextSecondary,
                             modifier = Modifier.size(22.dp)
                         )
                     }
                 }
             }
         }

         // Connection Error / Warning Notification Strip
         AnimatedVisibility(
             visible = state.error != null,
             enter = fadeIn(),
             exit = fadeOut()
         ) {
             Surface(
                 color = CloudStreamColors.Error.copy(alpha = 0.15f),
                 modifier = Modifier.fillMaxWidth()
             ) {
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(horizontal = 16.dp, vertical = 8.dp),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Row(
                         modifier = Modifier.weight(1f),
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.spacedBy(8.dp)
                     ) {
                         Icon(
                             imageVector = Icons.Default.Warning,
                             contentDescription = stringResource(Res.string.error),
                             tint = CloudStreamColors.Error,
                             modifier = Modifier.size(18.dp)
                         )
                          Text(
                              text = state.error?.asString() ?: stringResource(Res.string.noHomeContentDesc),
                             color = CloudStreamColors.Error,
                             fontSize = 12.sp,
                             maxLines = 2,
                             overflow = TextOverflow.Ellipsis
                         )
                     }

                     Row(
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.spacedBy(4.dp)
                     ) {
                         // Retry
                         Text(
                             text = stringResource(Res.string.retry),
                             color = CloudStreamColors.Primary,
                             fontSize = 12.sp,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier
                                 .clip(RoundedCornerShape(4.dp))
                                 .clickable { onEvent(HomeEvent.RefreshHome) }
                                 .padding(horizontal = 8.dp, vertical = 4.dp)
                         )

                         // Dismiss
                         IconButton(
                             onClick = { onEvent(HomeEvent.DismissError) },
                             modifier = Modifier.size(24.dp)
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Close,
                                 contentDescription = stringResource(Res.string.close),
                                 tint = CloudStreamColors.TextMuted,
                                 modifier = Modifier.size(16.dp)
                             )
                         }
                     }
                 }
             }
         }
     }
 }
