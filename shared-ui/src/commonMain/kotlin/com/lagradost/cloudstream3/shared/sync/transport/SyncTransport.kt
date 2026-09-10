package com.lagradost.cloudstream3.shared.sync.transport

import com.lagradost.cloudstream3.shared.sync.models.SyncDelta
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SyncTransport {
    val transportId: String
    suspend fun pushDelta(delta: SyncDelta): Result<Unit>
    suspend fun fetchDeltas(sinceTimestamp: Long): Result<ImmutableList<SyncDelta>>
}

class InMemorySyncTransport(
    override val transportId: String = "in-memory"
) : SyncTransport {
    private val mutex = Mutex()
    private val deltas = mutableListOf<SyncDelta>()

    override suspend fun pushDelta(delta: SyncDelta): Result<Unit> = runCatching {
        mutex.withLock {
            deltas.add(delta)
        }
    }

    override suspend fun fetchDeltas(sinceTimestamp: Long): Result<ImmutableList<SyncDelta>> = runCatching {
        mutex.withLock {
            deltas
                .filter { it.timestamp > sinceTimestamp }
                .toImmutableList()
        }
    }

    suspend fun clear() {
        mutex.withLock {
            deltas.clear()
        }
    }

    suspend fun getAllDeltas(): ImmutableList<SyncDelta> = mutex.withLock {
        deltas.toImmutableList()
    }
}
