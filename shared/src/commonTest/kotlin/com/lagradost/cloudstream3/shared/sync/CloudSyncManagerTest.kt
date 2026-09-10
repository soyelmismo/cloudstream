package com.lagradost.cloudstream3.shared.sync

import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.sync.manager.CloudSyncManager
import com.lagradost.cloudstream3.shared.sync.manager.CloudSyncProvider
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleDriveOAuth
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleOAuthToken
import com.lagradost.cloudstream3.shared.sync.oauth.GoogleUserInfo
import com.lagradost.cloudstream3.shared.sync.oauth.OneDriveOAuth
import com.lagradost.cloudstream3.shared.sync.oauth.OneDriveOAuthToken
import com.lagradost.cloudstream3.shared.sync.oauth.OneDriveUserInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryAppPreferenceRepository : AppPreferenceRepository {
    private val store = mutableMapOf<String, String>()
    private val flows = mutableMapOf<String, MutableStateFlow<String?>>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getString(key: String, defaultValue: String?): String? = getStringSync(key, defaultValue)
    override fun getStringFlow(key: String): Flow<String?> = flows.getOrPut(key) { MutableStateFlow(store[key]) }
    override suspend fun setString(key: String, value: String) = setStringSync(key, value)
    override suspend fun getInt(key: String, defaultValue: Int): Int = getIntSync(key, defaultValue)
    override suspend fun setInt(key: String, value: Int) = setIntSync(key, value)
    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = getBooleanSync(key, defaultValue)
    override suspend fun setBoolean(key: String, value: Boolean) = setBooleanSync(key, value)
    override suspend fun getStringSet(key: String, defaultValue: Set<String>?): ImmutableSet<String>? = getStringSetSync(key, defaultValue)
    override suspend fun setStringSet(key: String, value: Set<String>) = setStringSetSync(key, value)
    override suspend fun getKeys(prefix: String): ImmutableList<String> = getKeysSync(prefix)
    override suspend fun removeKeys(prefix: String): Int = removeKeysSync(prefix)
    override suspend fun deletePreference(key: String) = deletePreferenceSync(key)
    override suspend fun clearAll() = store.clear()

    override fun getStringSync(key: String, defaultValue: String?): String? = store[key] ?: defaultValue
    override fun getIntSync(key: String, defaultValue: Int): Int = store[key]?.toIntOrNull() ?: defaultValue
    override fun getBooleanSync(key: String, defaultValue: Boolean): Boolean = store[key]?.toBooleanStrictOrNull() ?: defaultValue
    override fun getStringSetSync(key: String, defaultValue: Set<String>?): ImmutableSet<String>? {
        val raw = store[key] ?: return defaultValue?.toImmutableSet()
        return runCatching { json.decodeFromString<Set<String>>(raw).toImmutableSet() }.getOrDefault(defaultValue?.toImmutableSet())
    }
    override fun setStringSync(key: String, value: String) {
        store[key] = value
        flows.getOrPut(key) { MutableStateFlow(null) }.value = value
    }
    override fun setIntSync(key: String, value: Int) = setStringSync(key, value.toString())
    override fun setBooleanSync(key: String, value: Boolean) = setStringSync(key, value.toString())
    override fun setStringSetSync(key: String, value: Set<String>) = setStringSync(key, json.encodeToString(value))
    override fun deletePreferenceSync(key: String) {
        store.remove(key)
        flows[key]?.value = null
    }
    override fun getKeysSync(prefix: String): ImmutableList<String> =
        store.keys.filter { it.startsWith(prefix) }.toImmutableList()
    override fun removeKeysSync(prefix: String): Int {
        val matching = store.keys.filter { it.startsWith(prefix) }.toList()
        matching.forEach { store.remove(it) }
        return matching.size
    }
    override fun getAllSync(): ImmutableMap<String, String> = store.toMap().toImmutableMap()
}

class CloudSyncManagerTest {

    @BeforeTest
    fun setUp() {
        AppPreferenceManager.init(MemoryAppPreferenceRepository())
        CloudSyncManager.refreshState()
    }

    @Test
    fun testGoogleDriveOAuthPkceVerifierAndChallenge() {
        val verifier = GoogleDriveOAuth.generateCodeVerifier()
        assertEquals(64, verifier.length)
        val validChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~".toSet()
        assertTrue(verifier.all { it in validChars })

        val challenge = GoogleDriveOAuth.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotBlank())
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertFalse(challenge.contains("="))
    }

    @Test
    fun testBuildAuthorizationUrl() {
        val url = GoogleDriveOAuth.buildAuthorizationUrl(
            clientId = "test-client-id",
            codeChallenge = "test-challenge",
            redirectUri = "http://127.0.0.1:8080"
        )
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("code_challenge=test-challenge"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun testSanitizeAuthCodeVariants() {
        val rawCode = "4/0AdLIrX"
        assertEquals(rawCode, GoogleDriveOAuth.sanitizeAuthCode(rawCode))

        val redirectHttp = "http://127.0.0.1:8080/?code=4%2F0AdLIrX&scope=drive"
        assertEquals("4/0AdLIrX", GoogleDriveOAuth.sanitizeAuthCode(redirectHttp))

        val redirectCustomScheme = "cloudstream://oauth2/google?code=4%2F0AdLIrX#state=123"
        assertEquals("4/0AdLIrX", GoogleDriveOAuth.sanitizeAuthCode(redirectCustomScheme))
    }

    @Test
    fun testCloudSyncInitialState() {
        val state = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.NONE, state.provider)
        assertFalse(state.isConnected)
        assertNull(state.accountName)
    }

    @Test
    fun testConfigureWebDav() {
        CloudSyncManager.configureWebDav(
            url = "https://dav.example.com",
            username = "testuser",
            pass = "secret"
        )
        val state = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.WEBDAV, state.provider)
        assertTrue(state.isConnected)
        assertEquals("testuser", state.accountName)
        assertEquals("https://dav.example.com", state.webDavUrl)
    }

    @Test
    fun testConfigureLocalFolder() {
        CloudSyncManager.configureLocalFolder("/tmp/cloudstream_sync")
        val state = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.LOCAL_FOLDER, state.provider)
        assertTrue(state.isConnected)
        assertEquals("/tmp/cloudstream_sync", state.localFolderPath)
    }

    @Test
    fun testConfigureGoogleDriveAndDisconnect() {
        val token = GoogleOAuthToken(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresIn = 3600L
        )
        val userInfo = GoogleUserInfo(
            id = "google-123",
            email = "user@gmail.com",
            name = "Test User"
        )
        CloudSyncManager.configureGoogleDrive(token, userInfo)
        val state = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.GOOGLE_DRIVE, state.provider)
        assertTrue(state.isConnected)
        assertEquals("Test User", state.accountName)
        assertEquals("user@gmail.com", state.accountEmail)

        CloudSyncManager.disconnectProvider(clearCredentials = true)
        val disconnectedState = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.NONE, disconnectedState.provider)
        assertFalse(disconnectedState.isConnected)
        assertNull(disconnectedState.accountName)
    }

    @Test
    fun testOneDriveOAuthPkceAndUrl() {
        val verifier = OneDriveOAuth.generateCodeVerifier()
        assertEquals(64, verifier.length)
        val validChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~".toSet()
        assertTrue(verifier.all { it in validChars })

        val challenge = OneDriveOAuth.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotBlank())
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertFalse(challenge.contains("="))

        val url = OneDriveOAuth.buildAuthorizationUrl(codeChallenge = challenge)
        assertTrue(url.startsWith(OneDriveOAuth.AUTH_URL))
        assertTrue(url.contains("client_id=${OneDriveOAuth.DEFAULT_CLIENT_ID}"))
        assertTrue(url.contains("code_challenge=$challenge"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun testOneDriveSanitizeAuthCode() {
        val raw = "M.C12345"
        assertEquals(raw, OneDriveOAuth.sanitizeAuthCode(raw))

        val redirect = "http://localhost:53682/?code=M.C12345&state=xyz"
        assertEquals("M.C12345", OneDriveOAuth.sanitizeAuthCode(redirect))
    }

    @Test
    fun testConfigureOneDriveAndDisconnect() {
        val token = OneDriveOAuthToken(
            accessToken = "onedrive-access-token",
            refreshToken = "onedrive-refresh-token",
            expiresIn = 3600L
        )
        val userInfo = OneDriveUserInfo(
            id = "ms-123",
            displayName = "MS User",
            userPrincipalName = "msuser@outlook.com"
        )
        CloudSyncManager.configureOneDrive(token, userInfo)
        val state = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.ONEDRIVE, state.provider)
        assertTrue(state.isConnected)
        assertEquals("MS User", state.accountName)
        assertEquals("msuser@outlook.com", state.accountEmail)
        assertEquals("MS User", state.oneDriveAccountName)
        assertEquals("msuser@outlook.com", state.oneDriveAccountEmail)

        CloudSyncManager.disconnectProvider(clearCredentials = true)
        val disconnected = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.NONE, disconnected.provider)
        assertFalse(disconnected.isConnected)
        assertNull(disconnected.oneDriveAccountName)
    }

    @Test
    fun testConfigureS3AndDisconnect() {
        CloudSyncManager.configureS3(
            endpoint = "https://my-account.r2.cloudflarestorage.com",
            bucket = "my-bucket",
            accessKey = "AKIAEXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "auto"
        )
        val state = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.S3_COMPATIBLE, state.provider)
        assertTrue(state.isConnected)
        assertEquals("my-bucket", state.accountName)
        assertEquals("https://my-account.r2.cloudflarestorage.com", state.accountEmail)
        assertEquals("https://my-account.r2.cloudflarestorage.com", state.s3Endpoint)
        assertEquals("my-bucket", state.s3Bucket)
        assertEquals("AKIAEXAMPLE", state.s3AccessKey)

        CloudSyncManager.disconnectProvider(clearCredentials = true)
        val disconnected = CloudSyncManager.syncState.value
        assertEquals(CloudSyncProvider.NONE, disconnected.provider)
        assertFalse(disconnected.isConnected)
        assertEquals("", disconnected.s3Endpoint)
    }

    @Test
    fun testAutoSyncToggle() {
        assertFalse(CloudSyncManager.syncState.value.autoSyncEnabled)
        CloudSyncManager.setAutoSyncEnabled(true)
        assertTrue(CloudSyncManager.syncState.value.autoSyncEnabled)
    }
}
