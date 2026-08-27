package com.lagradost.cloudstream3.desktop.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

/**
 * Pure Compose Desktop video surface that renders decoded VLC video frames directly via Skia.
 * Eliminates all heavyweight AWT Canvas subwindows, white flashes, and overlay clipping.
 */
@Composable
fun DesktopVideoSurface(
    player: DesktopVideoPlayer,
    modifier: Modifier = Modifier
) {
    val frame by player.videoFrameFlow.collectAsState()
    val ratioMode by player.aspectRatioFlow.collectAsState()
    val contentScale = when (ratioMode.lowercase()) {
        "zoom" -> ContentScale.Crop
        "stretch", "fill" -> ContentScale.FillBounds
        "original" -> ContentScale.Inside
        else -> ContentScale.Fit
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (frame != null) {
            Image(
                bitmap = frame!!,
                contentDescription = "Video Stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}

/**
 * Full Compose Desktop Video Player UI with interactive playback controls,
 * non-blocking loading state, buffering indicators, volume slider, seeking bar,
 * and state synchronization.
 */
@Composable
fun DesktopVideoPlayerView(
    player: DesktopVideoPlayer,
    modifier: Modifier = Modifier,
    title: String? = null,
    showControlsOverlay: Boolean = true,
    overlayContent: (@Composable () -> Unit)? = null
) {
    val state by player.stateFlow.collectAsState()
    val isReady by player.isReadyFlow.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableStateOf(0f) }
    var volume by remember { mutableStateOf(player.getVolume().toFloat()) }
    var isMuted by remember { mutableStateOf(player.isMuted()) }

    // Auto-hide controls after 4 seconds of inactivity when playing
    LaunchedEffect(controlsVisible, state.isPlaying, isSeeking) {
        if (controlsVisible && state.isPlaying && !isSeeking) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // Video Surface
        DesktopVideoSurface(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        // Loading overlay during async backend initialization
        if (!isReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colors.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cargando reproductor...",
                        style = MaterialTheme.typography.body2,
                        color = Color.LightGray
                    )
                }
            }
        }

        // Buffering Indicator during playback
        if (isReady && state.isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.primary,
                    strokeWidth = 4.dp
                )
            }
        }

        // Custom Overlay Content (Subtitles, custom badges, etc.)
        overlayContent?.invoke()

        // Controls Overlay
        if (showControlsOverlay && isReady) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // Top Bar (Title & Info)
                    if (!title.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.h6,
                                color = Color.White
                            )
                        }
                    }

                    // Bottom Bar (Progress Bar, Controls, Volume, Timestamps)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        // Seek Slider
                        val currentPos = if (isSeeking) seekPositionMs else state.positionMs.toFloat()
                        val duration = state.durationMs.toFloat().coerceAtLeast(1f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentPos.toLong()),
                                color = Color.White,
                                style = MaterialTheme.typography.caption
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Slider(
                                value = currentPos.coerceIn(0f, duration),
                                onValueChange = {
                                    isSeeking = true
                                    seekPositionMs = it
                                },
                                onValueChangeFinished = {
                                    player.seekTo(seekPositionMs.toLong())
                                    isSeeking = false
                                },
                                valueRange = 0f..duration,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colors.primary,
                                    activeTrackColor = MaterialTheme.colors.primary,
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = formatDuration(state.durationMs),
                                color = Color.White,
                                style = MaterialTheme.typography.caption
                            )
                        }

                        // Playback Action Buttons and Volume
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left section: Volume Control
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val newMute = !isMuted
                                        isMuted = newMute
                                        player.setMute(newMute)
                                    }
                                ) {
                                    Text(
                                        text = if (isMuted || volume == 0f) "🔇" else "🔊",
                                        color = Color.White,
                                        style = MaterialTheme.typography.body1
                                    )
                                }

                                Slider(
                                    value = if (isMuted) 0f else volume,
                                    onValueChange = {
                                        volume = it
                                        isMuted = false
                                        player.setMute(false)
                                        player.setVolume(it.toInt())
                                    },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.width(100.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colors.primary,
                                        activeTrackColor = MaterialTheme.colors.primary,
                                        inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                                    )
                                )
                            }

                            // Center section: Play / Pause / Stop
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Play / Pause Toggle Button
                                IconButton(
                                    onClick = {
                                        if (state.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.resume()
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Text(
                                        text = if (state.isPlaying) "⏸" else "▶",
                                        color = Color.White,
                                        style = MaterialTheme.typography.h5
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Stop Button
                                IconButton(
                                    onClick = {
                                        player.stop()
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Text(
                                        text = "⏹",
                                        color = Color.White,
                                        style = MaterialTheme.typography.h5
                                    )
                                }
                            }

                            // Right spacer for visual balance
                            Spacer(modifier = Modifier.width(120.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats milliseconds into standard HH:MM:SS or MM:SS format.
 */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
