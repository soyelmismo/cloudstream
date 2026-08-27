package com.lagradost.cloudstream3.shared.persistence.repository

import com.lagradost.cloudstream3.shared.persistence.dao.AppPreferenceDao
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory

/**
 * Centralized Single Source of Truth for application preferences in Kotlin Multiplatform.
 * Integrates with Room Multiplatform AppPreferenceDao and provides unified key constants
 * and typed suspending / synchronous getters and setters.
 */
object AppPreferenceManager {
    // Preference Keys
    const val KEY_APP_THEME = "app_theme_key"
    const val KEY_PRIMARY_COLOR = "primary_color_key"
    const val KEY_DARK_MODE = "dark_mode_key"
    const val KEY_PROVIDER_LANG = "provider_lang_key"
    const val KEY_APP_LOCALE = "locale_key"
    const val KEY_DOH_PROVIDER = "dns_pref"
    const val KEY_SUBTITLE_SETTINGS = "subtitle_settings"
    const val KEY_SUBTITLE_ENCODING = "subtitles_encoding_key"
    const val KEY_QUALITY_WIFI = "quality_pref_key"
    const val KEY_QUALITY_MOBILE = "quality_pref_mobile_data_key"
    const val KEY_SOFTWARE_DECODING = "software_decoding_key"
    const val KEY_AUTOPLAY_NEXT = "autoplay_next_key"
    const val KEY_PIP_ENABLED = "pip_enabled_key"
    const val KEY_PREVIEW_SEEKBAR = "preview_seekbar_key"
    const val KEY_BACKUP_PATH = "backup_path_key"
    const val KEY_BIOMETRIC = "biometric_key"
    const val KEY_APP_LAYOUT = "app_layout_key"
    const val KEY_AUTO_UPDATE = "auto_update_key"
    const val KEY_SKIP_UPDATE = "skip_update_key"
    const val KEY_BATTERY_OPTIMISATION = "battery_optimisation_key"
    const val KEY_BOTTOM_TITLE = "bottom_title_key"
    const val KEY_DOWNLOAD_PATH = "download_path_key"
    const val KEY_DOWNLOAD_PARALLEL = "download_parallel_key"
    const val KEY_DOWNLOAD_CONCURRENT = "download_concurrent_key"
    const val KEY_DISPLAY_SUB = "display_sub_key"
    const val KEY_SEARCH_TYPES = "search_types_list_key"
    const val KEY_SHOW_TRAILERS = "show_trailers_key"
    const val KEY_SHOW_PLAYER_METADATA = "show_player_metadata_key"
    const val KEY_PREFER_MEDIA_TYPE = "prefer_media_type_key"
    const val KEY_FILTER_SEARCH_QUALITY = "pref_filter_search_quality_key"
    const val KEY_SHOW_KITSU_POSTERS = "show_kitsu_posters_key"
    const val KEY_HOME_API_USED = "home_api_used"
    const val KEY_USER_CUSTOM_SITES = "user_custom_sites"
    const val KEY_SYNC_WATCH_PROGRESS = "sync_watch_progress"
    const val KEY_SYNC_SCORES = "sync_scores"
    const val KEY_SYNC_WIFI_ONLY = "sync_wifi_only"
    const val KEY_SKIP_STARTUP_ACCOUNT_SELECT = "skip_startup_account_select_key"
    const val KEY_SHOW_SOURCES_ON_PLAY = "show_sources_on_play_key"
    const val KEY_SEARCH_SELECTED_PROVIDERS = "search_selected_providers"
    const val KEY_SEARCH_SELECTED_TYPES = "search_selected_types"
    const val KEY_SEARCH_SELECTED_QUALITIES = "search_selected_qualities"
    const val KEY_SEARCH_DISPLAY_MODE = "search_display_mode"

    fun getLastSyncApiKey(accountId: Int): String = "${accountId}_last_sync_api"

    private var _repository: AppPreferenceRepository? = null

    val currentRepository: AppPreferenceRepository
        get() {
            return _repository ?: run {
                val defaultRepo = AppPreferenceRepositoryImpl(DatabaseDriverFactory.getDatabase().appPreferenceDao())
                _repository = defaultRepo
                defaultRepo
            }
        }

    fun init(dao: AppPreferenceDao) {
        _repository = AppPreferenceRepositoryImpl(dao)
    }

    fun init(repository: AppPreferenceRepository) {
        _repository = repository
    }

    // -------------------------------------------------------------------------
    // Suspending API
    // -------------------------------------------------------------------------

    suspend fun getInt(key: String, defaultValue: Int = 0): Int =
        currentRepository.getInt(key, defaultValue)

    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        currentRepository.getBoolean(key, defaultValue)

    suspend fun getStringSet(key: String, defaultValue: Set<String>? = null): Set<String>? =
        currentRepository.getStringSet(key, defaultValue)

    suspend fun setInt(key: String, value: Int) =
        currentRepository.setInt(key, value)

    suspend fun setBoolean(key: String, value: Boolean) =
        currentRepository.setBoolean(key, value)

    suspend fun setStringSet(key: String, value: Set<String>) =
        currentRepository.setStringSet(key, value)

    // -------------------------------------------------------------------------
    // Synchronous API
    // -------------------------------------------------------------------------

    fun getStringSync(key: String, defaultValue: String? = null): String? =
        currentRepository.getStringSync(key, defaultValue)

    fun getIntSync(key: String, defaultValue: Int = 0): Int =
        currentRepository.getIntSync(key, defaultValue)

    fun getBooleanSync(key: String, defaultValue: Boolean = false): Boolean =
        currentRepository.getBooleanSync(key, defaultValue)

    fun getStringSetSync(key: String, defaultValue: Set<String>? = null): Set<String>? =
        currentRepository.getStringSetSync(key, defaultValue)

    fun setStringSync(key: String, value: String) =
        currentRepository.setStringSync(key, value)

    fun setIntSync(key: String, value: Int) =
        currentRepository.setIntSync(key, value)

    fun setBooleanSync(key: String, value: Boolean) =
        currentRepository.setBooleanSync(key, value)

    fun setStringSetSync(key: String, value: Set<String>) =
        currentRepository.setStringSetSync(key, value)

    fun deletePreferenceSync(key: String) =
        currentRepository.deletePreferenceSync(key)

    fun removeKeysSync(prefix: String): Int =
        currentRepository.removeKeysSync(prefix)

    fun getKeysSync(prefix: String = ""): List<String> =
        currentRepository.getKeysSync(prefix)

    fun getAllSync(): Map<String, String> =
        currentRepository.getAllSync()
}
