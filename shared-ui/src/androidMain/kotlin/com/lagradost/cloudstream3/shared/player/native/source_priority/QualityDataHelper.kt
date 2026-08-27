package com.lagradost.cloudstream3.shared.player.native.source_priority

import androidx.annotation.StringRes
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jetbrains.compose.resources.StringResource
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.also
import kotlin.math.abs

object QualityDataHelper {
    private const val VIDEO_SOURCE_PRIORITY = "video_source_priority"
    private const val VIDEO_PROFILE_NAME = "video_profile_name"
    private const val VIDEO_QUALITY_PRIORITY = "video_quality_priority"
    const val VIDEO_PROFILE_SETTINGS = "video_profile_settings"

    // Old key only supporting one type per profile
    @Deprecated("Changed to support multiple types per profile")
    private const val VIDEO_PROFILE_TYPE = "video_profile_type"
    // New key supporting more than one type per profile

    private const val VIDEO_PROFILE_TYPES = "video_profile_types_2"
    private const val DEFAULT_SOURCE_PRIORITY = 1

    @PublishedApi
    internal val currentAccount: String
        get() = AppPreferenceManager.getIntSync("data_store_helper/account_key_index", 0).toString()

    /**
     * Automatically skip loading links once this priority is reached
     **/
    const val AUTO_SKIP_PRIORITY = 10

    /**
     * Must be higher than amount of QualityProfileTypes
     **/
    private const val PROFILE_COUNT = 7

    /**
     * Unique guarantees that there will always be one of this type in the profile list.
     **/
    enum class QualityProfileType(val stringRes: StringResource, val unique: Boolean) {
        None(Res.string.none, false),
        WiFi(Res.string.wifi, true),
        Data(Res.string.mobile_data, true),
        Download(Res.string.download, true)
    }

    data class QualityProfile(
        val name: UiText,
        val id: Int,
        val types: Set<QualityProfileType>
    )


    // Map profile and name to priority
    val sourcePriorityCache: ConcurrentHashMap<Int, HashMap<String, Int>> = ConcurrentHashMap()

    fun getSourcePriority(profile: Int, name: String?): Int {
        if (name == null) return DEFAULT_SOURCE_PRIORITY

        return sourcePriorityCache[profile]?.get(name) ?: (AppPreferenceManager.getIntSync(
            "$currentAccount/$VIDEO_SOURCE_PRIORITY/$profile/$name",
            DEFAULT_SOURCE_PRIORITY
        )).also {
            sourcePriorityCache.getOrPut(profile) { hashMapOf() }
            sourcePriorityCache[profile]?.set(name, it)
        }
    }

    fun getAllSourcePriorityNames(profile: Int): List<String> {
        val folder = "$currentAccount/$VIDEO_SOURCE_PRIORITY/$profile"
        return AppPreferenceManager.getKeysSync(folder).map { key ->
            key.substringAfter("$folder/")
        }
    }

    fun setSourcePriority(profile: Int, name: String, priority: Int) {
        val path = "$currentAccount/$VIDEO_SOURCE_PRIORITY/$profile/$name"
        // Prevent unnecessary keys
        if (priority == DEFAULT_SOURCE_PRIORITY) {
            AppPreferenceManager.deletePreferenceSync(path)
        } else {
            AppPreferenceManager.setIntSync(path, priority)
        }

        sourcePriorityCache[profile]?.set(name, priority)
    }

    fun setProfileName(profile: Int, name: String?) {
        val path = "$currentAccount/$VIDEO_PROFILE_NAME/$profile"
        if (name == null) {
            AppPreferenceManager.deletePreferenceSync(path)
        } else {
            AppPreferenceManager.setStringSync(path, name.trim())
        }
    }

    fun getProfileName(profile: Int): UiText {
        return AppPreferenceManager.getStringSync("$currentAccount/$VIDEO_PROFILE_NAME/$profile")?.let { txt(it) }
            ?: txt(Res.string.profile_number, profile)
    }

    // Map profile and quality to priority
    val qualityPriorityCache: ConcurrentHashMap<Int, EnumMap<Qualities, Int>> = ConcurrentHashMap()
    fun getQualityPriority(profile: Int, quality: Qualities): Int {
        return qualityPriorityCache[profile]?.get(quality) ?: (AppPreferenceManager.getIntSync(
            "$currentAccount/$VIDEO_QUALITY_PRIORITY/$profile/${quality.value}",
            quality.defaultPriority
        ).also {
            qualityPriorityCache.getOrPut(profile) { EnumMap(Qualities::class.java) }
            qualityPriorityCache[profile]?.set(quality, it)
        })
    }

    fun setQualityPriority(profile: Int, quality: Qualities, priority: Int) {
        AppPreferenceManager.setIntSync(
            "$currentAccount/$VIDEO_QUALITY_PRIORITY/$profile/${quality.value}",
            priority
        )
        qualityPriorityCache[profile]?.set(quality, priority)
    }

    fun <T : Any> setProfileSetting(profile: Int, setting: ProfileSettings<T>, value: T) {
        val path = "$currentAccount/$VIDEO_PROFILE_SETTINGS/$profile/${setting.key}"
        // Prevent unnecessary keys
        if (value == setting.defaultValue) {
            AppPreferenceManager.deletePreferenceSync(path)
        } else {
            when (value) {
                is Boolean -> AppPreferenceManager.setBooleanSync(path, value)
                is Int -> AppPreferenceManager.setIntSync(path, value)
                is String -> AppPreferenceManager.setStringSync(path, value)
                else -> AppPreferenceManager.setStringSync(path, toJson(value))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getProfileSetting(profile: Int, setting: ProfileSettings<T>): T {
        val path = "$currentAccount/$VIDEO_PROFILE_SETTINGS/$profile/${setting.key}"
        return when (val def = setting.defaultValue) {
            is Boolean -> AppPreferenceManager.getBooleanSync(path, def) as T
            is Int -> AppPreferenceManager.getIntSync(path, def) as T
            is String -> (AppPreferenceManager.getStringSync(path, def) ?: def) as T
            else -> AppPreferenceManager.getStringSync(path)?.let { parseJson<T>(it) } ?: def
        }
    }

    @Suppress("DEPRECATION")
    fun getQualityProfileTypes(profile: Int): Set<QualityProfileType> {
        val newKey = "$currentAccount/$VIDEO_PROFILE_TYPES/$profile"
        val rawTypes = AppPreferenceManager.getStringSetSync(newKey)
        if (rawTypes != null) {
            return rawTypes.mapNotNull { name -> QualityProfileType.entries.firstOrNull { it.name == name } }.toSet()
        }

        // Migrate to new profile key
        val oldType = AppPreferenceManager.getStringSync("$currentAccount/$VIDEO_PROFILE_TYPE/$profile")
        val oldProfileType = oldType?.let { name -> QualityProfileType.entries.firstOrNull { it.name == name } }
        val newSet = if (oldProfileType != null) setOf(oldProfileType) else emptySet()
        AppPreferenceManager.setStringSetSync(newKey, newSet.map { it.name }.toSet())
        return newSet
    }

    fun addQualityProfileType(profile: Int, type: QualityProfileType) {
        val path = "$currentAccount/$VIDEO_PROFILE_TYPES/$profile"
        val currentTypes = getQualityProfileTypes(profile)

        if (type != QualityProfileType.None) {
            AppPreferenceManager.setStringSetSync(path, (currentTypes + type).map { it.name }.toSet())
        }
    }

    fun removeQualityProfileType(profile: Int, type: QualityProfileType) {
        val path = "$currentAccount/$VIDEO_PROFILE_TYPES/$profile"
        val currentTypes = getQualityProfileTypes(profile)

        if (type != QualityProfileType.None) {
            AppPreferenceManager.setStringSetSync(path, (currentTypes - type).map { it.name }.toSet())
        }
    }

    /**
     * Gets all quality profiles, always includes one profile with WiFi and Data
     * Must under all circumstances at least return one profile
     **/
    fun getProfiles(): List<QualityProfile> {
        val availableTypes = QualityProfileType.entries.toMutableList()
        val profiles = (1..PROFILE_COUNT).map { profileNumber ->
            // Get the real type
            val types = getQualityProfileTypes(profileNumber)

            val uniqueTypes = types.mapNotNull { type ->
                // This makes it impossible to get more than one of each type
                if (type.unique && !availableTypes.remove(type)) {
                    null
                } else {
                    type
                }
            }.toSet()

            QualityProfile(
                getProfileName(profileNumber),
                profileNumber,
                uniqueTypes
            )
        }.toMutableList()

        /**
         * If no profile of this type exists: insert it on the earliest profile
         **/
        fun insertType(
            list: MutableList<QualityProfile>,
            type: QualityProfileType
        ) {
            if (list.any { it.types.contains(type) }) return

            synchronized(list) {
                val firstItem = list.firstOrNull() ?: return
                val fixedTypes = firstItem.types + type
                val fixedItem = firstItem.copy(types = fixedTypes)
                list.set(0, fixedItem)
            }
        }

        QualityProfileType.entries.forEach {
            if (it.unique) insertType(profiles, it)
        }

        debugAssert({
            !QualityProfileType.entries.all { type ->
                !type.unique || profiles.any { it.types.contains(type) }
            }
        }, { "All unique quality types do not exist" })

        debugAssert({
            profiles.isEmpty()
        }, { "No profiles!" })

        return profiles
    }

    fun getLinkPriority(
        qualityProfile: Int,
        linkData: ExtractorLink?
    ): Int {
        val qualityPriority = getQualityPriority(
            qualityProfile,
            closestQuality(linkData?.quality)
        )
        val sourcePriority = getSourcePriority(qualityProfile, linkData?.source)

        return qualityPriority + sourcePriority
    }

    private fun closestQuality(target: Int?): Qualities {
        if (target == null) return Qualities.Unknown
        return Qualities.entries.minBy { abs(it.value - target) }
    }
}

sealed class ProfileSettings<T>(val key: String, val defaultValue: T) {
    object HideErrorSources : ProfileSettings<Boolean>("hide_error_sources", false)
    object HideNegativeSources : ProfileSettings<Boolean>("hide_negative_sources", false)
}
