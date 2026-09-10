package com.lagradost.cloudstream3.shared.sync.manager

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.sync.cloud.CloudStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.GoogleDriveStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.LocalStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.WebDavStorageClient
import com.lagradost.cloudstream3.shared.sync.engine.SyncEngine
import com.lagradost.cloudstream3.shared.sync.engine.SyncEngineImpl
import com.lagradost.cloudstream3.shared.sync.models.SyncApplyResult
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleDriveOAuth
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleOAuthToken
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleUserInfo
import com.lagradost.cloudstream3.shared.sync.transport.CloudStorageSyncTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class CloudSyncProvider {
    NONE,
    GOOGLE_DRIVE,
    WEBDAV,
    LOCAL_FOLDER
}

@Immutable
@Serializable
data class CloudSyncState(
    val provider: CloudSyncProvider = CloudSyncProvider.NONE,
    val isConnected: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountAvatar: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncSummary: String? = null,
    val autoSyncEnabled: Boolean = false,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val localFolderPath: String = ""
)

object CloudSyncManager {
    const val PREF_CLOUD_SYNC_PROVIDER = "cloud_sync_provider"
    const val PREF_CLOUD_SYNC_WEBDAV_URL = "cloud_sync_webdav_url"
    const val PREF_CLOUD_SYNC_WEBDAV_USER = "cloud_sync_webdav_user"
    const val PREF_CLOUD_SYNC_WEBDAV_PASS = "cloud_sync_webdav_pass"
    const val PREF_CLOUD_SYNC_LOCAL_PATH = "cloud_sync_local_path"
    const val PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN = "cloud_sync_gdrive_refresh_token"
    const val PREF_CLOUD_SYNC_GDRIVE_ACCESS_TOKEN = "cloud_sync_gdrive_access_token"
    const val PREF_CLOUD_SYNC_GDRIVE_TOKEN_EXPIRY = "cloud_sync_gdrive_token_expiry"
    const val PREF_CLOUD_SYNC_GDRIVE_EMAIL = "cloud_sync_gdrive_email"
    const val PREF_CLOUD_SYNC_GDRIVE_NAME = "cloud_sync_gdrive_name"
    const val PREF_CLOUD_SYNC_GDRIVE_PICTURE = "cloud_sync_gdrive_picture"
    const val PREF_CLOUD_SYNC_LAST_TIMESTAMP = "cloud_sync_last_timestamp"
    const val PREF_CLOUD_SYNC_LAST_SUMMARY = "cloud_sync_last_summary"
    const val PREF_CLOUD_SYNC_AUTO_ENABLED = "cloud_sync_auto_enabled"
    const val PREF_CLOUD_SYNC_DEVICE_ID = "cloud_sync_device_id"

    // Proactively refresh 60s before actual token expiration to prevent mid-flight invalidation
    private const val TOKEN_EXPIRY_BUFFER_MS = 60_000L

    private val tokenMutex = Mutex()
    private val syncMutex = Mutex()

    private var testSyncEngine: SyncEngine? = null

    private val _syncState = MutableStateFlow(buildCurrentState())
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    fun setSyncEngineForTesting(engine: SyncEngine?) {
        testSyncEngine = engine
    }

    private fun resolveSyncEngine(): SyncEngine {
        return testSyncEngine ?: SyncEngineImpl(DatabaseDriverFactory.getDatabase())
    }

    fun getActiveProvider(): CloudSyncProvider {
        val raw = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_PROVIDER) ?: return CloudSyncProvider.NONE
        return runCatching { CloudSyncProvider.valueOf(raw) }.getOrDefault(CloudSyncProvider.NONE)
    }

    fun setProvider(provider: CloudSyncProvider) {
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, provider.name)
        refreshState()
    }

    suspend fun getValidGoogleAccessToken(): String? = tokenMutex.withLock {
        val refreshToken = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN)
        if (refreshToken.isNullOrBlank()) {
            return null
        }

        val currentAccessToken = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_ACCESS_TOKEN)
        val expiryTimestamp = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_TOKEN_EXPIRY)?.toLongOrNull() ?: 0L
        val now = APIHolder.unixTimeMS

        if (!currentAccessToken.isNullOrBlank() && now < (expiryTimestamp - TOKEN_EXPIRY_BUFFER_MS)) {
            return currentAccessToken
        }

        val refreshResult = GoogleDriveOAuth.refreshAccessToken(refreshToken = refreshToken)
        val token = refreshResult.getOrNull() ?: return null

        persistGoogleTokens(token)
        return token.accessToken
    }

    private fun persistGoogleTokens(token: GoogleOAuthToken) {
        val newExpiry = APIHolder.unixTimeMS + (token.expiresIn * 1000L)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_ACCESS_TOKEN, token.accessToken)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_TOKEN_EXPIRY, newExpiry.toString())
        if (!token.refreshToken.isNullOrBlank()) {
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN, token.refreshToken)
        }
    }

    suspend fun getActiveClient(): CloudStorageClient? = getClientForProvider(getActiveProvider())

    suspend fun testConnection(provider: CloudSyncProvider? = null): Result<Boolean> = runCatching {
        val targetProvider = provider ?: getActiveProvider()
        val client = getClientForProvider(targetProvider)
            ?: throw IllegalStateException("Cloud storage client not configured for $targetProvider")
        client.testConnection().getOrThrow()
    }

    suspend fun syncNow(accountId: Int = 0, deviceId: String = ""): Result<SyncApplyResult> = syncMutex.withLock {
        runCatching {
            val client = getActiveClient()
                ?: throw IllegalStateException("No active cloud storage provider configured")

            _syncState.update { it.copy(isSyncing = true) }
            try {
                executeSync(client, accountId, deviceId)
            } finally {
                _syncState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private suspend fun executeSync(
        client: CloudStorageClient,
        accountId: Int,
        deviceId: String
    ): SyncApplyResult {
        val transport = CloudStorageSyncTransport(storageClient = client)
        val syncEngine = resolveSyncEngine()
        val effectiveDeviceId = deviceId.ifBlank { getOrCreateDeviceId() }
        val lastTimestamp = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LAST_TIMESTAMP)?.toLongOrNull() ?: 0L

        val applyResult = syncEngine.sync(
            accountId = accountId,
            deviceId = effectiveDeviceId,
            transport = transport,
            lastSyncTimestamp = lastTimestamp
        ).getOrThrow()

        recordSyncSuccess(applyResult)
        return applyResult
    }

    private fun recordSyncSuccess(result: SyncApplyResult) {
        val now = APIHolder.unixTimeMS
        val summary = "Applied ${result.totalApplied}, Skipped ${result.conflictsSkipped}"
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_LAST_TIMESTAMP, now.toString())
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_LAST_SUMMARY, summary)
        _syncState.update {
            it.copy(
                lastSyncTimestamp = now,
                lastSyncSummary = summary
            )
        }
    }

    fun disconnectProvider(clearCredentials: Boolean = true) {
        val currentProvider = getActiveProvider()
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, CloudSyncProvider.NONE.name)
        if (clearCredentials) {
            clearProviderCredentials(currentProvider)
        }
        refreshState()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        AppPreferenceManager.setBooleanSync(PREF_CLOUD_SYNC_AUTO_ENABLED, enabled)
        _syncState.update { it.copy(autoSyncEnabled = enabled) }
    }

    fun configureWebDav(url: String, username: String, pass: String) {
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, CloudSyncProvider.WEBDAV.name)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_WEBDAV_URL, url)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_WEBDAV_USER, username)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_WEBDAV_PASS, pass)
        refreshState()
    }

    fun saveWebDavConfig(url: String, username: String, pass: String) =
        configureWebDav(url, username, pass)

    fun configureLocalFolder(path: String) {
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, CloudSyncProvider.LOCAL_FOLDER.name)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_LOCAL_PATH, path)
        refreshState()
    }

    fun saveLocalSyncPath(path: String) =
        configureLocalFolder(path)

    fun disconnect(clearCredentials: Boolean = true) =
        disconnectProvider(clearCredentials)

    fun configureGoogleDrive(token: GoogleOAuthToken, userInfo: GoogleUserInfo? = null) {
        val expiry = APIHolder.unixTimeMS + (token.expiresIn * 1000L)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, CloudSyncProvider.GOOGLE_DRIVE.name)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_ACCESS_TOKEN, token.accessToken)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_TOKEN_EXPIRY, expiry.toString())
        if (!token.refreshToken.isNullOrBlank()) {
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN, token.refreshToken)
        }
        if (userInfo != null) {
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_EMAIL, userInfo.email)
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_NAME, userInfo.name)
            userInfo.picture?.let { AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_GDRIVE_PICTURE, it) }
        }
        refreshState()
    }

    suspend fun authenticateGoogleDrive(
        authCode: String,
        codeVerifier: String,
        clientId: String = GoogleDriveOAuth.DEFAULT_CLIENT_ID,
        clientSecret: String? = GoogleDriveOAuth.DEFAULT_CLIENT_SECRET,
        redirectUri: String = GoogleDriveOAuth.DEFAULT_REDIRECT_URI
    ): Result<GoogleUserInfo> = runCatching {
        val sanitizedCode = GoogleDriveOAuth.sanitizeAuthCode(authCode)
        val token = GoogleDriveOAuth.exchangeCode(
            clientId = clientId,
            clientSecret = clientSecret,
            code = sanitizedCode,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri
        ).getOrThrow()

        val userInfo = GoogleDriveOAuth.fetchUserInfo(token.accessToken).getOrNull()
        configureGoogleDrive(token, userInfo)
        userInfo ?: GoogleUserInfo()
    }

    fun getOrCreateDeviceId(): String {
        val existing = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_DEVICE_ID, generated)
        return generated
    }

    fun refreshState() {
        _syncState.value = buildCurrentState(_syncState.value.isSyncing)
    }

    private suspend fun getClientForProvider(provider: CloudSyncProvider): CloudStorageClient? = when (provider) {
        CloudSyncProvider.GOOGLE_DRIVE -> resolveGoogleDriveClient()
        CloudSyncProvider.WEBDAV -> resolveWebDavClient()
        CloudSyncProvider.LOCAL_FOLDER -> resolveLocalStorageClient()
        CloudSyncProvider.NONE -> null
    }

    private fun resolveGoogleDriveClient(): CloudStorageClient? {
        val token = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN)
        if (token.isNullOrBlank()) return null
        return GoogleDriveStorageClient(accessTokenProvider = { getValidGoogleAccessToken() })
    }

    private fun resolveWebDavClient(): CloudStorageClient? {
        val url = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_URL).orEmpty()
        if (url.isBlank()) return null
        val user = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_USER).orEmpty()
        val pass = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_PASS).orEmpty()
        return WebDavStorageClient(serverUrl = url, username = user, password = pass)
    }

    private fun resolveLocalStorageClient(): CloudStorageClient? {
        val path = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH).orEmpty()
        if (path.isBlank()) return null
        return LocalStorageClient(rootDirectoryPath = path)
    }

    private fun isProviderConnected(provider: CloudSyncProvider): Boolean = when (provider) {
        CloudSyncProvider.GOOGLE_DRIVE -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN).isNullOrBlank()
        CloudSyncProvider.WEBDAV -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_URL).isNullOrBlank()
        CloudSyncProvider.LOCAL_FOLDER -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH).isNullOrBlank()
        CloudSyncProvider.NONE -> false
    }

    private fun resolveAccountDetails(provider: CloudSyncProvider): Triple<String?, String?, String?> = when (provider) {
        CloudSyncProvider.GOOGLE_DRIVE -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_NAME),
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_EMAIL),
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_PICTURE)
        )
        CloudSyncProvider.WEBDAV -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_USER)?.ifBlank { null },
            null,
            null
        )
        CloudSyncProvider.LOCAL_FOLDER -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH)?.ifBlank { null },
            null,
            null
        )
        CloudSyncProvider.NONE -> Triple(null, null, null)
    }

    private fun buildCurrentState(isSyncing: Boolean = false): CloudSyncState {
        val provider = getActiveProvider()
        val connected = isProviderConnected(provider)
        val (name, email, avatar) = resolveAccountDetails(provider)

        return CloudSyncState(
            provider = provider,
            isConnected = connected,
            accountName = name,
            accountEmail = email,
            accountAvatar = avatar,
            isSyncing = isSyncing,
            lastSyncTimestamp = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LAST_TIMESTAMP)?.toLongOrNull() ?: 0L,
            lastSyncSummary = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LAST_SUMMARY),
            autoSyncEnabled = AppPreferenceManager.getBooleanSync(PREF_CLOUD_SYNC_AUTO_ENABLED, false),
            webDavUrl = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_URL).orEmpty(),
            webDavUsername = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_USER).orEmpty(),
            localFolderPath = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH).orEmpty()
        )
    }

    private fun clearProviderCredentials(provider: CloudSyncProvider) {
        when (provider) {
            CloudSyncProvider.GOOGLE_DRIVE -> clearGoogleCredentials()
            CloudSyncProvider.WEBDAV -> clearWebDavCredentials()
            CloudSyncProvider.LOCAL_FOLDER -> clearLocalFolderConfig()
            CloudSyncProvider.NONE -> Unit
        }
    }

    private fun clearGoogleCredentials() {
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_GDRIVE_ACCESS_TOKEN)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_GDRIVE_TOKEN_EXPIRY)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_GDRIVE_EMAIL)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_GDRIVE_NAME)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_GDRIVE_PICTURE)
    }

    private fun clearWebDavCredentials() {
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_WEBDAV_URL)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_WEBDAV_USER)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_WEBDAV_PASS)
    }

    private fun clearLocalFolderConfig() {
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_LOCAL_PATH)
    }
}
