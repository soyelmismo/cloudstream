package com.lagradost.cloudstream3.shared.persistence.converters

import androidx.room.TypeConverter
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        if (value.isNullOrBlank()) return null
        return try {
            json.decodeFromString<Map<String, String>>(value)
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    @TypeConverter
    fun fromDubStatusMap(value: Map<DubStatus, Int?>?): String? = value?.let { map ->
        val stringKeyMap = map.mapKeys { it.key.name }
        json.encodeToString(stringKeyMap)
    }

    @TypeConverter
    fun toDubStatusMap(value: String?): Map<DubStatus, Int?>? {
        if (value.isNullOrBlank()) return null
        return try {
            val stringKeyMap = json.decodeFromString<Map<String, Int?>>(value)
            stringKeyMap.mapNotNull { (key, count) ->
                stringToEnum<DubStatus>(key)?.let { it to count }
            }.toMap()
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
}
