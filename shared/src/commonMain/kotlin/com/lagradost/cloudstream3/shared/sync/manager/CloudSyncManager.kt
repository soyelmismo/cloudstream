package com.lagradost.cloudstream3.shared.sync.manager

import androidx.compose.runtime.Immutable
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.sync.cloud.CloudStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.GoogleDriveStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.LocalStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.OneDriveStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.S3StorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.WebDavStorageClient
import com.lagradost.cloudstream3.shared.sync.engine.SyncEngine
import com.lagradost.cloudstream3.shared.sync.engine.SyncEngineImpl
import com.lagradost.cloudstream3.shared.sync.models.SyncApplyResult
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleDriveOAuth
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleOAuthToken
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleUserInfo
import com.lagradost.cloudstream3.shared.sync.oauth.OneDriveOAuth
import com.lagradost.cloudstream3.shared.sync.oauth.OneDriveOAuthToken
import com.lagradost.cloudstream3.shared.sync.oauth.OneDriveUserInfo
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
    ONEDRIVE,
    WEBDAV,
    S3_COMPATIBLE,
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
    val localFolderPath: String = "",
    val oneDriveAccountName: String? = null,
    val oneDriveAccountEmail: String? = null,
    val s3Endpoint: String = "",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3Region: String = "auto",
    val pendingOneDriveOAuthUrl: String? = null
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
    const val PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN = "cloud_sync_onedrive_refresh_token"
    const val PREF_CLOUD_SYNC_ONEDRIVE_ACCESS_TOKEN = "cloud_sync_onedrive_access_token"
    const val PREF_CLOUD_SYNC_ONEDRIVE_TOKEN_EXPIRY = "cloud_sync_onedrive_token_expiry"
    const val PREF_CLOUD_SYNC_ONEDRIVE_EMAIL = "cloud_sync_onedrive_email"
    const val PREF_CLOUD_SYNC_ONEDRIVE_NAME = "cloud_sync_onedrive_name"
    const val PREF_CLOUD_SYNC_S3_ENDPOINT = "cloud_sync_s3_endpoint"
    const val PREF_CLOUD_SYNC_S3_BUCKET = "cloud_sync_s3_bucket"
    const val PREF_CLOUD_SYNC_S3_ACCESS_KEY = "cloud_sync_s3_access_key"
    const val PREF_CLOUD_SYNC_S3_SECRET_KEY = "cloud_sync_s3_secret_key"
    const val PREF_CLOUD_SYNC_S3_REGION = "cloud_sync_s3_region"
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

    suspend fun getValidOneDriveAccessToken(): String? = tokenMutex.withLock {
        val refreshToken = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN)
        if (refreshToken.isNullOrBlank()) {
            return null
        }

        val currentAccessToken = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_ACCESS_TOKEN)
        val expiryTimestamp = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_TOKEN_EXPIRY)?.toLongOrNull() ?: 0L
        val now = APIHolder.unixTimeMS

        if (!currentAccessToken.isNullOrBlank() && now < (expiryTimestamp - TOKEN_EXPIRY_BUFFER_MS)) {
            return currentAccessToken
        }

        val refreshResult = OneDriveOAuth.refreshAccessToken(refreshToken = refreshToken)
        val token = refreshResult.getOrNull() ?: return null

        persistOneDriveTokens(token)
        return token.accessToken
    }

    private fun persistOneDriveTokens(token: OneDriveOAuthToken) {
        val newExpiry = APIHolder.unixTimeMS + (token.expiresIn * 1000L)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_ACCESS_TOKEN, token.accessToken)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_TOKEN_EXPIRY, newExpiry.toString())
        if (!token.refreshToken.isNullOrBlank()) {
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN, token.refreshToken)
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
            } catch (t: Throwable) {
                Log.e("CloudSync", "Sync failed for ${client.providerId}: ${t.message}")
                throw t
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
        val effectiveAccountId = if (accountId != 0) accountId else AppPreferenceManager.getIntSync("active_account_id", 0)
        val transport = CloudStorageSyncTransport(storageClient = client)
        val syncEngine = resolveSyncEngine()
        val effectiveDeviceId = deviceId.ifBlank { getOrCreateDeviceId() }
        val providerTimestampKey = "${PREF_CLOUD_SYNC_LAST_TIMESTAMP}_${client.providerId}"
        val lastTimestamp = AppPreferenceManager.getStringSync(providerTimestampKey)?.toLongOrNull() ?: 0L

        Log.i("CloudSync", "Starting sync: provider=${client.providerId}, account=$effectiveAccountId, device=$effectiveDeviceId, since=$lastTimestamp")

        val applyResult = syncEngine.sync(
            accountId = effectiveAccountId,
            deviceId = effectiveDeviceId,
            transport = transport,
            lastSyncTimestamp = lastTimestamp
        ).getOrThrow()

        Log.i("CloudSync", "Sync completed: applied=${applyResult.totalApplied}, pushed=${applyResult.localItemsPushed}, skipped=${applyResult.conflictsSkipped}")
        recordSyncSuccess(applyResult, client.providerId)
        return applyResult
    }

    private fun recordSyncSuccess(result: SyncApplyResult, providerId: String) {
        val now = APIHolder.unixTimeMS
        val summary = if (result.localItemsPushed > 0) {
            "Applied ${result.totalApplied}, Pushed ${result.localItemsPushed}, Skipped ${result.conflictsSkipped}"
        } else {
            "Applied ${result.totalApplied}, Skipped ${result.conflictsSkipped}"
        }
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_LAST_TIMESTAMP, now.toString())
        AppPreferenceManager.setStringSync("${PREF_CLOUD_SYNC_LAST_TIMESTAMP}_$providerId", now.toString())
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_LAST_SUMMARY, summary)
        _syncState.update {
            it.copy(
                lastSyncTimestamp = now,
                lastSyncSummary = summary
            )
        }
    }

    fun resetSyncTimestamps() {
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_LAST_TIMESTAMP)
        AppPreferenceManager.removeKeysSync(PREF_CLOUD_SYNC_LAST_TIMESTAMP)
        _syncState.update { it.copy(lastSyncTimestamp = 0L) }
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

    fun configureOneDrive(token: OneDriveOAuthToken, userInfo: OneDriveUserInfo? = null) {
        val expiry = APIHolder.unixTimeMS + (token.expiresIn * 1000L)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, CloudSyncProvider.ONEDRIVE.name)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_ACCESS_TOKEN, token.accessToken)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_TOKEN_EXPIRY, expiry.toString())
        if (!token.refreshToken.isNullOrBlank()) {
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN, token.refreshToken)
        }
        if (userInfo != null) {
            val email = userInfo.mail ?: userInfo.userPrincipalName
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_EMAIL, email)
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_ONEDRIVE_NAME, userInfo.displayName)
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

    suspend fun authenticateOneDrive(
        authCode: String,
        codeVerifier: String,
        clientId: String = OneDriveOAuth.DEFAULT_CLIENT_ID,
        clientSecret: String? = OneDriveOAuth.DEFAULT_CLIENT_SECRET,
        redirectUri: String = OneDriveOAuth.DEFAULT_REDIRECT_URI
    ): Result<OneDriveUserInfo> = runCatching {
        val sanitizedCode = OneDriveOAuth.sanitizeAuthCode(authCode)
        val token = OneDriveOAuth.exchangeCode(
            clientId = clientId,
            clientSecret = clientSecret,
            code = sanitizedCode,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri
        ).getOrThrow()

        val userInfo = OneDriveOAuth.fetchUserInfo(token.accessToken).getOrNull()
        configureOneDrive(token, userInfo)
        userInfo ?: OneDriveUserInfo()
    }

    fun configureS3(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        region: String = "auto"
    ) {
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_PROVIDER, CloudSyncProvider.S3_COMPATIBLE.name)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_S3_ENDPOINT, endpoint)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_S3_BUCKET, bucket)
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_S3_ACCESS_KEY, accessKey)
        if (secretKey.isNotBlank()) {
            AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_S3_SECRET_KEY, secretKey)
        }
        AppPreferenceManager.setStringSync(PREF_CLOUD_SYNC_S3_REGION, region)
        refreshState()
    }

    fun saveS3Config(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        region: String = "auto"
    ) = configureS3(endpoint, bucket, accessKey, secretKey, region)

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
        CloudSyncProvider.ONEDRIVE -> resolveOneDriveClient()
        CloudSyncProvider.WEBDAV -> resolveWebDavClient()
        CloudSyncProvider.S3_COMPATIBLE -> resolveS3Client()
        CloudSyncProvider.LOCAL_FOLDER -> resolveLocalStorageClient()
        CloudSyncProvider.NONE -> null
    }

    private fun resolveGoogleDriveClient(): CloudStorageClient? {
        val token = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN)
        if (token.isNullOrBlank()) return null
        return GoogleDriveStorageClient(accessTokenProvider = { getValidGoogleAccessToken() })
    }

    private fun resolveOneDriveClient(): CloudStorageClient? {
        val token = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN)
        if (token.isNullOrBlank()) return null
        return OneDriveStorageClient(accessTokenProvider = { getValidOneDriveAccessToken() })
    }

    private fun resolveWebDavClient(): CloudStorageClient? {
        val url = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_URL).orEmpty()
        if (url.isBlank()) return null
        val user = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_USER).orEmpty()
        val pass = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_PASS).orEmpty()
        return WebDavStorageClient(serverUrl = url, username = user, password = pass)
    }

    private fun resolveS3Client(): CloudStorageClient? {
        val endpoint = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ENDPOINT).orEmpty()
        val bucket = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_BUCKET).orEmpty()
        val accessKey = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ACCESS_KEY).orEmpty()
        val secretKey = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_SECRET_KEY).orEmpty()
        val region = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_REGION) ?: "auto"
        if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) return null
        return S3StorageClient(
            endpointUrl = endpoint,
            bucketName = bucket,
            accessKey = accessKey,
            secretKey = secretKey,
            region = region
        )
    }

    private fun resolveLocalStorageClient(): CloudStorageClient? {
        val path = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH).orEmpty()
        if (path.isBlank()) return null
        return LocalStorageClient(rootDirectoryPath = path)
    }

    private fun isProviderConnected(provider: CloudSyncProvider): Boolean = when (provider) {
        CloudSyncProvider.GOOGLE_DRIVE -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_REFRESH_TOKEN).isNullOrBlank()
        CloudSyncProvider.ONEDRIVE -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN).isNullOrBlank()
        CloudSyncProvider.WEBDAV -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_URL).isNullOrBlank()
        CloudSyncProvider.S3_COMPATIBLE -> isS3Configured()
        CloudSyncProvider.LOCAL_FOLDER -> !AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH).isNullOrBlank()
        CloudSyncProvider.NONE -> false
    }

    private fun isS3Configured(): Boolean {
        val ep = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ENDPOINT)
        val bk = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_BUCKET)
        val ak = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ACCESS_KEY)
        val sk = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_SECRET_KEY)
        return !ep.isNullOrBlank() && !bk.isNullOrBlank() && !ak.isNullOrBlank() && !sk.isNullOrBlank()
    }

    private fun resolveAccountDetails(provider: CloudSyncProvider): Triple<String?, String?, String?> = when (provider) {
        CloudSyncProvider.GOOGLE_DRIVE -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_NAME),
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_EMAIL),
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_GDRIVE_PICTURE)
        )
        CloudSyncProvider.ONEDRIVE -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_NAME),
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_EMAIL),
            null
        )
        CloudSyncProvider.WEBDAV -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_WEBDAV_USER)?.ifBlank { null },
            null,
            null
        )
        CloudSyncProvider.S3_COMPATIBLE -> Triple(
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_BUCKET)?.ifBlank { null },
            AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ENDPOINT)?.ifBlank { null },
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
            localFolderPath = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_LOCAL_PATH).orEmpty(),
            oneDriveAccountName = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_NAME),
            oneDriveAccountEmail = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_ONEDRIVE_EMAIL),
            s3Endpoint = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ENDPOINT).orEmpty(),
            s3Bucket = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_BUCKET).orEmpty(),
            s3AccessKey = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_ACCESS_KEY).orEmpty(),
            s3Region = AppPreferenceManager.getStringSync(PREF_CLOUD_SYNC_S3_REGION) ?: "auto"
        )
    }

    private fun clearProviderCredentials(provider: CloudSyncProvider) {
        when (provider) {
            CloudSyncProvider.GOOGLE_DRIVE -> clearGoogleCredentials()
            CloudSyncProvider.ONEDRIVE -> clearOneDriveCredentials()
            CloudSyncProvider.WEBDAV -> clearWebDavCredentials()
            CloudSyncProvider.S3_COMPATIBLE -> clearS3Credentials()
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

    private fun clearOneDriveCredentials() {
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_ONEDRIVE_ACCESS_TOKEN)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_ONEDRIVE_REFRESH_TOKEN)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_ONEDRIVE_TOKEN_EXPIRY)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_ONEDRIVE_EMAIL)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_ONEDRIVE_NAME)
    }

    private fun clearS3Credentials() {
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_S3_ENDPOINT)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_S3_BUCKET)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_S3_ACCESS_KEY)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_S3_SECRET_KEY)
        AppPreferenceManager.deletePreferenceSync(PREF_CLOUD_SYNC_S3_REGION)
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
