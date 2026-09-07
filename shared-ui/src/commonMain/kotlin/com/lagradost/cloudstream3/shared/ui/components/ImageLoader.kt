package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import com.lagradost.cloudstream3.app
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

interface ImageCache {
    val size: Int
    fun get(url: String?): ImageBitmap?
    fun put(url: String?, bitmap: ImageBitmap)
    fun clear()
}

object ImageMemoryCache : SynchronizedObject(), ImageCache {
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

    override val size: Int
        get() = synchronized(this) { map.size }

    fun getSync(url: String?): ImageBitmap? = get(url)

    override fun get(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        return synchronized(this) {
            val node = map[url] ?: return@synchronized null
            moveToHead(node)
            node.bitmap
        }
    }

    override fun put(url: String?, bitmap: ImageBitmap) {
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

    override fun clear() {
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

interface ImageFetcher {
    suspend fun fetch(url: String, headers: Map<String, String>): ByteArray
}

class NetworkImageFetcher : ImageFetcher {
    override suspend fun fetch(url: String, headers: Map<String, String>): ByteArray {
        val response = app.get(url, headers = headers)
        return response.body.bytes()
    }
}

interface ImageDecoder {
    fun decode(bytes: ByteArray): ImageBitmap
}

@OptIn(ExperimentalResourceApi::class)
class DefaultImageDecoder : ImageDecoder {
    override fun decode(bytes: ByteArray): ImageBitmap {
        require(bytes.isNotEmpty()) { "Empty image bytes" }
        return bytes.decodeToImageBitmap()
    }
}

interface ImageLoader {
    fun getCached(url: String?): ImageBitmap?
    suspend fun load(url: String, headers: Map<String, String>? = null): Result<ImageBitmap>

    companion object {
        val Default: ImageLoader by lazy { DefaultImageLoader() }
    }
}

class DefaultImageLoader(
    private val cache: ImageCache = ImageMemoryCache,
    private val fetcher: ImageFetcher = NetworkImageFetcher(),
    private val decoder: ImageDecoder = DefaultImageDecoder(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ImageLoader {

    override fun getCached(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        return cache.get(url)
    }

    override suspend fun load(url: String, headers: Map<String, String>?): Result<ImageBitmap> {
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty URL"))
        }

        val cached = cache.get(url)
        if (cached != null) {
            return Result.success(cached)
        }

        return withContext(dispatcher) {
            val alreadyCached = cache.get(url)
            if (alreadyCached != null) {
                return@withContext Result.success(alreadyCached)
            }
            runCatching {
                val bytes = fetcher.fetch(url, headers ?: emptyMap())
                val bitmap = decoder.decode(bytes)
                cache.put(url, bitmap)
                bitmap
            }
        }
    }
}
