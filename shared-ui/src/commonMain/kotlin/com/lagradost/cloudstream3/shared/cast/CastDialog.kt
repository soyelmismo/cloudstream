package com.lagradost.cloudstream3.shared.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.stringResource

/**
 * Modern Multiplatform Casting Device Selection Dialog.
 * Displays discovered Google Cast, DLNA / UPnP, and AirPlay/Local renderers
 * with active connection states, responsive D-Pad focus, and seamless connection triggers.
 */
@Composable
fun CastDialog(
    castManager: CastManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val castState by castManager.castState.collectAsState()
    val availableDevices by castManager.availableDevices.collectAsState()
    val currentDevice by castManager.currentDevice.collectAsState()
    val currentSession by castManager.currentSession.collectAsState()

    DisposableEffect(castManager) {
        castManager.startDiscovery()
        onDispose {
            castManager.stopDiscovery()
        }
    }

    ActionDialog(
        title = stringResource(Res.string.cast_to_device),
        onDismissRequest = onDismissRequest,
        showCloseButton = true,
        cancelTextRes = Res.string.sort_close,
        onCancel = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Connected Device Banner (if any)
            if (currentDevice != null && castState != CastState.DISCONNECTED) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = CloudStreamColors.Primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.4f)),
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CloudStreamColors.Primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CastConnected,
                                        contentDescription = null,
                                        tint = MaterialTheme.colors.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = currentDevice?.name ?: stringResource(Res.string.cast_connected),
                                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                    color = CloudStreamColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (castState) {
                                        CastState.CONNECTING -> stringResource(Res.string.cast_connecting)
                                        CastState.CASTING -> stringResource(Res.string.playing)
                                        CastState.PAUSED -> stringResource(Res.string.pause)
                                        else -> stringResource(Res.string.cast_connected)
                                    },
                                    style = MaterialTheme.typography.caption,
                                    color = CloudStreamColors.Primary
                                )
                            }
                        }

                        SecondaryButton(
                            textRes = Res.string.cast_disconnect,
                            onClick = { castManager.disconnect() }
                        )
                    }
                }
            }

            // Discovery Header & Scanning Spinner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.cast_device_count_format, availableDevices.size),
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = CloudStreamColors.TextSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        color = CloudStreamColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(Res.string.cast_searching_devices),
                        style = MaterialTheme.typography.caption,
                        color = CloudStreamColors.TextMuted
                    )
                }
            }

            // Discovered Devices List
            if (availableDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = CloudStreamColors.TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(Res.string.cast_no_devices_found),
                            style = MaterialTheme.typography.body2,
                            color = CloudStreamColors.TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = availableDevices,
                        key = { it.id }
                    ) { device ->
                        val isSelected = device.id == currentDevice?.id

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = if (isSelected) CloudStreamColors.Primary.copy(alpha = 0.12f) else CloudStreamColors.SurfaceVariant,
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) CloudStreamColors.Primary else CloudStreamColors.Divider.copy(alpha = 0.3f)
                            ),
                            elevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    onClick = {
                                        if (isSelected) {
                                            castManager.disconnect()
                                        } else {
                                            castManager.connect(device)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CloudStreamColors.SurfaceElevated,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                                            color = CloudStreamColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val protocolLabel = when (device.protocol) {
                                            CastProtocol.GOOGLE_CAST -> stringResource(Res.string.cast_google_cast)
                                            CastProtocol.UPNP_DLNA -> stringResource(Res.string.cast_dlna_upnp)
                                            else -> device.modelName ?: stringResource(Res.string.cast_dlna_upnp)
                                        }

                                        Text(
                                            text = protocolLabel,
                                            style = MaterialTheme.typography.caption,
                                            color = CloudStreamColors.TextMuted
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = CloudStreamColors.Primary,
                                        modifier = Modifier.size(10.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
