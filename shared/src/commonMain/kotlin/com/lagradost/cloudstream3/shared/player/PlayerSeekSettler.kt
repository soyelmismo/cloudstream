package com.lagradost.cloudstream3.shared.player

import com.lagradost.cloudstream3.APIHolder
import kotlin.jvm.Volatile
import kotlin.math.abs

/**
 * Thread-safe seek coordinator that prevents stale decoder timestamps from rubber-banding the UI.
 * Used by video player engines to debounce and settle seek and initial resume transitions.
 */
class PlayerSeekSettler(
    private val settlingWindowMs: Long = 400L,
    private val maxSeekTimeoutMs: Long = 3000L,
    private val targetToleranceMs: Long = 1500L
) {
    @Volatile
    private var pendingSeekTargetMs: Long = -1L

    @Volatile
    private var seekInitiatedAtMs: Long = 0L

    @Volatile
    private var seekOriginMs: Long = 0L

    val isSeeking: Boolean
        get() = pendingSeekTargetMs >= 0L

    val targetPositionMs: Long
        get() = pendingSeekTargetMs

    fun startSeek(targetMs: Long, currentPosMs: Long) {
        if (!isSeeking) {
            seekOriginMs = currentPosMs
        }
        pendingSeekTargetMs = targetMs
        seekInitiatedAtMs = APIHolder.unixTimeMS
    }

    fun filterIncomingPosition(incomingMs: Long): Long? {
        val target = pendingSeekTargetMs
        if (target < 0L) return incomingMs

        val now = APIHolder.unixTimeMS
        val elapsed = now - seekInitiatedAtMs

        if (elapsed > maxSeekTimeoutMs) {
            pendingSeekTargetMs = -1L
            return incomingMs
        }

        val distToTarget = abs(incomingMs - target)
        val seekDistance = abs(target - seekOriginMs)

        if (seekDistance > targetToleranceMs && distToTarget <= targetToleranceMs) {
            pendingSeekTargetMs = -1L
            return incomingMs
        }

        if (seekDistance <= targetToleranceMs && elapsed >= settlingWindowMs) {
            pendingSeekTargetMs = -1L
            return incomingMs
        }

        return null
    }

    fun reset() {
        pendingSeekTargetMs = -1L
        seekInitiatedAtMs = 0L
        seekOriginMs = 0L
    }
}
