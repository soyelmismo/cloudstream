package com.lagradost.cloudstream3.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Top Search Bar with quick clearing, debounced auto-search, search button,
 * keyboard controls (Enter to search, Escape to clear), and view display mode switcher.
 */
@Composable
fun SearchBarView(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String, isQuickSearch: Boolean) -> Unit,
    onClear: () -> Unit,
    displayMode: SearchDisplayMode,
    onDisplayModeChange: (SearchDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
    debounceMs: Long = 400L,
    isLoading: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var localText by remember(query) { mutableStateOf(query) }
    val effectivePlaceholder = placeholderText ?: stringResource(Res.string.search_placeholder)

    // Debounce typing logic for responsive auto-search
    LaunchedEffect(localText) {
        if (localText == query) return@LaunchedEffect

        if (localText.isBlank()) {
            onClear()
            return@LaunchedEffect
        }

        delay(debounceMs)
        onQueryChange(localText)
        onSearch(localText, true)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CloudStreamColors.SurfaceVariant,
        elevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = CloudStreamColors.Divider,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Leading Icon / Button
            IconButton(
                onClick = {
                    if (localText.isNotBlank()) {
                        onSearch(localText, false)
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search),
                    tint = if (localText.isNotBlank()) CloudStreamColors.Primary else CloudStreamColors.TextMuted
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Main Text Input with Desktop Keyboard Handling
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (localText.isEmpty()) {
                    Text(
                        text = effectivePlaceholder,
                        style = TextStyle(
                            color = CloudStreamColors.TextMuted,
                            fontSize = 15.sp
                        )
                    )
                }

                BasicTextField(
                    value = localText,
                    onValueChange = { newValue ->
                        localText = newValue
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(CloudStreamColors.Primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (localText.isNotBlank()) {
                                onSearch(localText, false)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                when (keyEvent.key) {
                                    Key.Enter, Key.NumPadEnter -> {
                                        if (localText.isNotBlank()) {
                                            onSearch(localText, false)
                                        }
                                        true
                                    }
                                    Key.Escape -> {
                                        localText = ""
                                        onClear()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                )
            }

            // Quick Clear Button
            if (localText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        localText = ""
                        onClear()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.clear_input),
                        tint = CloudStreamColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Loading indicator inside search bar
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(2.dp),
                    strokeWidth = 2.dp,
                    color = CloudStreamColors.Primary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Divider before mode switch
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(CloudStreamColors.Divider)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Display Mode Switcher (Unified Grid vs Grouped by Provider)
            DisplayModeToggle(
                currentMode = displayMode,
                onModeSelected = onDisplayModeChange
            )
        }
    }
}

/**
 * Toggle selector for switching between Unified (Interleaved) grid and Grouped by provider list.
 */
@Composable
private fun DisplayModeToggle(
    currentMode: SearchDisplayMode,
    onModeSelected: (SearchDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CloudStreamColors.SurfaceElevated)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Unified Mode Button
        ModeIconButton(
            isSelected = currentMode == SearchDisplayMode.Unified,
            onClick = { onModeSelected(SearchDisplayMode.Unified) },
            description = stringResource(Res.string.search_view_mode_unified)
        ) {
            // Grid icon represented as 4 small squares
            GridModeIcon(
                tint = if (currentMode == SearchDisplayMode.Unified) CloudStreamColors.Primary else CloudStreamColors.TextMuted
            )
        }

        // Grouped Mode Button
        ModeIconButton(
            isSelected = currentMode == SearchDisplayMode.Grouped,
            onClick = { onModeSelected(SearchDisplayMode.Grouped) },
            description = stringResource(Res.string.search_view_mode_grouped)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.search_view_mode_grouped),
                tint = if (currentMode == SearchDisplayMode.Grouped) CloudStreamColors.Primary else CloudStreamColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ModeIconButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) CloudStreamColors.SurfaceVariant else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun GridModeIcon(tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.size(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(5.dp).background(tint, RoundedCornerShape(1.dp)))
            Box(modifier = Modifier.size(5.dp).background(tint, RoundedCornerShape(1.dp)))
        }
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(5.dp).background(tint, RoundedCornerShape(1.dp)))
            Box(modifier = Modifier.size(5.dp).background(tint, RoundedCornerShape(1.dp)))
        }
    }
}

