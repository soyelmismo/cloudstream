package com.lagradost.cloudstream3.shared.viewmodels.settings

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.backup.BackupCategory
import com.lagradost.cloudstream3.shared.backup.BackupManager
import com.lagradost.cloudstream3.shared.backup.BackupRestoreResult
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeAppPreferenceRepository : AppPreferenceRepository {
    private val data = mutableMapOf<String, String>()
    private val flows = mutableMapOf<String, MutableStateFlow<String?>>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun getString(key: String, defaultValue: String?): String? {
        return data[key] ?: defaultValue
    }

    override fun getStringFlow(key: String): Flow<String?> {
        return flows.getOrPut(key) { MutableStateFlow(data[key]) }
    }

    override suspend fun setString(key: String, value: String) {
        data[key] = value
        flows.getOrPut(key) { MutableStateFlow(null) }.value = value
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return data[key]?.toIntOrNull() ?: defaultValue
    }

    override suspend fun setInt(key: String, value: Int) {
        setString(key, value.toString())
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val str = data[key] ?: return defaultValue
        return str.toBooleanStrictOrNull() ?: (str.toIntOrNull()?.let { it != 0 } ?: defaultValue)
    }

    override suspend fun setBoolean(key: String, value: Boolean) {
        setString(key, value.toString())
    }

    override suspend fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        val raw = data[key] ?: return defaultValue
        return parseStringSet(raw, defaultValue)
    }

    override suspend fun setStringSet(key: String, value: Set<String>) {
        setString(key, json.encodeToString(value))
    }

    override suspend fun getKeys(prefix: String): List<String> {
        return data.keys.filter { it.startsWith(prefix) }.toList()
    }

    override suspend fun removeKeys(prefix: String): Int {
        val matching = data.keys.filter { it.startsWith(prefix) }.toList()
        matching.forEach { deletePreference(it) }
        return matching.size
    }

    override suspend fun deletePreference(key: String) {
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
    }

    override suspend fun clearAll() {
        data.clear()
        flows.values.forEach { it.value = null }
    }

    override fun getStringSync(key: String, defaultValue: String?): String? {
        return data[key] ?: defaultValue
    }

    override fun getIntSync(key: String, defaultValue: Int): Int {
        return data[key]?.toIntOrNull() ?: defaultValue
    }

    override fun getBooleanSync(key: String, defaultValue: Boolean): Boolean {
        val str = data[key] ?: return defaultValue
        return str.toBooleanStrictOrNull() ?: (str.toIntOrNull()?.let { it != 0 } ?: defaultValue)
    }

    override fun getStringSetSync(key: String, defaultValue: Set<String>?): Set<String>? {
        val raw = data[key] ?: return defaultValue
        return parseStringSet(raw, defaultValue)
    }

    override fun setStringSync(key: String, value: String) {
        data[key] = value
        flows.getOrPut(key) { MutableStateFlow(null) }.value = value
    }

    override fun setIntSync(key: String, value: Int) {
        setStringSync(key, value.toString())
    }

    override fun setBooleanSync(key: String, value: Boolean) {
        setStringSync(key, value.toString())
    }

    override fun setStringSetSync(key: String, value: Set<String>) {
        setStringSync(key, json.encodeToString(value))
    }

    override fun deletePreferenceSync(key: String) {
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
    }

    override fun getKeysSync(prefix: String): List<String> =
        data.keys.filter { it.startsWith(prefix) }.toList()

    override fun removeKeysSync(prefix: String): Int {
        val matching = data.keys.filter { it.startsWith(prefix) }.toList()
        matching.forEach { deletePreferenceSync(it) }
        return matching.size
    }

    override fun getAllSync(): Map<String, String> =
        data.toMap()

    private fun parseStringSet(raw: String, defaultValue: Set<String>?): Set<String>? {
        return try {
            json.decodeFromString<Set<String>>(raw)
        } catch (_: Throwable) {
            try {
                json.decodeFromString<List<String>>(raw).toSet()
            } catch (_: Throwable) {
                raw.removePrefix("[").removeSuffix("]")
                    .split(",")
                    .map { it.trim().trim('"', '\'') }
                    .filter { it.isNotBlank() }
                    .toSet()
                    .ifEmpty { defaultValue }
            }
        }
    }
}

class FakeBackupManager(
    private val preferenceRepository: AppPreferenceRepository? = null
) : BackupManager {
    var lastCreatedCategories: Set<BackupCategory>? = null
    var lastRestoredJson: String? = null
    var lastRestoredCategories: Set<BackupCategory>? = null
    var shouldFail: Boolean = false

    override suspend fun createBackup(categories: Set<BackupCategory>): String {
        lastCreatedCategories = categories
        return """{"version":2,"categories":["SETTINGS"],"settings":{"app_theme_key":"Amoled","locale_key":"es","quality_pref_key":"1080"}}"""
    }

    override suspend fun restoreBackup(
        jsonContent: String,
        selectedCategories: Set<BackupCategory>?
    ): BackupRestoreResult {
        lastRestoredJson = jsonContent
        lastRestoredCategories = selectedCategories
        if (shouldFail) return BackupRestoreResult.Error("Failed to restore backup")
        preferenceRepository?.setString(AppSettingsViewModel.KEY_APP_THEME, "Amoled")
        preferenceRepository?.setString(AppSettingsViewModel.KEY_APP_LOCALE, "es")
        preferenceRepository?.setInt(AppSettingsViewModel.KEY_QUALITY_WIFI, 1080)
        return BackupRestoreResult.Success(
            restoredCategories = selectedCategories ?: setOf(BackupCategory.SETTINGS),
            itemsCount = 3
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsViewModelTest {

    @Test
    fun testInitialLoadAndDefaults() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )

        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(AppTheme.SYSTEM, state.theme)
        assertTrue(state.isDarkMode)
        assertEquals(DohProvider.NONE, state.dohProvider)
        assertEquals(listOf("en"), state.preferredProviderLanguages)
        assertEquals("en", state.appLanguage)
        assertEquals(0, state.qualityWifi)
        assertEquals(0, state.qualityMobile)
        assertEquals(-1, state.softwareDecoding)
        assertEquals("UTF-8", state.subtitleEncoding)
        assertEquals(20f, state.subtitleStyle.fontSize)
    }

    @Test
    fun testSetTheme() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        viewModel.onEvent(AppSettingsEvent.SetTheme(AppTheme.DEFAULT))
        testScope.advanceUntilIdle()

        assertEquals(AppTheme.DEFAULT, viewModel.state.value.theme)
        assertEquals("Black", repository.getString(AppSettingsViewModel.KEY_APP_THEME))

        viewModel.onEvent(AppSettingsEvent.SetTheme(AppTheme.DRACULA))
        testScope.advanceUntilIdle()

        assertEquals(AppTheme.DRACULA, viewModel.state.value.theme)
        assertEquals("Dracula", repository.getString(AppSettingsViewModel.KEY_APP_THEME))
    }

    @Test
    fun testSetDarkMode() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        viewModel.onEvent(AppSettingsEvent.SetDarkMode(false))
        testScope.advanceUntilIdle()

        assertFalse(viewModel.state.value.isDarkMode)
        assertEquals("false", repository.getString(AppSettingsViewModel.KEY_DARK_MODE))
    }

    @Test
    fun testSetDohProvider() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        viewModel.onEvent(AppSettingsEvent.SetDohProvider(DohProvider.CLOUDFLARE))
        testScope.advanceUntilIdle()

        assertEquals(DohProvider.CLOUDFLARE, viewModel.state.value.dohProvider)
        assertEquals("2", repository.getString(AppSettingsViewModel.KEY_DOH_PROVIDER))

        viewModel.onEvent(AppSettingsEvent.SetDohProvider(DohProvider.QUAD9))
        testScope.advanceUntilIdle()

        assertEquals(DohProvider.QUAD9, viewModel.state.value.dohProvider)
        assertEquals("6", repository.getString(AppSettingsViewModel.KEY_DOH_PROVIDER))
    }

    @Test
    fun testSetProviderLanguagesAndAppLocale() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val langs = listOf("es", "en", "ja")
        viewModel.onEvent(AppSettingsEvent.SetLanguage(langs))
        testScope.advanceUntilIdle()

        assertEquals(langs, viewModel.state.value.preferredProviderLanguages)

        viewModel.onEvent(AppSettingsEvent.SetAppLanguage("es"))
        testScope.advanceUntilIdle()

        assertEquals("es", viewModel.state.value.appLanguage)
        assertEquals("es", repository.getString(AppSettingsViewModel.KEY_APP_LOCALE))
    }

    @Test
    fun testSetSubtitleStyle() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val customStyle = SubtitleStyle(
            fontSize = 24f,
            textColor = 0xFFFFFF00L,
            backgroundColor = 0x80000000L,
            edgeType = SubtitleEdgeType.DROP_SHADOW,
            bold = true,
            italic = false,
            uppercase = true,
            encoding = "ISO-8859-1"
        )

        viewModel.onEvent(AppSettingsEvent.SetDefaultSubtitleStyle(customStyle))
        testScope.advanceUntilIdle()

        assertEquals(customStyle, viewModel.state.value.subtitleStyle)
        assertEquals("ISO-8859-1", viewModel.state.value.subtitleEncoding)
        assertEquals("ISO-8859-1", repository.getString(AppSettingsViewModel.KEY_SUBTITLE_ENCODING))
    }

    @Test
    fun testVideoQualityAndDecodingPreferences() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        // Set WiFi Quality
        viewModel.onEvent(AppSettingsEvent.SetQualityWifi(1080))
        testScope.advanceUntilIdle()
        assertEquals(1080, viewModel.state.value.qualityWifi)
        assertEquals(1080, repository.getInt(AppSettingsViewModel.KEY_QUALITY_WIFI))

        // Set Mobile Quality
        viewModel.onEvent(AppSettingsEvent.SetQualityMobile(720))
        testScope.advanceUntilIdle()
        assertEquals(720, viewModel.state.value.qualityMobile)
        assertEquals(720, repository.getInt(AppSettingsViewModel.KEY_QUALITY_MOBILE))

        // Set Software Decoding mode
        viewModel.onEvent(AppSettingsEvent.SetSoftwareDecoding(1))
        testScope.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.softwareDecoding)
        assertEquals(1, repository.getInt(AppSettingsViewModel.KEY_SOFTWARE_DECODING))

        // Set Subtitle Encoding
        viewModel.onEvent(AppSettingsEvent.SetSubtitleEncoding("Windows-1252"))
        testScope.advanceUntilIdle()
        assertEquals("Windows-1252", viewModel.state.value.subtitleEncoding)
        assertEquals("Windows-1252", viewModel.state.value.subtitleStyle.encoding)
        assertEquals("Windows-1252", repository.getString(AppSettingsViewModel.KEY_SUBTITLE_ENCODING))
    }

    @Test
    fun testResetToDefaults() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        viewModel.onEvent(AppSettingsEvent.SetTheme(AppTheme.DEFAULT))
        viewModel.onEvent(AppSettingsEvent.SetDohProvider(DohProvider.ADGUARD))
        viewModel.onEvent(AppSettingsEvent.SetQualityWifi(2160))
        viewModel.onEvent(AppSettingsEvent.SetQualityMobile(480))
        viewModel.onEvent(AppSettingsEvent.SetSoftwareDecoding(0))
        viewModel.onEvent(AppSettingsEvent.SetSubtitleEncoding("GBK"))
        testScope.advanceUntilIdle()

        assertEquals(AppTheme.DEFAULT, viewModel.state.value.theme)
        assertEquals(DohProvider.ADGUARD, viewModel.state.value.dohProvider)
        assertEquals(2160, viewModel.state.value.qualityWifi)
        assertEquals(480, viewModel.state.value.qualityMobile)
        assertEquals(0, viewModel.state.value.softwareDecoding)
        assertEquals("GBK", viewModel.state.value.subtitleEncoding)

        viewModel.onEvent(AppSettingsEvent.ResetToDefaults)
        testScope.advanceUntilIdle()

        assertEquals(AppTheme.SYSTEM, viewModel.state.value.theme)
        assertEquals(DohProvider.NONE, viewModel.state.value.dohProvider)
        assertEquals(0, viewModel.state.value.qualityWifi)
        assertEquals(0, viewModel.state.value.qualityMobile)
        assertEquals(-1, viewModel.state.value.softwareDecoding)
        assertEquals("UTF-8", viewModel.state.value.subtitleEncoding)
        assertTrue(viewModel.state.value.syncWatchProgress)
        assertTrue(viewModel.state.value.syncScores)
        assertFalse(viewModel.state.value.syncWifiOnly)
        assertFalse(viewModel.state.value.skipStartupAccountSelect)
    }

    @Test
    fun testSyncPreferences() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        // Initial default sync preferences
        assertTrue(viewModel.state.value.syncWatchProgress)
        assertTrue(viewModel.state.value.syncScores)
        assertFalse(viewModel.state.value.syncWifiOnly)
        assertFalse(viewModel.state.value.skipStartupAccountSelect)

        // Set Sync Watch Progress
        viewModel.onEvent(AppSettingsEvent.SetSyncWatchProgress(false))
        testScope.advanceUntilIdle()
        assertFalse(viewModel.state.value.syncWatchProgress)
        assertEquals("false", repository.getString(AppSettingsViewModel.KEY_SYNC_WATCH_PROGRESS))

        // Set Sync Scores
        viewModel.onEvent(AppSettingsEvent.SetSyncScores(false))
        testScope.advanceUntilIdle()
        assertFalse(viewModel.state.value.syncScores)
        assertEquals("false", repository.getString(AppSettingsViewModel.KEY_SYNC_SCORES))

        // Set Sync WiFi Only
        viewModel.onEvent(AppSettingsEvent.SetSyncWifiOnly(true))
        testScope.advanceUntilIdle()
        assertTrue(viewModel.state.value.syncWifiOnly)
        assertEquals("true", repository.getString(AppSettingsViewModel.KEY_SYNC_WIFI_ONLY))

        // Set Skip Startup Account Select
        viewModel.onEvent(AppSettingsEvent.SetSkipStartupAccountSelect(true))
        testScope.advanceUntilIdle()
        assertTrue(viewModel.state.value.skipStartupAccountSelect)
        assertEquals("true", repository.getString(AppSettingsViewModel.KEY_SKIP_STARTUP_ACCOUNT_SELECT))

        // Reset to Defaults restores sync preferences
        viewModel.onEvent(AppSettingsEvent.ResetToDefaults)
        testScope.advanceUntilIdle()
        assertTrue(viewModel.state.value.syncWatchProgress)
        assertTrue(viewModel.state.value.syncScores)
        assertFalse(viewModel.state.value.syncWifiOnly)
        assertFalse(viewModel.state.value.skipStartupAccountSelect)
    }

    @Test
    fun testAccountAndAuthEvents() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()
        AppPreferenceManager.init(repository)

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        // Active accounts reflect AccountManager.accountsState
        assertEquals(AccountManager.accountsState.value, viewModel.state.value.activeAuthAccounts)

        // Clear error event
        viewModel.onEvent(AppSettingsEvent.ClearError)
        testScope.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun testAppPreferenceManagerTypedAndSyncHelpers() = runTest {
        val repository = FakeAppPreferenceRepository()
        AppPreferenceManager.init(repository)

        // Suspending tests
        AppPreferenceManager.setInt("pref_int", 42)
        assertEquals(42, AppPreferenceManager.getInt("pref_int"))

        AppPreferenceManager.setBoolean("pref_bool", true)
        assertTrue(AppPreferenceManager.getBoolean("pref_bool"))

        AppPreferenceManager.setStringSet("pref_set", setOf("item1", "item2", "item3"))
        assertEquals(setOf("item1", "item2", "item3"), AppPreferenceManager.getStringSet("pref_set"))

        val keys = repository.getKeys("pref_")
        assertEquals(3, keys.size)

        // Sync tests
        AppPreferenceManager.setIntSync("sync_int", 99)
        assertEquals(99, AppPreferenceManager.getIntSync("sync_int"))

        AppPreferenceManager.setBooleanSync("sync_bool", false)
        assertFalse(AppPreferenceManager.getBooleanSync("sync_bool"))

        AppPreferenceManager.setStringSetSync("sync_set", setOf("alpha", "beta"))
        assertEquals(setOf("alpha", "beta"), AppPreferenceManager.getStringSetSync("sync_set"))

        val all = AppPreferenceManager.getAllSync()
        assertTrue(all.containsKey("sync_int"))
        assertTrue(all.containsKey("sync_bool"))
        assertTrue(all.containsKey("sync_set"))

        val removed = AppPreferenceManager.removeKeysSync("sync_")
        assertEquals(3, removed)
        assertNull(AppPreferenceManager.getStringSync("sync_int"))
    }

    @Test
    fun testBackupStateAndInitialCategories() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isBackingUp)
        assertFalse(state.isRestoring)
        assertNull(state.backupSuccessRes)
        assertNull(state.backupErrorRes)
        assertEquals(BackupCategory.entries.toSet(), state.availableBackupCategories)
    }

    @Test
    fun testCreateAndRestoreBackup() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()
        val backupManager = FakeBackupManager(preferenceRepository = repository)

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            backupManager = backupManager,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        // Set non-default preferences
        viewModel.onEvent(AppSettingsEvent.SetTheme(AppTheme.AMOLED))
        viewModel.onEvent(AppSettingsEvent.SetAppLanguage("es"))
        viewModel.onEvent(AppSettingsEvent.SetQualityWifi(1080))
        testScope.advanceUntilIdle()

        assertEquals(AppTheme.AMOLED, viewModel.state.value.theme)
        assertEquals("es", viewModel.state.value.appLanguage)

        // Create backup
        viewModel.onEvent(AppSettingsEvent.CreateBackup(setOf(BackupCategory.SETTINGS)))
        testScope.advanceUntilIdle()

        assertEquals(Res.string.backup_export_success_msg, viewModel.state.value.backupSuccessRes)
        assertFalse(viewModel.state.value.isBackingUp)
        assertEquals(setOf(BackupCategory.SETTINGS), backupManager.lastCreatedCategories)

        // Reset to defaults
        viewModel.onEvent(AppSettingsEvent.ResetToDefaults)
        testScope.advanceUntilIdle()
        assertEquals(AppTheme.SYSTEM, viewModel.state.value.theme)
        assertEquals("en", viewModel.state.value.appLanguage)

        // Restore backup
        val testBackupPayload = """{"version":2,"categories":["SETTINGS"],"settings":{"app_theme_key":"Amoled","locale_key":"es","quality_pref_key":"1080"}}"""
        viewModel.onEvent(AppSettingsEvent.RestoreBackup(testBackupPayload))
        testScope.advanceUntilIdle()

        assertEquals(Res.string.backup_restore_success_msg, viewModel.state.value.backupSuccessRes)
        assertFalse(viewModel.state.value.isRestoring)
        assertEquals(AppTheme.AMOLED, viewModel.state.value.theme)
        assertEquals("es", viewModel.state.value.appLanguage)
        assertEquals(1080, viewModel.state.value.qualityWifi)

        // Clear backup message
        viewModel.onEvent(AppSettingsEvent.ClearBackupMessage)
        testScope.advanceUntilIdle()
        assertNull(viewModel.state.value.backupSuccessRes)
        assertNull(viewModel.state.value.backupErrorRes)
    }

    @Test
    fun testRestoreBackupFailure() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = FakeAppPreferenceRepository()
        val backupManager = FakeBackupManager(preferenceRepository = repository).apply {
            shouldFail = true
        }

        val viewModel = AppSettingsViewModel(
            preferenceRepository = repository,
            backupManager = backupManager,
            coroutineScope = testScope
        )
        testScope.advanceUntilIdle()

        viewModel.onEvent(AppSettingsEvent.RestoreBackup("invalid_json"))
        testScope.advanceUntilIdle()

        assertFalse(viewModel.state.value.isRestoring)
        assertNull(viewModel.state.value.backupSuccessRes)
        assertEquals(Res.string.backup_restore_error_invalid, viewModel.state.value.backupErrorRes)

        viewModel.onEvent(AppSettingsEvent.ClearBackupMessage)
        testScope.advanceUntilIdle()
        assertNull(viewModel.state.value.backupErrorRes)
    }
}


