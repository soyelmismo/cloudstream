package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.player.LockPinDialogMode
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerActiveModal
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerAspectRatio
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerControllerViewModel
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full interactive controls overlay layer positioned on top of the [VideoPlayer] surface.
 * Connects directly to [PlayerControllerViewModel] in MVI architecture.
 */
@Composable
fun PlayerControlsOverlay(
    viewModel: PlayerControllerViewModel,
    onBackClick: () -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    PlayerControlsOverlay(
        state = state,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        onToggleFullscreen = onToggleFullscreen,
        modifier = modifier
    )
}

/**
 * Stateless Player Controls Overlay composable for previewing, gesture support, and testability.
 */
@Composable
fun PlayerControlsOverlay(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var userInteractionCount by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    // Gesture HUD States
    var brightnessLevel by remember { mutableFloatStateOf(0.75f) }
    var volumeLevel by remember { mutableFloatStateOf(0.80f) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingScrub by remember { mutableStateOf(false) }
    var scrubTargetPosMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var isSpeedBoostActive by remember { mutableStateOf(false) }
    var doubleTapSeekFeedback by remember { mutableStateOf<Long?>(null) }
    var isFirstAspectRatioEmission by remember { mutableStateOf(true) }
    var showAspectRatioIndicator by remember { mutableStateOf(false) }

    // Auto-hide controls after 4 seconds of inactivity during active playback
    LaunchedEffect(state.areControlsVisible, state.isPlaying, state.isBuffering, userInteractionCount, state.isControlsLocked, state.activeModal) {
        if (state.areControlsVisible && state.isPlaying && !state.isBuffering && !state.isControlsLocked && state.activeModal == null) {
            delay(4000)
            onEvent(PlayerUiEvent.VisibilityChanged(false))
        }
    }

    // Auto-hide HUD indicators after gesture ends
    LaunchedEffect(isDraggingBrightness) {
        if (!isDraggingBrightness) {
            delay(1200)
        }
    }
    LaunchedEffect(isDraggingVolume) {
        if (!isDraggingVolume) {
            delay(1200)
        }
    }
    LaunchedEffect(doubleTapSeekFeedback) {
        if (doubleTapSeekFeedback != null) {
            delay(800)
            doubleTapSeekFeedback = null
        }
    }
    // Auto-hide Aspect Ratio HUD indicator after 1.5 seconds
    LaunchedEffect(state.aspectRatio) {
        if (isFirstAspectRatioEmission) {
            isFirstAspectRatioEmission = false
            return@LaunchedEffect
        }
        showAspectRatioIndicator = true
        delay(1500)
        showAspectRatioIndicator = false
    }

    // Request keyboard focus when overlay is displayed
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    CloudStreamTheme {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        userInteractionCount++
                        if (!state.areControlsVisible && !state.isControlsLocked) {
                            onEvent(PlayerUiEvent.VisibilityChanged(true))
                        }

                        when (keyEvent.key) {
                            Key.Spacebar, Key.MediaPlayPause, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onEvent(PlayerUiEvent.TogglePlayPause)
                                true
                            }
                            Key.MediaPlay -> {
                                if (!state.isPlaying) onEvent(PlayerUiEvent.TogglePlayPause)
                                true
                            }
                            Key.MediaPause -> {
                                if (state.isPlaying) onEvent(PlayerUiEvent.TogglePlayPause)
                                true
                            }
                            Key.MediaFastForward -> {
                                onEvent(PlayerUiEvent.SeekBy(10_000L))
                                true
                            }
                            Key.MediaRewind -> {
                                onEvent(PlayerUiEvent.SeekBy(-10_000L))
                                true
                            }
                            Key.DirectionLeft -> {
                                onEvent(PlayerUiEvent.SeekBy(-10_000L))
                                true
                            }
                            Key.DirectionRight -> {
                                onEvent(PlayerUiEvent.SeekBy(10_000L))
                                true
                            }
                            Key.DirectionUp -> {
                                onEvent(PlayerUiEvent.SeekBy(60_000L))
                                true
                            }
                            Key.DirectionDown -> {
                                onEvent(PlayerUiEvent.SeekBy(-60_000L))
                                true
                            }
                            Key.S -> {
                                if (state.activeSkipTimestamp != null) {
                                    if (state.activeSkipTimestamp.isIntro) {
                                        onEvent(PlayerUiEvent.SkipIntro)
                                    } else {
                                        onEvent(PlayerUiEvent.SkipOutro)
                                    }
                                }
                                true
                            }
                            Key.L -> {
                                onEvent(PlayerUiEvent.ToggleControlsLock())
                                true
                            }
                            Key.F, Key.F11 -> {
                                if (onToggleFullscreen != null) {
                                    onToggleFullscreen()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.Back, Key.Escape -> {
                                if (state.areControlsVisible) {
                                    onEvent(PlayerUiEvent.VisibilityChanged(false))
                                } else {
                                    onBackClick()
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Move) {
                                userInteractionCount++
                                if (!state.areControlsVisible && !state.isControlsLocked) {
                                    onEvent(PlayerUiEvent.VisibilityChanged(true))
                                }
                            }
                        }
                    }
                }
                .pointerInput(state.isControlsLocked, state.positionMs, state.durationMs) {
                    if (state.isControlsLocked) {
                        return@pointerInput
                    }
                    var dragStartX = 0f
                    var dragStartY = 0f
                    var isHorizontalScrub = false
                    var isVerticalLeft = false
                    var isVerticalRight = false
                    var initialDragPosition = 0L

                    detectDragGestures(
                        onDragStart = { offset ->
                            userInteractionCount++
                            dragStartX = offset.x
                            dragStartY = offset.y
                            initialDragPosition = state.positionMs
                            isHorizontalScrub = false
                            isVerticalLeft = false
                            isVerticalRight = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            userInteractionCount++
                            val currentX = change.position.x
                            val totalDeltaX = currentX - dragStartX
                            val totalDeltaY = change.position.y - dragStartY

                            if (!isHorizontalScrub && !isVerticalLeft && !isVerticalRight) {
                                if (abs(totalDeltaX) > abs(totalDeltaY) && abs(totalDeltaX) > 20f) {
                                    isHorizontalScrub = true
                                    isDraggingScrub = true
                                } else if (abs(totalDeltaY) > 20f) {
                                    if (dragStartX < size.width * 0.45f) {
                                        isVerticalLeft = true
                                        isDraggingBrightness = true
                                    } else if (dragStartX > size.width * 0.55f) {
                                        isVerticalRight = true
                                        isDraggingVolume = true
                                    }
                                }
                            }

                            if (isHorizontalScrub && state.durationMs > 0L) {
                                val seekRatio = totalDeltaX / size.width.toFloat()
                                val maxSeekMs = (state.durationMs * 0.25f).coerceAtLeast(60_000f).toLong()
                                val deltaMs = (seekRatio * maxSeekMs).toLong()
                                scrubDeltaMs = deltaMs
                                scrubTargetPosMs = (initialDragPosition + deltaMs).coerceIn(0L, state.durationMs)
                            } else if (isVerticalLeft) {
                                val changeRatio = -dragAmount.y / (size.height * 0.75f)
                                brightnessLevel = (brightnessLevel + changeRatio).coerceIn(0.05f, 1.0f)
                            } else if (isVerticalRight) {
                                val changeRatio = -dragAmount.y / (size.height * 0.75f)
                                volumeLevel = (volumeLevel + changeRatio).coerceIn(0.0f, 1.0f)
                            }
                        },
                        onDragEnd = {
                            if (isHorizontalScrub) {
                                onEvent(PlayerUiEvent.SeekTo(scrubTargetPosMs))
                                isDraggingScrub = false
                            }
                            isDraggingBrightness = false
                            isDraggingVolume = false
                        },
                        onDragCancel = {
                            isDraggingScrub = false
                            isDraggingBrightness = false
                            isDraggingVolume = false
                        }
                    )
                }
                .pointerInput(state.isControlsLocked, onToggleFullscreen) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (!state.isControlsLocked) {
                                userInteractionCount++
                                if (onToggleFullscreen != null) {
                                    onToggleFullscreen()
                                } else {
                                    val width = size.width
                                    if (offset.x < width * 0.38f) {
                                        onEvent(PlayerUiEvent.SeekBy(-10_000L))
                                        doubleTapSeekFeedback = -10L
                                    } else if (offset.x > width * 0.62f) {
                                        onEvent(PlayerUiEvent.SeekBy(10_000L))
                                        doubleTapSeekFeedback = 10L
                                    } else {
                                        onEvent(PlayerUiEvent.TogglePlayPause)
                                    }
                                }
                            }
                        },
                        onLongPress = {
                            if (!state.isControlsLocked) {
                                isSpeedBoostActive = true
                                onEvent(PlayerUiEvent.SetSpeed(2.0f))
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (isSpeedBoostActive) {
                                isSpeedBoostActive = false
                                onEvent(PlayerUiEvent.SetSpeed(1.0f))
                            }
                        },
                        onTap = {
                            if (!state.isControlsLocked) {
                                onEvent(PlayerUiEvent.ToggleControlsVisibility)
                            }
                        }
                    )
                }
        ) {
            if (state.isControlsLocked) {
                // Screen Lock Accidental Touches Interceptor Overlay
                PlayerLockOverlay(
                    state = state,
                    onEvent = onEvent
                )
            } else {
                // Main Controls Layer (Fade In / Fade Out)
                AnimatedVisibility(
                    visible = state.areControlsVisible,
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
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    ) {
                        // Top Bar (Back, Title, Qualities, Subtitles, Audio, Speed, Lock)
                        PlayerTopBar(
                            state = state,
                            onEvent = onEvent,
                            onBackClick = onBackClick,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        // Center Controls (Play/Pause, Rewind 10s, Forward 10s)
                        PlayerCenterControls(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        // Bottom Bar (SeekBar, Timestamps, Next/Previous, Fullscreen)
                        PlayerBottomBar(
                            state = state,
                            onEvent = onEvent,
                            onToggleFullscreen = onToggleFullscreen,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }

                // Buffering Spinner (Always visible when buffering even if controls are hidden)
                if (!state.areControlsVisible && state.isBuffering) {
                    PlayerCenterControls(
                        state = state,
                        onEvent = onEvent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Floating Skip Intro / Outro / Next Episode Button (Bottom Right)
                PlayerSkipButton(
                    activeSkipTimestamp = state.activeSkipTimestamp,
                    hasNextEpisode = state.hasNextEpisode,
                    onEvent = onEvent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(end = 24.dp, bottom = if (state.areControlsVisible) 110.dp else 32.dp)
                )

                // -------------------------------------------------------------
                // Gesture HUD Overlays (Brightness, Volume, Scrub, Speed Boost)
                // -------------------------------------------------------------

                // Brightness HUD
                AnimatedVisibility(
                    visible = isDraggingBrightness,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(start = 36.dp)
                ) {
                    val brightPct = (brightnessLevel * 100).roundToInt()
                    val brightIcon = when {
                        brightnessLevel > 0.66f -> Res.drawable.ic_baseline_brightness_7_24
                        brightnessLevel > 0.33f -> Res.drawable.ic_baseline_brightness_5_24
                        else -> Res.drawable.ic_baseline_brightness_2_24
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, CloudStreamColors.Divider),
                        elevation = 12.dp,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                        ) {
                            Icon(
                                painter = painterResource(brightIcon),
                                contentDescription = stringResource(Res.string.gesture_brightness),
                                tint = CloudStreamColors.Warning,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "$brightPct%",
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(110.dp)
                                    .background(CloudStreamColors.SurfaceElevated, RoundedCornerShape(4.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction = brightnessLevel)
                                        .align(Alignment.BottomCenter)
                                        .background(CloudStreamColors.Warning, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                // Volume HUD
                AnimatedVisibility(
                    visible = isDraggingVolume,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(end = 36.dp)
                ) {
                    val volPct = (volumeLevel * 100).roundToInt()
                    val volIcon = when {
                        volumeLevel <= 0.01f -> Res.drawable.ic_baseline_volume_mute_24
                        volumeLevel < 0.5f -> Res.drawable.ic_baseline_volume_down_24
                        else -> Res.drawable.ic_baseline_volume_up_24
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, CloudStreamColors.Divider),
                        elevation = 12.dp,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                        ) {
                            Icon(
                                painter = painterResource(volIcon),
                                contentDescription = stringResource(Res.string.gesture_volume),
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "$volPct%",
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(110.dp)
                                    .background(CloudStreamColors.SurfaceElevated, RoundedCornerShape(4.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction = volumeLevel)
                                        .align(Alignment.BottomCenter)
                                        .background(CloudStreamColors.Primary, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                // Scrub HUD
                AnimatedVisibility(
                    visible = isDraggingScrub,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    val deltaSeconds = (scrubDeltaMs / 1000L).toInt()
                    val deltaPrefix = if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s"

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.92f),
                        border = BorderStroke(1.5.dp, CloudStreamColors.Primary.copy(alpha = 0.8f)),
                        elevation = 16.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = deltaPrefix,
                                style = MaterialTheme.typography.h6.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (deltaSeconds >= 0) CloudStreamColors.Success else CloudStreamColors.Warning,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "${PlayerUiState.formatTime(scrubTargetPosMs)} / ${state.formattedDuration}",
                                style = MaterialTheme.typography.body2.copy(
                                    color = CloudStreamColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                // Double Tap Seek Feedback Pulse
                AnimatedVisibility(
                    visible = doubleTapSeekFeedback != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier
                        .align(
                            if (doubleTapSeekFeedback != null && doubleTapSeekFeedback!! < 0) Alignment.CenterStart else Alignment.CenterEnd
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 48.dp)
                ) {
                    val isRewind = doubleTapSeekFeedback != null && doubleTapSeekFeedback!! < 0
                    Surface(
                        shape = CircleShape,
                        color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, CloudStreamColors.Divider),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(
                                        if (isRewind) Res.drawable.netflix_skip_back else Res.drawable.netflix_skip_forward
                                    ),
                                    contentDescription = stringResource(
                                        if (isRewind) Res.string.action_rewind_10 else Res.string.action_forward_10
                                    ),
                                    tint = CloudStreamColors.Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (isRewind) "-10s" else "+10s",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CloudStreamColors.TextPrimary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Speed Boost HUD
                AnimatedVisibility(
                    visible = isSpeedBoostActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CloudStreamColors.Primary.copy(alpha = 0.9f),
                        elevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_speed_24),
                                contentDescription = stringResource(Res.string.speed),
                                tint = CloudStreamColors.OnMediaScrim,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(Res.string.gesture_speed_2x),
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.OnMediaScrim,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                // Aspect Ratio Floating HUD
                AnimatedVisibility(
                    visible = showAspectRatioIndicator,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(top = 72.dp)
                ) {
                    val ratioLabel = when (state.aspectRatio) {
                        PlayerAspectRatio.Fit -> stringResource(Res.string.aspect_ratio_fit)
                        PlayerAspectRatio.Zoom -> stringResource(Res.string.aspect_ratio_zoom)
                        PlayerAspectRatio.Stretch -> stringResource(Res.string.aspect_ratio_stretch)
                        PlayerAspectRatio.Original -> stringResource(Res.string.aspect_ratio_original)
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, CloudStreamColors.Divider),
                        elevation = 12.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_aspect_ratio_24),
                                contentDescription = stringResource(Res.string.aspect_ratio),
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = ratioLabel,
                                style = MaterialTheme.typography.body2.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // Active Modal Dialogs Layer (Quality, Subtitles, Audio, Speed, Episodes, Settings)
            // -------------------------------------------------------------
            if (state.activeModal != null) {
                PlayerActiveModals(
                    state = state,
                    onEvent = onEvent,
                    onDismiss = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
                )
            }

            // Screen Lock PIN Dialog
            if (state.showLockPinDialog) {
                PlayerLockPinDialog(
                    mode = state.lockPinDialogMode,
                    currentPin = state.lockPin,
                    onPinConfirmed = { pin ->
                        if (state.lockPinDialogMode == LockPinDialogMode.Unlock) {
                            onEvent(PlayerUiEvent.UnlockWithPin(pin))
                        } else {
                            onEvent(PlayerUiEvent.SetLockPin(pin.ifBlank { null }))
                            onEvent(PlayerUiEvent.ToggleControlsLock(true))
                        }
                    },
                    onClearPin = {
                        onEvent(PlayerUiEvent.ClearLockPin)
                    },
                    onDismiss = {
                        onEvent(PlayerUiEvent.ShowLockPinDialog(false))
                    }
                )
            }
        }
    }
}
