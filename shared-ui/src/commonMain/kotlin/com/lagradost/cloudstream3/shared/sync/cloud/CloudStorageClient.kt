package com.lagradost.cloudstream3.shared.sync.cloud

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class RemoteFileMetadata(
    val path: String,
    val name: String,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
    val isDirectory: Boolean = false
)

interface CloudStorageClient {
    val providerId: String
    val displayName: String

    suspend fun testConnection(): Result<Boolean>
    suspend fun listFiles(remoteDir: String, modifiedSince: Long = 0L): Result<List<RemoteFileMetadata>>
    suspend fun readFile(remotePath: String): Result<String>
    suspend fun writeFile(remotePath: String, content: String): Result<Unit>
    suspend fun deleteFile(remotePath: String): Result<Unit>
}
