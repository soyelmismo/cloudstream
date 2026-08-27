package com.lagradost.cloudstream3.shared.viewmodels.settings

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.shared.backup.BackupCategory
import com.lagradost.cloudstream3.shared.backup.BackupManager
import com.lagradost.cloudstream3.shared.backup.BackupManagerImpl
import com.lagradost.cloudstream3.shared.backup.BackupRestoreResult
import com.lagradost.cloudstream3.shared.backup.PlatformFilePicker
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.shared.syncproviders.AuthPinData
import com.lagradost.cloudstream3.shared.syncproviders.AuthRepo
import com.lagradost.cloudstream3.shared.syncproviders.AuthUser
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import org.jetbrains.compose.resources.StringResource

/**
 * App visual theme families supported across all platforms.
 * Dark/Light variations are dynamically controlled by the application's dark mode preference.
 */
@Serializable
enum class AppTheme(val key: String, val displayNameRes: StringResource) {
    SYSTEM("System", Res.string.theme_system),
    DEFAULT("Black", Res.string.theme_default),
    AMOLED("Amoled", Res.string.theme_amoled),
    DRACULA("Dracula", Res.string.theme_dracula),
    LAVENDER("Lavender", Res.string.theme_lavender),
    SILENT_BLUE("SilentBlue", Res.string.theme_silent_blue);

    companion object {
        fun fromKey(key: String?): AppTheme {
            return when (key?.lowercase()) {
                "system", "monet", "materialyou" -> SYSTEM
                "black", "dark", "light", "default" -> DEFAULT
                "amoled", "amoledblack", "amoledlight" -> AMOLED
                "dracula" -> DRACULA
                "lavender" -> LAVENDER
                "silentblue" -> SILENT_BLUE
                else -> entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
            }
        }
    }
}

/**
 * DNS-over-HTTPS (DoH) providers supported by the application.
 */
@Serializable
enum class DohProvider(
    val id: Int,
    val displayName: String,
    val url: String? = null,
    val ips: List<String> = emptyList()
) {
    NONE(0, "None (System Default)"),
    GOOGLE(1, "Google", "https://dns.google/dns-query", listOf("8.8.4.4", "8.8.8.8")),
    CLOUDFLARE(2, "Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    ADGUARD(4, "AdGuard", "https://dns.adguard.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    DNS_WATCH(5, "DNS.WATCH", "https://resolver2.dns.watch/dns-query", listOf("84.200.69.80", "84.200.70.40")),
    QUAD9(6, "Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    DNS_SB(7, "DNS.SB", "https://doh.dns.sb/dns-query", listOf("185.222.222.222", "45.11.45.11")),
    CANADIAN_SHIELD(8, "Canadian Shield", "https://private.canadianshield.cira.ca/dns-query", listOf("149.112.121.10", "149.112.122.10"));

    companion object {
        fun fromId(id: Int): DohProvider =
            entries.firstOrNull { it.id == id } ?: NONE

        fun fromString(value: String?): DohProvider {
            if (value == null) return NONE
            val intId = value.toIntOrNull()
            return if (intId != null) {
                fromId(intId)
            } else {
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
            }
        }
    }
}

/**
 * Subtitle edge/border rendering styles.
 */
@Serializable
enum class SubtitleEdgeType(val id: Int, val displayName: String) {
    NONE(0, "None"),
    OUTLINE(1, "Outline"),
    DROP_SHADOW(2, "Drop Shadow"),
    RAISED(3, "Raised"),
    DEPRESSED(4, "Depressed");

    companion object {
        fun fromId(id: Int): SubtitleEdgeType =
            entries.firstOrNull { it.id == id } ?: OUTLINE
    }
}

/**
 * Options and style configuration for video subtitles.
 */
@Serializable
data class SubtitleStyle(
    val fontSize: Float = 20f,
    val textColor: Long = 0xFFFFFFFFL,
    val backgroundColor: Long = 0x00000000L,
    val windowColor: Long = 0x00000000L,
    val edgeType: SubtitleEdgeType = SubtitleEdgeType.OUTLINE,
    val edgeColor: Long = 0xFF000000L,
    val outlineWidth: Float = 1f,
    val backgroundOpacity: Float = 0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val uppercase: Boolean = false,
    val elevation: Int = 20,
    val removeCaptions: Boolean = false,
    val removeBloat: Boolean = true,
    val autoSelectSubtitles: Boolean = true,
    val autoDownloadSubtitles: Boolean = false,
    val encoding: String = "UTF-8",
    val fontFilePath: String? = null
)

/**
 * Immutable UI state for App Settings in MVI.
 */
@Serializable
data class AppSettingsState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val isDarkMode: Boolean = true,
    val preferredProviderLanguages: List<String> = listOf("en"),
    val dohProvider: DohProvider = DohProvider.NONE,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val appLanguage: String = "en",
    val qualityWifi: Int = 0,
    val qualityMobile: Int = 0,
    val softwareDecoding: Int = -1,
    val subtitleEncoding: String = "UTF-8",
    val syncWatchProgress: Boolean = true,
    val syncScores: Boolean = true,
    val syncWifiOnly: Boolean = false,
    val skipStartupAccountSelect: Boolean = false,
    val showSourcesOnPlay: Boolean = false,
    val activeAuthAccounts: Map<String, AuthData?> = emptyMap(),
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    @Transient
    val backupSuccessRes: StringResource? = null,
    @Transient
    val backupErrorRes: StringResource? = null,
    val availableBackupCategories: Set<BackupCategory> = BackupCategory.entries.toSet(),
    val isLoading: Boolean = false,
    @Transient
    val error: UiText? = null
) : UiState

/**
 * Sealed events/intents for App Settings in MVI.
 */
sealed class AppSettingsEvent : UiEvent {
    data object LoadSettings : AppSettingsEvent()
    data class SetTheme(val theme: AppTheme) : AppSettingsEvent()
    data class SetDarkMode(val enabled: Boolean) : AppSettingsEvent()
    data class SetLanguage(val languages: List<String>) : AppSettingsEvent()
    data class SetAppLanguage(val languageCode: String) : AppSettingsEvent()
    data class SetDohProvider(val provider: DohProvider) : AppSettingsEvent()
    data class SetDefaultSubtitleStyle(val style: SubtitleStyle) : AppSettingsEvent()
    data class SetQualityWifi(val quality: Int) : AppSettingsEvent()
    data class SetQualityMobile(val quality: Int) : AppSettingsEvent()
    data class SetSoftwareDecoding(val mode: Int) : AppSettingsEvent()
    data class SetSubtitleEncoding(val encoding: String) : AppSettingsEvent()
    data class SetSyncWatchProgress(val enabled: Boolean) : AppSettingsEvent()
    data class SetSyncScores(val enabled: Boolean) : AppSettingsEvent()
    data class SetSyncWifiOnly(val enabled: Boolean) : AppSettingsEvent()
    data class SetSkipStartupAccountSelect(val enabled: Boolean) : AppSettingsEvent()
    data class SetShowSourcesOnPlay(val enabled: Boolean) : AppSettingsEvent()
    data class LoginInApp(val api: AuthRepo, val form: AuthLoginResponse) : AppSettingsEvent()
    data class LoginPin(val api: AuthRepo, val pinData: AuthPinData) : AppSettingsEvent()
    data class LogoutAccount(val api: AuthRepo, val user: AuthUser) : AppSettingsEvent()
    data class SwitchActiveAccount(val api: AuthRepo, val accountId: Int) : AppSettingsEvent()
    data class StartOAuthLogin(val api: AuthRepo) : AppSettingsEvent()
    data class CreateBackup(val categories: Set<BackupCategory>) : AppSettingsEvent()
    data class RestoreBackup(val jsonContent: String, val categories: Set<BackupCategory>? = null) : AppSettingsEvent()
    data class ExportBackupWithPicker(val categories: Set<BackupCategory> = BackupCategory.entries.toSet()) : AppSettingsEvent()
    data object ImportBackupWithPicker : AppSettingsEvent()
    data object ClearBackupMessage : AppSettingsEvent()
    data object ResetToDefaults : AppSettingsEvent()
    data object ClearError : AppSettingsEvent()
}

/**
 * Cross-platform MVI ViewModel for Application Settings connected to AppPreferenceRepository.
 */
class AppSettingsViewModel(
    private val preferenceRepository: AppPreferenceRepository,
    coroutineScope: CoroutineScope? = null,
    backupManager: BackupManager? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) : MviViewModel<AppSettingsState, AppSettingsEvent>(
    initialState = AppSettingsState(isLoading = true),
    coroutineScope = coroutineScope
) {
    private val backupManager: BackupManager = backupManager ?: run {
        try {
            BackupManagerImpl(
                database = DatabaseDriverFactory.getDatabase(),
                preferenceRepo = preferenceRepository
            )
        } catch (_: Throwable) {
            object : BackupManager {
                override suspend fun createBackup(categories: Set<BackupCategory>): String = "{}"
                override suspend fun restoreBackup(
                    jsonContent: String,
                    selectedCategories: Set<BackupCategory>?
                ): BackupRestoreResult = BackupRestoreResult.Success(emptySet(), 0)
            }
        }
    }

    constructor(
        preferenceRepository: AppPreferenceRepository,
        coroutineContext: CoroutineContext
    ) : this(preferenceRepository, CoroutineScope(coroutineContext))

    constructor(
        preferenceRepository: AppPreferenceRepository,
        backupManager: BackupManager,
        coroutineScope: CoroutineScope? = null
    ) : this(
        preferenceRepository = preferenceRepository,
        coroutineScope = coroutineScope,
        backupManager = backupManager,
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    )

    companion object {
        const val KEY_APP_THEME = "app_theme_key"
        const val KEY_DARK_MODE = "dark_mode_key"
        const val KEY_PROVIDER_LANG = "provider_lang_key"
        const val KEY_DOH_PROVIDER = "dns_pref"
        const val KEY_SUBTITLE_SETTINGS = "subtitle_settings"
        const val KEY_APP_LOCALE = "locale_key"
        const val KEY_QUALITY_WIFI = "quality_pref_key"
        const val KEY_QUALITY_MOBILE = "quality_pref_mobile_data_key"
        const val KEY_SOFTWARE_DECODING = "software_decoding_key"
        const val KEY_SUBTITLE_ENCODING = "subtitles_encoding_key"
        const val KEY_SYNC_WATCH_PROGRESS = "sync_watch_progress"
        const val KEY_SYNC_SCORES = "sync_scores"
        const val KEY_SYNC_WIFI_ONLY = "sync_wifi_only"
        const val KEY_SKIP_STARTUP_ACCOUNT_SELECT = "skip_startup_account_select_key"
        const val KEY_SHOW_SOURCES_ON_PLAY = "show_sources_on_play_key"
    }

    init {
        handleEvent(AppSettingsEvent.LoadSettings)
        observePreferenceChanges()
    }

    override fun handleEvent(event: AppSettingsEvent) {
        when (event) {
            is AppSettingsEvent.LoadSettings -> loadSettings()
            is AppSettingsEvent.SetTheme -> setTheme(event.theme)
            is AppSettingsEvent.SetDarkMode -> setDarkMode(event.enabled)
            is AppSettingsEvent.SetLanguage -> setProviderLanguages(event.languages)
            is AppSettingsEvent.SetAppLanguage -> setAppLanguage(event.languageCode)
            is AppSettingsEvent.SetDohProvider -> setDohProvider(event.provider)
            is AppSettingsEvent.SetDefaultSubtitleStyle -> setDefaultSubtitleStyle(event.style)
            is AppSettingsEvent.SetQualityWifi -> setQualityWifi(event.quality)
            is AppSettingsEvent.SetQualityMobile -> setQualityMobile(event.quality)
            is AppSettingsEvent.SetSoftwareDecoding -> setSoftwareDecoding(event.mode)
            is AppSettingsEvent.SetSubtitleEncoding -> setSubtitleEncoding(event.encoding)
            is AppSettingsEvent.SetSyncWatchProgress -> setSyncWatchProgress(event.enabled)
            is AppSettingsEvent.SetSyncScores -> setSyncScores(event.enabled)
            is AppSettingsEvent.SetSyncWifiOnly -> setSyncWifiOnly(event.enabled)
            is AppSettingsEvent.SetSkipStartupAccountSelect -> setSkipStartupAccountSelect(event.enabled)
            is AppSettingsEvent.SetShowSourcesOnPlay -> setShowSourcesOnPlay(event.enabled)
            is AppSettingsEvent.LoginInApp -> loginInApp(event.api, event.form)
            is AppSettingsEvent.LoginPin -> loginPin(event.api, event.pinData)
            is AppSettingsEvent.LogoutAccount -> logoutAccount(event.api, event.user)
            is AppSettingsEvent.SwitchActiveAccount -> switchActiveAccount(event.api, event.accountId)
            is AppSettingsEvent.StartOAuthLogin -> startOAuthLogin(event.api)
            is AppSettingsEvent.CreateBackup -> createBackup(event.categories)
            is AppSettingsEvent.RestoreBackup -> restoreBackup(event.jsonContent, event.categories)
            is AppSettingsEvent.ExportBackupWithPicker -> exportBackupWithPicker(event.categories)
            is AppSettingsEvent.ImportBackupWithPicker -> importBackupWithPicker()
            is AppSettingsEvent.ClearBackupMessage -> clearBackupMessage()
            is AppSettingsEvent.ResetToDefaults -> resetToDefaults()
            is AppSettingsEvent.ClearError -> updateState { copy(error = null) }
        }
    }

    private fun loadSettings() {
        launchSafeJob(
            key = "load_settings",
            onError = { t ->
                updateState {
                    copy(
                        isLoading = false,
                        error = t.message?.let { txt(it) } ?: txt(Res.string.settings_error_load_failed)
                    )
                }
            }
        ) {
            updateState { copy(isLoading = true, error = null) }

            val themeKey = preferenceRepository.getString(KEY_APP_THEME)
            val theme = AppTheme.fromKey(themeKey)

            val darkModeStr = preferenceRepository.getString(KEY_DARK_MODE)
            val isDarkMode = darkModeStr?.toBooleanStrictOrNull() ?: true

            val langStr = preferenceRepository.getString(KEY_PROVIDER_LANG)
            val providerLangs = parseStringList(langStr, default = listOf("en"))

            val dohStr = preferenceRepository.getString(KEY_DOH_PROVIDER)
            val dohProvider = DohProvider.fromString(dohStr)

            val subStr = preferenceRepository.getString(KEY_SUBTITLE_SETTINGS)
            val subtitleStyle = if (!subStr.isNullOrBlank()) {
                try {
                    json.decodeFromString<SubtitleStyle>(subStr)
                } catch (_: Throwable) {
                    SubtitleStyle()
                }
            } else {
                SubtitleStyle()
            }

            val appLocale = preferenceRepository.getString(KEY_APP_LOCALE, "en") ?: "en"
            val qualityWifi = preferenceRepository.getInt(KEY_QUALITY_WIFI, 0)
            val qualityMobile = preferenceRepository.getInt(KEY_QUALITY_MOBILE, 0)
            val softwareDecoding = preferenceRepository.getInt(KEY_SOFTWARE_DECODING, -1)
            val subtitleEncoding = preferenceRepository.getString(KEY_SUBTITLE_ENCODING, "UTF-8") ?: "UTF-8"

            val syncWatchProgress = preferenceRepository.getBoolean(KEY_SYNC_WATCH_PROGRESS, true)
            val syncScores = preferenceRepository.getBoolean(KEY_SYNC_SCORES, true)
            val syncWifiOnly = preferenceRepository.getBoolean(KEY_SYNC_WIFI_ONLY, false)
            val skipStartupAccountSelect = preferenceRepository.getBoolean(KEY_SKIP_STARTUP_ACCOUNT_SELECT, false)
            val showSourcesOnPlay = preferenceRepository.getBoolean(KEY_SHOW_SOURCES_ON_PLAY, false)
            val activeAuthAccounts = AccountManager.accountsState.value

            updateState {
                copy(
                    theme = theme,
                    isDarkMode = isDarkMode,
                    preferredProviderLanguages = providerLangs,
                    dohProvider = dohProvider,
                    subtitleStyle = subtitleStyle.copy(encoding = subtitleEncoding),
                    appLanguage = appLocale,
                    qualityWifi = qualityWifi,
                    qualityMobile = qualityMobile,
                    softwareDecoding = softwareDecoding,
                    subtitleEncoding = subtitleEncoding,
                    syncWatchProgress = syncWatchProgress,
                    syncScores = syncScores,
                    syncWifiOnly = syncWifiOnly,
                    skipStartupAccountSelect = skipStartupAccountSelect,
                    showSourcesOnPlay = showSourcesOnPlay,
                    activeAuthAccounts = activeAuthAccounts,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    private fun observePreference(key: String, onValue: (String) -> Unit) {
        preferenceRepository.getStringFlow(key)
            .onEach { value -> if (value != null) onValue(value) }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)
    }

    private fun observePreferenceChanges() {
        observePreference(KEY_APP_THEME) { themeKey ->
            updateState { copy(theme = AppTheme.fromKey(themeKey)) }
        }

        observePreference(KEY_DARK_MODE) { darkModeStr ->
            val isDark = darkModeStr.toBooleanStrictOrNull() ?: true
            updateState { copy(isDarkMode = isDark) }
        }

        observePreference(KEY_DOH_PROVIDER) { dohStr ->
            updateState { copy(dohProvider = DohProvider.fromString(dohStr)) }
        }

        observePreference(KEY_QUALITY_WIFI) { qualStr ->
            val qual = qualStr.toIntOrNull() ?: 0
            updateState { copy(qualityWifi = qual) }
        }

        observePreference(KEY_QUALITY_MOBILE) { qualStr ->
            val qual = qualStr.toIntOrNull() ?: 0
            updateState { copy(qualityMobile = qual) }
        }

        observePreference(KEY_SOFTWARE_DECODING) { modeStr ->
            val mode = modeStr.toIntOrNull() ?: -1
            updateState { copy(softwareDecoding = mode) }
        }

        observePreference(KEY_SUBTITLE_ENCODING) { encStr ->
            updateState {
                copy(
                    subtitleEncoding = encStr,
                    subtitleStyle = subtitleStyle.copy(encoding = encStr)
                )
            }
        }

        fun parseBool(str: String, default: Boolean) =
            str.toBooleanStrictOrNull() ?: (str.toIntOrNull()?.let { it != 0 } ?: default)

        observePreference(KEY_SYNC_WATCH_PROGRESS) { str ->
            updateState { copy(syncWatchProgress = parseBool(str, true)) }
        }

        observePreference(KEY_SYNC_SCORES) { str ->
            updateState { copy(syncScores = parseBool(str, true)) }
        }

        observePreference(KEY_SYNC_WIFI_ONLY) { str ->
            updateState { copy(syncWifiOnly = parseBool(str, false)) }
        }

        observePreference(KEY_SKIP_STARTUP_ACCOUNT_SELECT) { str ->
            updateState { copy(skipStartupAccountSelect = parseBool(str, false)) }
        }

        observePreference(KEY_SHOW_SOURCES_ON_PLAY) { str ->
            updateState { copy(showSourcesOnPlay = parseBool(str, false)) }
        }

        AccountManager.accountsState
            .onEach { accounts ->
                updateState { copy(activeAuthAccounts = accounts) }
            }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)
    }

    private fun persistPreference(
        description: String,
        persist: suspend () -> Unit,
        update: AppSettingsState.() -> AppSettingsState
    ) {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = txt(Res.string.settings_error_update_failed, description, t.message.orEmpty()))
                }
            }
        ) {
            persist()
            updateState(update)
        }
    }

    private fun setTheme(theme: AppTheme) = persistPreference("theme", {
        preferenceRepository.setString(KEY_APP_THEME, theme.key)
    }) { copy(theme = theme) }

    private fun setDarkMode(enabled: Boolean) = persistPreference("dark mode", {
        preferenceRepository.setString(KEY_DARK_MODE, enabled.toString())
    }) { copy(isDarkMode = enabled) }

    private fun setProviderLanguages(languages: List<String>) {
        val distinctLangs = languages.distinct()
        persistPreference("provider languages", {
            preferenceRepository.setString(KEY_PROVIDER_LANG, json.encodeToString(distinctLangs))
        }) { copy(preferredProviderLanguages = distinctLangs) }
    }

    private fun setAppLanguage(languageCode: String) = persistPreference("app language", {
        preferenceRepository.setString(KEY_APP_LOCALE, languageCode)
    }) { copy(appLanguage = languageCode) }

    private fun setDohProvider(provider: DohProvider) = persistPreference("DoH provider", {
        preferenceRepository.setString(KEY_DOH_PROVIDER, provider.id.toString())
    }) { copy(dohProvider = provider) }

    private fun setDefaultSubtitleStyle(style: SubtitleStyle) = persistPreference("subtitle style", {
        preferenceRepository.setString(KEY_SUBTITLE_SETTINGS, json.encodeToString(style))
        preferenceRepository.setString(KEY_SUBTITLE_ENCODING, style.encoding)
    }) { copy(subtitleStyle = style, subtitleEncoding = style.encoding) }

    private fun setQualityWifi(quality: Int) = persistPreference("WiFi quality", {
        preferenceRepository.setInt(KEY_QUALITY_WIFI, quality)
    }) { copy(qualityWifi = quality) }

    private fun setQualityMobile(quality: Int) = persistPreference("mobile quality", {
        preferenceRepository.setInt(KEY_QUALITY_MOBILE, quality)
    }) { copy(qualityMobile = quality) }

    private fun setSoftwareDecoding(mode: Int) = persistPreference("software decoding", {
        preferenceRepository.setInt(KEY_SOFTWARE_DECODING, mode)
    }) { copy(softwareDecoding = mode) }

    private fun setSubtitleEncoding(encoding: String) {
        val updatedStyle = currentState.subtitleStyle.copy(encoding = encoding)
        persistPreference("subtitle encoding", {
            preferenceRepository.setString(KEY_SUBTITLE_ENCODING, encoding)
            preferenceRepository.setString(KEY_SUBTITLE_SETTINGS, json.encodeToString(updatedStyle))
        }) { copy(subtitleEncoding = encoding, subtitleStyle = updatedStyle) }
    }

    private fun setSyncWatchProgress(enabled: Boolean) = persistPreference("sync watch progress", {
        preferenceRepository.setBoolean(KEY_SYNC_WATCH_PROGRESS, enabled)
    }) { copy(syncWatchProgress = enabled) }

    private fun setSyncScores(enabled: Boolean) = persistPreference("sync scores", {
        preferenceRepository.setBoolean(KEY_SYNC_SCORES, enabled)
    }) { copy(syncScores = enabled) }

    private fun setSyncWifiOnly(enabled: Boolean) = persistPreference("sync wifi only", {
        preferenceRepository.setBoolean(KEY_SYNC_WIFI_ONLY, enabled)
    }) { copy(syncWifiOnly = enabled) }

    private fun setSkipStartupAccountSelect(enabled: Boolean) = persistPreference("skip startup account select", {
        preferenceRepository.setBoolean(KEY_SKIP_STARTUP_ACCOUNT_SELECT, enabled)
    }) { copy(skipStartupAccountSelect = enabled) }

    private fun setShowSourcesOnPlay(enabled: Boolean) = persistPreference("show sources on play", {
        preferenceRepository.setBoolean(KEY_SHOW_SOURCES_ON_PLAY, enabled)
    }) { copy(showSourcesOnPlay = enabled) }

    private fun loginInApp(api: AuthRepo, form: AuthLoginResponse) {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt(Res.string.settings_error_login_failed, api.name))
                }
            }
        ) {
            val success = api.login(form)
            if (!success) {
                updateState { copy(error = txt(Res.string.settings_error_login_failed, api.name)) }
            }
        }
    }

    private fun loginPin(api: AuthRepo, pinData: AuthPinData) {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt(Res.string.settings_error_pin_login_failed, api.name))
                }
            }
        ) {
            val success = api.login(pinData)
            if (!success) {
                updateState { copy(error = txt(Res.string.settings_error_pin_login_failed, api.name)) }
            }
        }
    }

    private fun logoutAccount(api: AuthRepo, user: AuthUser) {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt(Res.string.settings_error_logout_failed, api.name))
                }
            }
        ) {
            api.logout(user)
        }
    }

    private fun switchActiveAccount(api: AuthRepo, accountId: Int) {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt(Res.string.settings_error_switch_account_failed, api.name))
                }
            }
        ) {
            api.accountId = accountId
        }
    }

    private fun startOAuthLogin(api: AuthRepo) {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt(Res.string.settings_error_oauth_failed, api.name))
                }
            }
        ) {
            api.openOAuth2PageWithToast()
        }
    }

    private fun createBackup(categories: Set<BackupCategory>) {
        launchSafeJob(
            onError = { _ ->
                updateState {
                    copy(
                        isBackingUp = false,
                        backupErrorRes = Res.string.backup_failed,
                        backupSuccessRes = null
                    )
                }
            }
        ) {
            updateState { copy(isBackingUp = true, backupErrorRes = null, backupSuccessRes = null) }
            backupManager.createBackup(categories)
            updateState {
                copy(
                    isBackingUp = false,
                    backupSuccessRes = Res.string.backup_export_success_msg,
                    backupErrorRes = null
                )
            }
        }
    }

    private fun restoreBackup(jsonContent: String, categories: Set<BackupCategory>?) {
        launchSafeJob(
            onError = { _ ->
                updateState {
                    copy(
                        isRestoring = false,
                        backupErrorRes = Res.string.backup_restore_error_invalid,
                        backupSuccessRes = null
                    )
                }
            }
        ) {
            updateState { copy(isRestoring = true, backupErrorRes = null, backupSuccessRes = null) }
            when (val result = backupManager.restoreBackup(jsonContent, categories)) {
                is BackupRestoreResult.Success -> {
                    loadSettings()
                    updateState {
                        copy(
                            isRestoring = false,
                            backupSuccessRes = Res.string.backup_restore_success_msg,
                            backupErrorRes = null
                        )
                    }
                }
                is BackupRestoreResult.Error -> {
                    updateState {
                        copy(
                            isRestoring = false,
                            backupErrorRes = Res.string.backup_restore_error_invalid,
                            backupSuccessRes = null
                        )
                    }
                }
            }
        }
    }

    private fun exportBackupWithPicker(categories: Set<BackupCategory>) {
        launchSafeJob(
            onError = { _ ->
                updateState {
                    copy(
                        isBackingUp = false,
                        backupErrorRes = Res.string.backup_failed,
                        backupSuccessRes = null
                    )
                }
            }
        ) {
            updateState { copy(isBackingUp = true, backupErrorRes = null, backupSuccessRes = null) }
            val content = backupManager.createBackup(categories)
            val fileName = "cloudstream_backup_${APIHolder.unixTimeMS}.json"
            val success = PlatformFilePicker.pickFileForSave(fileName, content)
            if (success) {
                updateState {
                    copy(
                        isBackingUp = false,
                        backupSuccessRes = Res.string.backup_export_success_msg,
                        backupErrorRes = null
                    )
                }
            } else {
                updateState {
                    copy(
                        isBackingUp = false,
                        backupErrorRes = Res.string.backup_failed,
                        backupSuccessRes = null
                    )
                }
            }
        }
    }

    private fun importBackupWithPicker() {
        launchSafeJob(
            onError = { _ ->
                updateState {
                    copy(
                        isRestoring = false,
                        backupErrorRes = Res.string.backup_restore_error_invalid,
                        backupSuccessRes = null
                    )
                }
            }
        ) {
            updateState { copy(isRestoring = true, backupErrorRes = null, backupSuccessRes = null) }
            val content = PlatformFilePicker.readTextFromFile(listOf("json", "txt"))
            if (content != null) {
                when (val result = backupManager.restoreBackup(content, currentState.availableBackupCategories)) {
                    is BackupRestoreResult.Success -> {
                        loadSettings()
                        updateState {
                            copy(
                                isRestoring = false,
                                backupSuccessRes = Res.string.backup_restore_success_msg,
                                backupErrorRes = null
                            )
                        }
                    }
                    is BackupRestoreResult.Error -> {
                        updateState {
                            copy(
                                isRestoring = false,
                                backupErrorRes = Res.string.backup_restore_error_invalid,
                                backupSuccessRes = null
                            )
                        }
                    }
                }
            } else {
                updateState { copy(isRestoring = false) }
            }
        }
    }

    private fun clearBackupMessage() {
        updateState { copy(backupSuccessRes = null, backupErrorRes = null) }
    }

    private fun resetToDefaults() {
        launchSafeJob(
            onError = { t ->
                updateState {
                    copy(error = txt(Res.string.settings_error_reset_failed, t.message.orEmpty()))
                }
            }
        ) {
            val defaultState = AppSettingsState()
            preferenceRepository.setString(KEY_APP_THEME, defaultState.theme.key)
            preferenceRepository.setString(KEY_DARK_MODE, defaultState.isDarkMode.toString())
            preferenceRepository.setString(KEY_PROVIDER_LANG, json.encodeToString(defaultState.preferredProviderLanguages))
            preferenceRepository.setString(KEY_DOH_PROVIDER, defaultState.dohProvider.id.toString())
            preferenceRepository.setString(KEY_SUBTITLE_SETTINGS, json.encodeToString(defaultState.subtitleStyle))
            preferenceRepository.setString(KEY_APP_LOCALE, defaultState.appLanguage)
            preferenceRepository.setInt(KEY_QUALITY_WIFI, defaultState.qualityWifi)
            preferenceRepository.setInt(KEY_QUALITY_MOBILE, defaultState.qualityMobile)
            preferenceRepository.setInt(KEY_SOFTWARE_DECODING, defaultState.softwareDecoding)
            preferenceRepository.setString(KEY_SUBTITLE_ENCODING, defaultState.subtitleEncoding)
            preferenceRepository.setBoolean(KEY_SYNC_WATCH_PROGRESS, defaultState.syncWatchProgress)
            preferenceRepository.setBoolean(KEY_SYNC_SCORES, defaultState.syncScores)
            preferenceRepository.setBoolean(KEY_SYNC_WIFI_ONLY, defaultState.syncWifiOnly)
            preferenceRepository.setBoolean(KEY_SKIP_STARTUP_ACCOUNT_SELECT, defaultState.skipStartupAccountSelect)
            preferenceRepository.setBoolean(KEY_SHOW_SOURCES_ON_PLAY, defaultState.showSourcesOnPlay)
            setState(defaultState.copy(activeAuthAccounts = AccountManager.accountsState.value))
        }
    }

    private fun parseStringList(value: String?, default: List<String>): List<String> {
        if (value.isNullOrBlank()) return default
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Throwable) {
            // Support comma-separated format or raw set format
            value.removePrefix("[").removeSuffix("]")
                .split(",")
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotBlank() }
                .ifEmpty { default }
        }
    }
}
