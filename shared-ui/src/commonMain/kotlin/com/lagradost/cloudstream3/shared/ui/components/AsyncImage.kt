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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * High-performance in-memory LRU bitmap cache with O(1) lookups and mutations.
 * Fully thread-safe and supports synchronous lookups for zero-flicker frame-0 rendering.
 */
object ImageMemoryCache : SynchronizedObject() {
    private const val MAX_ENTRIES = 300

    private class CacheNode(
        val key: String,
        var bitmap: ImageBitmap
    ) {
        var prev: CacheNode? = null
        var next: CacheNode? = null
    }

    private val map = HashMap<String, CacheNode>(MAX_ENTRIES)
    private var head: CacheNode? = null
    private var tail: CacheNode? = null

    val size: Int
        get() = synchronized(this) { map.size }

    /**
     * Synchronously retrieves a cached [ImageBitmap] by [url] in O(1) time.
     * Moves the accessed entry to the head of the LRU queue.
     */
    fun getSync(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        return synchronized(this) {
            val node = map[url] ?: return@synchronized null
            moveToHead(node)
            node.bitmap
        }
    }

    /**
     * Caches the [bitmap] for [url] in O(1) time, evicting the least-recently used entry
     * if capacity exceeds [MAX_ENTRIES].
     */
    fun put(url: String?, bitmap: ImageBitmap) {
        if (url.isNullOrBlank()) return
        synchronized(this) {
            val existing = map[url]
            if (existing != null) {
                existing.bitmap = bitmap
                moveToHead(existing)
                return@synchronized
            }

            if (map.size >= MAX_ENTRIES) {
                removeTail()
            }

            val newNode = CacheNode(url, bitmap)
            map[url] = newNode
            addToHead(newNode)
        }
    }

    /**
     * Clears all cached entries.
     */
    fun clear() {
        synchronized(this) {
            map.clear()
            head = null
            tail = null
        }
    }

    private fun addToHead(node: CacheNode) {
        node.prev = null
        node.next = head
        head?.prev = node
        head = node
        if (tail == null) {
            tail = node
        }
    }

    private fun removeNode(node: CacheNode) {
        val prev = node.prev
        val next = node.next

        if (prev != null) {
            prev.next = next
        } else {
            head = next
        }

        if (next != null) {
            next.prev = prev
        } else {
            tail = prev
        }

        node.prev = null
        node.next = null
    }

    private fun moveToHead(node: CacheNode) {
        if (head === node) return
        removeNode(node)
        addToHead(node)
    }

    private fun removeTail() {
        val t = tail ?: return
        map.remove(t.key)
        removeNode(t)
    }
}

private sealed interface ImageLoadState {
    object Loading : ImageLoadState
    data class Success(val bitmap: ImageBitmap) : ImageLoadState
    data class Error(val message: String?) : ImageLoadState
}

/**
 * Pure Compose Multiplatform asynchronous image loader.
 * Fetches images via CloudStream network client with headers support,
 * caches decoded bitmaps in memory, and displays customizable placeholders.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun AsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    headers: Map<String, String>? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null
) {
    var loadState by remember(url) {
        mutableStateOf<ImageLoadState>(
            ImageMemoryCache.getSync(url)?.let { ImageLoadState.Success(it) }
                ?: if (url.isNullOrBlank()) ImageLoadState.Error("Empty URL")
                else ImageLoadState.Loading
        )
    }

    LaunchedEffect(url, headers) {
        if (url.isNullOrBlank()) {
            loadState = ImageLoadState.Error("Empty URL")
            return@LaunchedEffect
        }

        // Fast path: Check cache first without jumping across dispatchers
        val cached = ImageMemoryCache.getSync(url)
        if (cached != null) {
            loadState = ImageLoadState.Success(cached)
            return@LaunchedEffect
        }

        if (loadState !is ImageLoadState.Loading) {
            loadState = ImageLoadState.Loading
        }

        withContext(Dispatchers.IO) {
            try {
                val response = app.get(url, headers = headers ?: emptyMap())
                val bytes = response.body.bytes()
                if (bytes.isEmpty()) {
                    loadState = ImageLoadState.Error("Empty image bytes")
                    return@withContext
                }
                val bitmap = bytes.decodeToImageBitmap()
                ImageMemoryCache.put(url, bitmap)
                loadState = ImageLoadState.Success(bitmap)
            } catch (t: Throwable) {
                loadState = ImageLoadState.Error(t.message ?: "Failed to load image")
            }
        }
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
