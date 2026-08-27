package com.lagradost.cloudstream3.shared.persistence.converters

import androidx.room.TypeConverter
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters for complex data types and enums used across CloudStream KMP entities.
 */
object RoomTypeConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private inline fun <reified E : Enum<E>> enumToString(value: E?): String? = value?.name
    private inline fun <reified E : Enum<E>> stringToEnum(value: String?): E? =
        value?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    @TypeConverter
    fun fromTvType(value: TvType?): String? = enumToString(value)

    @TypeConverter
    fun toTvType(value: String?): TvType? = stringToEnum(value)

    @TypeConverter
    fun fromSearchQuality(value: SearchQuality?): String? = enumToString(value)

    @TypeConverter
    fun toSearchQuality(value: String?): SearchQuality? = stringToEnum(value)

    @TypeConverter
    fun fromDubStatus(value: DubStatus?): String? = enumToString(value)

    @TypeConverter
    fun toDubStatus(value: String?): DubStatus? = stringToEnum(value)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.let {
        try {
            json.decodeFromString<List<String>>(it)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? = value?.let {
        try {
            json.decodeFromString<Map<String, String>>(it)
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromDubStatusMap(value: Map<DubStatus, Int?>?): String? = value?.let { map ->
        val stringKeyMap = map.mapKeys { it.key.name }
        json.encodeToString(stringKeyMap)
    }

    @TypeConverter
    fun toDubStatusMap(value: String?): Map<DubStatus, Int?>? = value?.let { str ->
        try {
            val stringKeyMap = json.decodeFromString<Map<String, Int?>>(str)
            stringKeyMap.mapNotNull { (key, count) ->
                stringToEnum<DubStatus>(key)?.let { it to count }
            }.toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
