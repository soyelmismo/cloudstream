package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.util.Rational
import com.lagradost.cloudstream3.shared.player.native.*
import com.lagradost.cloudstream3.shared.player.PlayerEvent as SharedPlayerEvent
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidVideoPlayerTest {

    private class FakePlayer : IPlayer {
        var eventHandler: ((PlayerEvent) -> Unit)? = null
        var lastLoadedLink: ExtractorLink? = null
        var lastLoadedSubtitles: Set<SubtitleData>? = null
        var lastLoadedSubtitle: SubtitleData? = null
        var lastInitSubtitlesSubtitles: Set<SubtitleData>? = null
        var lastInitSubtitlesSubtitle: SubtitleData? = null
        var lastHandledEvent: CSPlayerEvent? = null
        var lastSeekPosition: Long? = null
        var isPlaying: Boolean = false
        var isReleased: Boolean = false
        var isCallbacksReleased: Boolean = false

        fun triggerEvent(event: PlayerEvent) {
            eventHandler?.invoke(event)
        }

        override fun getPlaybackSpeed(): Float = 1f
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getIsPlaying(): Boolean = isPlaying
        override fun getDuration(): Long? = 100_000L
        override fun getPosition(): Long? = 0L
        override fun seekTime(time: Long, source: PlayerEventSource) {}
        override fun seekTo(time: Long, source: PlayerEventSource) {
            lastSeekPosition = time
        }
        override fun getSubtitleOffset(): Long = 0L
        override fun setSubtitleOffset(offset: Long) {}
        override fun initCallbacks(
            eventHandler: (PlayerEvent) -> Unit,
            requestedListeningPercentages: List<Int>?
        ) {
            this.eventHandler = eventHandler
        }
        override fun releaseCallbacks() {
            isCallbacksReleased = true
            this.eventHandler = null
        }
        override fun updateSubtitleStyle(style: SaveCaptionStyle) {}
        override fun saveData() {}
        override fun addTimeStamps(timeStamps: List<VideoSkipStamp>) {}
        override fun loadPlayer(
            context: Context,
            sameEpisode: Boolean,
            link: ExtractorLink?,
            data: ExtractorUri?,
            startPosition: Long?,
            subtitles: Set<SubtitleData>,
            subtitle: SubtitleData?,
            autoPlay: Boolean?,
            preview: Boolean
        ) {
            lastLoadedLink = link
            lastLoadedSubtitles = subtitles
            lastLoadedSubtitle = subtitle
            isPlaying = autoPlay == true
        }
        override fun reloadPlayer(context: Context) {}
        override fun getPreview(fraction: Float): Bitmap? = null
        override fun hasPreview(): Boolean = false
        override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
            lastInitSubtitlesSubtitles = subtitles
        }
        override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
            lastInitSubtitlesSubtitle = subtitle
            return false
        }
        override fun initSubtitles(subtitles: Set<SubtitleData>, subtitle: SubtitleData?) {
            lastInitSubtitlesSubtitles = subtitles
            lastInitSubtitlesSubtitle = subtitle
        }
        override fun getCurrentPreferredSubtitle(): SubtitleData? = null
        override fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource) {
            lastHandledEvent = event
            when (event) {
                CSPlayerEvent.Play -> isPlaying = true
                CSPlayerEvent.Pause -> isPlaying = false
                else -> {}
            }
        }
        override fun onStop() {
            isPlaying = false
        }
        override fun onPause() {
            isPlaying = false
        }
        override fun onResume(context: Context) {
            isPlaying = true
        }
        override fun release() {
            isReleased = true
        }
        override fun isActive(): Boolean = !isReleased
        override fun getVideoTracks(): CurrentTracks = CurrentTracks(null, null, emptyList(), emptyList(), emptyList(), emptyList())
        override fun getAspectRatio(): Rational? = null
        override fun setMaxVideoSize(width: Int, height: Int, id: String?) {}
        override fun setPreferredAudioTrack(trackLanguage: String?, id: String?, formatIndex: Int?) {}
        override fun getSubtitleCues(): List<SubtitleCue> = emptyList()
    }

    @Test
    fun `play url updates state and loads player`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        val testUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        player.play(testUrl)

        assertEquals(testUrl, fakePlayer.lastLoadedLink?.url)
        assertEquals(testUrl, player.state.currentUrl)
        assertTrue(player.state.isBuffering)
    }

    @Test
    fun `play with PlayerQuality and subtitles maps data and selects default subtitle`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        val customLink = ExtractorLink(
            source = "CustomSource",
            name = "1080p",
            url = "https://example.com/stream.m3u8",
            referer = "https://example.com",
            quality = 1080,
            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
        )
        val quality = com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality(
            id = "q1",
            name = "1080p",
            quality = 1080,
            url = "https://example.com/stream.m3u8",
            extractorLink = customLink
        )
        val sub1 = com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack(
            id = "sub1",
            name = "English",
            url = "https://example.com/en.vtt",
            isDefault = true
        )
        val sub2 = com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack(
            id = "sub2",
            name = "Spanish",
            url = "https://example.com/es.srt",
            isDefault = false
        )

        player.play(quality, listOf(sub1, sub2))

        assertEquals(customLink, fakePlayer.lastLoadedLink)
        assertEquals(2, fakePlayer.lastLoadedSubtitles?.size)
        assertEquals("https://example.com/en.vtt", fakePlayer.lastLoadedSubtitle?.url)
        assertEquals("https://example.com/stream.m3u8", player.state.currentUrl)
        assertTrue(player.state.isBuffering)
    }

    @Test
    fun `loadSubtitle constructs SubtitleData and initializes subtitles`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        val subUrl = "https://example.com/sub.vtt"
        player.loadSubtitle(subUrl, mapOf("Authorization" to "Bearer token"))

        assertEquals(1, fakePlayer.lastInitSubtitlesSubtitles?.size)
        assertEquals(subUrl, fakePlayer.lastInitSubtitlesSubtitle?.url)
        assertEquals("Bearer token", fakePlayer.lastInitSubtitlesSubtitle?.headers?.get("Authorization"))
    }

    @Test
    fun `status event updates isPlaying and isBuffering states`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        fakePlayer.triggerEvent(StatusEvent(CSPlayerLoading.IsBuffering, CSPlayerLoading.IsPlaying))
        assertTrue(player.state.isPlaying)
        assertFalse(player.state.isBuffering)

        fakePlayer.triggerEvent(StatusEvent(CSPlayerLoading.IsPlaying, CSPlayerLoading.IsPaused))
        assertFalse(player.state.isPlaying)
        assertFalse(player.state.isBuffering)
    }

    @Test
    fun `position event updates position and duration and emits event`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        fakePlayer.triggerEvent(PositionEvent(PlayerEventSource.Player, fromMs = 0L, toMs = 5000L, durationMs = 60000L))
        assertEquals(5000L, player.state.positionMs)
        assertEquals(60000L, player.state.durationMs)
    }

    @Test
    fun `pause and resume control playback and state`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        player.pause()
        assertEquals(CSPlayerEvent.Pause, fakePlayer.lastHandledEvent)
        assertFalse(player.state.isPlaying)

        player.resume()
        assertEquals(CSPlayerEvent.Play, fakePlayer.lastHandledEvent)
        assertTrue(player.state.isPlaying)
    }

    @Test
    fun `seekTo delegates to player and updates position`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        player.seekTo(12345L)
        assertEquals(12345L, fakePlayer.lastSeekPosition)
        assertEquals(12345L, player.state.positionMs)
    }

    @Test
    fun `stop resets state and releases player`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        player.play("https://example.com/video.mp4")
        player.stop()

        assertTrue(fakePlayer.isReleased)
        assertEquals(0L, player.state.positionMs)
        assertEquals(null, player.state.currentUrl)
        assertFalse(player.state.isPlaying)
    }

    @Test
    fun `error event emits OnError`() = runTest(UnconfinedTestDispatcher()) {
        val fakePlayer = FakePlayer()
        val mockContext = mock(Context::class.java)
        val player = AndroidVideoPlayer(mockContext, fakePlayer, this)

        fakePlayer.triggerEvent(ErrorEvent(RuntimeException("Test error message")))
        // player.events extraBufferCapacity allows observing the emitted event
        val event = player.events.first()
        assertTrue(event is SharedPlayerEvent.OnError)
        assertEquals("Test error message", (event as SharedPlayerEvent.OnError).message)
    }
}
