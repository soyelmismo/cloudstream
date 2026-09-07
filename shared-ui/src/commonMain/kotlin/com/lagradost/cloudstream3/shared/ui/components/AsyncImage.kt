package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Immutable
private sealed interface ImageLoadState {
    @Immutable
    data object Loading : ImageLoadState
    @Immutable
    data class Success(val bitmap: ImageBitmap) : ImageLoadState
    @Immutable
    data class Error(val message: String?) : ImageLoadState
}

@Composable
fun AsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    headers: Map<String, String>? = null,
    contentScale: ContentScale = ContentScale.Crop,
    imageLoader: ImageLoader = ImageLoader.Default,
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null
) {
    var loadState by remember(url, imageLoader) {
        mutableStateOf<ImageLoadState>(
            imageLoader.getCached(url)?.let { ImageLoadState.Success(it) }
                ?: if (url.isNullOrBlank()) ImageLoadState.Error("Empty URL")
                else ImageLoadState.Loading
        )
    }

    LaunchedEffect(url, headers, imageLoader) {
        if (url.isNullOrBlank()) {
            loadState = ImageLoadState.Error("Empty URL")
            return@LaunchedEffect
        }

        val cached = imageLoader.getCached(url)
        if (cached != null) {
            loadState = ImageLoadState.Success(cached)
            return@LaunchedEffect
        }

        if (loadState !is ImageLoadState.Loading) {
            loadState = ImageLoadState.Loading
        }

        val result = imageLoader.load(url, headers)
        loadState = result.fold(
            onSuccess = { ImageLoadState.Success(it) },
            onFailure = { ImageLoadState.Error(it.message ?: "Failed to load image") }
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (val state = loadState) {
            is ImageLoadState.Loading -> {
                if (placeholder != null) {
                    placeholder()
                } else {
                    DefaultImagePlaceholder()
                }
            }
            is ImageLoadState.Success -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ImageLoadState.Error -> {
                if (error != null) {
                    error()
                } else if (placeholder != null) {
                    placeholder()
                } else {
                    DefaultImageErrorPlaceholder()
                }
            }
        }
    }
}

@Composable
fun DefaultImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.SurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = CloudStreamColors.Primary.copy(alpha = 0.6f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun DefaultImageErrorPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.SurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            tint = CloudStreamColors.TextMuted,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Preview
@Composable
private fun AsyncImagePlaceholderPreview() {
    CloudStreamTheme {
        Box(modifier = Modifier.size(100.dp)) {
            DefaultImagePlaceholder()
        }
    }
}

@Preview
@Composable
private fun AsyncImageErrorPreview() {
    CloudStreamTheme {
        Box(modifier = Modifier.size(100.dp)) {
            DefaultImageErrorPlaceholder()
        }
    }
}
