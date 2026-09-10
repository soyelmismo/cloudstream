package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces

private fun createFakeBitmap(): ImageBitmap = object : ImageBitmap {
    override val width: Int = 10
    override val height: Int = 10
    override val hasAlpha: Boolean = true
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int
    ) {}
    override fun prepareToDraw() {}
}

private class FakeImageFetcher(
    private val responses: Map<String, ByteArray> = emptyMap(),
    private val shouldFail: Boolean = false
) : ImageFetcher {
    val fetchedUrls = mutableListOf<String>()
    val receivedHeaders = mutableListOf<Map<String, String>>()

    override suspend fun fetch(url: String, headers: Map<String, String>): ByteArray {
        fetchedUrls.add(url)
        receivedHeaders.add(headers)
        if (shouldFail) throw IllegalStateException("Network error fetching $url")
        return responses[url] ?: ByteArray(0)
    }
}

private class FakeImageDecoder(
    private val shouldFail: Boolean = false
) : ImageDecoder {
    var decodeCalls = 0

    override fun decode(bytes: ByteArray): ImageBitmap {
        if (shouldFail || bytes.isEmpty()) throw IllegalStateException("Decoder error")
        decodeCalls++
        return createFakeBitmap()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImageLoaderTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        ImageMemoryCache.clear()
    }

    @AfterTest
    fun tearDown() {
        ImageMemoryCache.clear()
    }

    @Test
    fun testMemoryCacheOperations() {
        val bitmap1 = createFakeBitmap()
        val bitmap2 = createFakeBitmap()

        assertNull(ImageMemoryCache.get("https://example.com/1.png"))
        assertEquals(0, ImageMemoryCache.size)

        ImageMemoryCache.put("https://example.com/1.png", bitmap1)
        assertEquals(1, ImageMemoryCache.size)
        assertEquals(bitmap1, ImageMemoryCache.get("https://example.com/1.png"))

        ImageMemoryCache.put("https://example.com/2.png", bitmap2)
        assertEquals(2, ImageMemoryCache.size)

        val updatedBitmap1 = createFakeBitmap()
        ImageMemoryCache.put("https://example.com/1.png", updatedBitmap1)
        assertEquals(2, ImageMemoryCache.size)
        assertEquals(updatedBitmap1, ImageMemoryCache.get("https://example.com/1.png"))

        ImageMemoryCache.clear()
        assertEquals(0, ImageMemoryCache.size)
        assertNull(ImageMemoryCache.get("https://example.com/1.png"))
    }

    @Test
    fun testEmptyUrlFailsFast() = runTest(testDispatcher) {
        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = FakeImageFetcher(),
            decoder = FakeImageDecoder(),
            dispatcher = testDispatcher
        )

        val resultBlank = loader.load("   ")
        assertTrue(resultBlank.isFailure)

        val resultEmpty = loader.load("")
        assertTrue(resultEmpty.isFailure)
    }

    @Test
    fun testCacheHitBypassesFetcherAndDecoder() = runTest(testDispatcher) {
        val url = "https://example.com/cached.png"
        val cachedBitmap = createFakeBitmap()
        ImageMemoryCache.put(url, cachedBitmap)

        val fetcher = FakeImageFetcher()
        val decoder = FakeImageDecoder()
        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = fetcher,
            decoder = decoder,
            dispatcher = testDispatcher
        )

        assertEquals(cachedBitmap, loader.getCached(url))

        val result = loader.load(url)
        assertTrue(result.isSuccess)
        assertEquals(cachedBitmap, result.getOrNull())
        assertEquals(0, fetcher.fetchedUrls.size)
        assertEquals(0, decoder.decodeCalls)
    }

    @Test
    fun testSuccessfulPipelineFetchDecodeAndCache() = runTest(testDispatcher) {
        val url = "https://example.com/network.png"
        val fakeBytes = byteArrayOf(1, 2, 3, 4)
        val fetcher = FakeImageFetcher(mapOf(url to fakeBytes))
        val decoder = FakeImageDecoder()
        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = fetcher,
            decoder = decoder,
            dispatcher = testDispatcher
        )

        assertNull(loader.getCached(url))

        val result = loader.load(url)
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        assertEquals(1, fetcher.fetchedUrls.size)
        assertEquals(1, decoder.decodeCalls)

        assertNotNull(ImageMemoryCache.get(url))
        assertNotNull(loader.getCached(url))
    }

    @Test
    fun testNetworkFailurePropagates() = runTest(testDispatcher) {
        val url = "https://example.com/fail.png"
        val fetcher = FakeImageFetcher(shouldFail = true)
        val decoder = FakeImageDecoder()
        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = fetcher,
            decoder = decoder,
            dispatcher = testDispatcher
        )

        val result = loader.load(url)
        assertTrue(result.isFailure)
        assertNull(ImageMemoryCache.get(url))
    }

    @Test
    fun testDecoderFailurePropagates() = runTest(testDispatcher) {
        val url = "https://example.com/invalid_bytes.png"
        val fetcher = FakeImageFetcher(mapOf(url to byteArrayOf(9, 9)))
        val decoder = FakeImageDecoder(shouldFail = true)
        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = fetcher,
            decoder = decoder,
            dispatcher = testDispatcher
        )

        val result = loader.load(url)
        assertTrue(result.isFailure)
        assertNull(ImageMemoryCache.get(url))
    }

    @Test
    fun testHeadersPassedToFetcher() = runTest(testDispatcher) {
        val url = "https://example.com/headers.png"
        val customHeaders = mapOf("Authorization" to "Bearer token123", "User-Agent" to "CloudStream")
        val fetcher = FakeImageFetcher(mapOf(url to byteArrayOf(1, 2)))
        val decoder = FakeImageDecoder()
        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = fetcher,
            decoder = decoder,
            dispatcher = testDispatcher
        )

        val result = loader.load(url, customHeaders)
        assertTrue(result.isSuccess)
        assertEquals(1, fetcher.receivedHeaders.size)
        assertEquals("Bearer token123", fetcher.receivedHeaders.first()["Authorization"])
        assertEquals("CloudStream", fetcher.receivedHeaders.first()["User-Agent"])
    }

    @Test
    fun testDoubleCheckedCacheAvoidsFetch() = runTest(testDispatcher) {
        val url = "https://example.com/concurrent.png"
        val fakeBitmap = createFakeBitmap()
        val fetcher = FakeImageFetcher(mapOf(url to byteArrayOf(1)))
        val decoder = FakeImageDecoder()

        val loader = DefaultImageLoader(
            cache = ImageMemoryCache,
            fetcher = fetcher,
            decoder = decoder,
            dispatcher = testDispatcher
        )

        ImageMemoryCache.put(url, fakeBitmap)
        val result = loader.load(url)
        assertTrue(result.isSuccess)
        assertEquals(fakeBitmap, result.getOrNull())
        assertEquals(0, fetcher.fetchedUrls.size)
    }
}
