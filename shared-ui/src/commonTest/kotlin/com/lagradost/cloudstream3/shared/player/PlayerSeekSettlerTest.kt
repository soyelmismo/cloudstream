package com.lagradost.cloudstream3.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerSeekSettlerTest {

    @Test
    fun testNormalPlaybackPassthrough() {
        val settler = PlayerSeekSettler()
        assertFalse(settler.isSeeking)
        assertEquals(10_000L, settler.filterIncomingPosition(10_000L))
        assertEquals(10_250L, settler.filterIncomingPosition(10_250L))
    }

    @Test
    fun testLargeSeekFiltersStaleTimestampsUntilTargetReached() {
        val settler = PlayerSeekSettler(
            settlingWindowMs = 400L,
            maxSeekTimeoutMs = 3000L,
            targetToleranceMs = 1500L
        )

        // Starting at 10s, seek to 50s
        settler.startSeek(targetMs = 50_000L, currentPosMs = 10_000L)
        assertTrue(settler.isSeeking)
        assertEquals(50_000L, settler.targetPositionMs)

        // Stale pre-seek timestamps from decoder buffer
        assertNull(settler.filterIncomingPosition(10_000L))
        assertNull(settler.filterIncomingPosition(10_250L))
        assertNull(settler.filterIncomingPosition(10_500L))

        // Decoder lands within tolerance of target (e.g. 49_800L)
        val settledPos = settler.filterIncomingPosition(49_800L)
        assertEquals(49_800L, settledPos)
        assertFalse(settler.isSeeking)

        // Subsequent timestamps pass through normally
        assertEquals(50_100L, settler.filterIncomingPosition(50_100L))
    }

    @Test
    fun testSmallSeekFiltersTrailingPacketBeforeAccepting() {
        val settler = PlayerSeekSettler(
            settlingWindowMs = 100L,
            maxSeekTimeoutMs = 1000L,
            targetToleranceMs = 1500L
        )

        // Seek from 10s to 11s (distance = 1s <= 1500ms tolerance)
        settler.startSeek(targetMs = 11_000L, currentPosMs = 10_000L)
        assertTrue(settler.isSeeking)

        // Immediate callback before settling window has elapsed
        assertNull(settler.filterIncomingPosition(10_000L))
    }

    @Test
    fun testResetClearsSeekingState() {
        val settler = PlayerSeekSettler()
        settler.startSeek(targetMs = 30_000L, currentPosMs = 5_000L)
        assertTrue(settler.isSeeking)

        settler.reset()
        assertFalse(settler.isSeeking)
        assertEquals(10_000L, settler.filterIncomingPosition(10_000L))
    }

    @Test
    fun testRapidConsecutiveSeeksPreserveRealOrigin() {
        val settler = PlayerSeekSettler(
            settlingWindowMs = 400L,
            maxSeekTimeoutMs = 3000L,
            targetToleranceMs = 1500L
        )

        // First seek from 10s to 50s
        settler.startSeek(targetMs = 50_000L, currentPosMs = 10_000L)
        assertTrue(settler.isSeeking)

        // Rapid second seek to 51s before settling (e.g. dragging seekbar)
        settler.startSeek(targetMs = 51_000L, currentPosMs = 50_000L)
        assertTrue(settler.isSeeking)
        assertEquals(51_000L, settler.targetPositionMs)

        // Old decoder position should still be filtered out because origin is 10s, distance is 41s
        assertNull(settler.filterIncomingPosition(10_200L))

        // Reaching target settles seeking
        val settled = settler.filterIncomingPosition(50_900L)
        assertEquals(50_900L, settled)
        assertFalse(settler.isSeeking)
    }
}
