package com.lagradost.cloudstream3.shared.syncproviders.providers

import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.shared.syncproviders.AuthData
import com.lagradost.cloudstream3.shared.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName

class Addic7ed : SubtitleAPI() {
    override val name = "Addic7ed"
    override val idPrefix = "addic7ed"
    override val requiresLogin = false

    companion object {
        const val HOST = "https://www.addic7ed.com"
        const val TAG = "ADDIC7ED"
    }

    private fun String.fixUrl(): String {
        val url = this
        return if (url.startsWith("/")) HOST + url
        else if (!url.startsWith("http")) "$HOST/$url"
        else url
    }

    private fun buildSearchTargetQuery(title: String, seasonNum: Int, epNum: Int): String {
        return if (seasonNum > 0) "$title $seasonNum $epNum" else title
    }

    private fun resolveDownloadPageSelector(seasonNum: Int, yearNum: Int, epNum: Int): String = when {
        seasonNum > 0 -> "a[href~=serie\\/.+\\/$seasonNum\\/$epNum\\/\\w]"
        yearNum > 0 -> "a[href~=movie\\/]:contains($yearNum)"
        else -> "a[href~=movie\\/]"
    }

    private fun createSubtitleEntity(
        displayName: String?,
        link: String?,
        isHearingImpaired: Boolean,
        langTagIETF: String,
        seasonNum: Int,
        epNum: Int,
        yearNum: Int
    ): SubtitleEntity? {
        if (displayName.isNullOrBlank() || link.isNullOrBlank()) return null
        return SubtitleEntity(
            idPrefix = this.idPrefix,
            name = displayName,
            lang = langTagIETF,
            data = link,
            source = this.name,
            type = if (seasonNum > 0) TvType.TvSeries else TvType.Movie,
            epNumber = epNum,
            seasonNumber = seasonNum,
            year = yearNum,
            headers = mapOf("referer" to "$HOST/"),
            isHearingImpaired = isHearingImpaired
        )
    }

    private suspend fun fetchShowEpisodeSubtitles(
        showUrl: String,
        seasonNum: Int,
        epNum: Int,
        langNum: String,
        langTagIETF: String,
        yearNum: Int
    ): List<SubtitleEntity> {
        val showId = showUrl.substringAfterLast("/")
        val doc = app.get(
            "$HOST/ajax_loadShow.php?show=$showId&season=$seasonNum&langs=|$langNum|&hd=0&hi=0",
            referer = "$HOST/"
        ).document

        return doc.select("#season tbody tr").mapNotNull { node ->
            if (node.select("td:eq(1)").text().toIntOrNull() != epNum) return@mapNotNull null
            createSubtitleEntity(
                displayName = node.select("td:eq(2)").text() + "\n" + node.select("td:eq(4)").text(),
                link = node.selectFirst("a[href~=updated\\/|original\\/]")?.attr("href")?.fixUrl(),
                isHearingImpaired = node.select("td:eq(6)").text().isNotEmpty(),
                langTagIETF = langTagIETF,
                seasonNum = seasonNum,
                epNum = epNum,
                yearNum = yearNum
            )
        }
    }

    private suspend fun fetchDownloadPageSubtitles(
        downloadPage: String,
        langName: String,
        langTagIETF: String,
        seasonNum: Int,
        epNum: Int,
        yearNum: Int
    ): List<SubtitleEntity> {
        val doc = app.get(url = downloadPage).document
        return doc.select(".tabel95 .tabel95 tr:has(.language):contains($langName)").mapNotNull { node ->
            val parent = node.parent() ?: return@mapNotNull null
            val titlePrefix = doc.selectFirst("span.titulo")?.text()?.substringBefore(" Subtitle").orEmpty()
            val versionInfo = parent.select(".NewsTitle").text().substringAfter("Version ").substringBefore(", Duration")
            val displayName = "$titlePrefix\n$versionInfo"
            val link = node.selectFirst("a[href~=updated\\/|original\\/]")?.attr("href")?.fixUrl()
            val isHearingImpaired = parent.select("tr:last-child [title=\"Hearing Impaired\"]").isNotEmpty()

            createSubtitleEntity(
                displayName = displayName,
                link = link,
                isHearingImpaired = isHearingImpaired,
                langTagIETF = langTagIETF,
                seasonNum = seasonNum,
                epNum = epNum,
                yearNum = yearNum
            )
        }
    }

    override suspend fun search(
        auth: AuthData?,
        query: SubtitleSearch
    ): List<SubtitleEntity>? {
        val langTagIETF = query.lang ?: AllLanguagesName
        val langNumAddic7ed = langTagIETF2Addic7ed[langTagIETF]?.first ?: "0"
        val langName = langTagIETF2Addic7ed[langTagIETF]?.second
            ?: fromTagToEnglishLanguageName(langTagIETF)
            ?: "Completed"
        val title = query.query.trim()
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0
        val searchQuery = buildSearchTargetQuery(title, seasonNum, epNum)

        val response = app.get(url = "$HOST/search.php?search=$searchQuery&Submit=Search")
        if (response.url.contains("/show/")) {
            return fetchShowEpisodeSubtitles(
                showUrl = response.url,
                seasonNum = seasonNum,
                epNum = epNum,
                langNum = langNumAddic7ed,
                langTagIETF = langTagIETF,
                yearNum = yearNum
            )
        }

        val initialUrl = if (response.url.contains("/movie/") || response.url.contains("/serie/")) {
            response.url
        } else {
            val selector = resolveDownloadPageSelector(seasonNum, yearNum, epNum)
            response.document.select("table.tabel a").selectFirst(selector)?.attr("href")?.fixUrl() ?: return null
        }

        val downloadPage = if (initialUrl.contains("/serie/")) {
            initialUrl.substringBeforeLast("/") + "/$langNumAddic7ed"
        } else {
            initialUrl
        }

        return fetchDownloadPageSubtitles(
            downloadPage = downloadPage,
            langName = langName,
            langTagIETF = langTagIETF,
            seasonNum = seasonNum,
            epNum = epNum,
            yearNum = yearNum
        )
    }

    override suspend fun load(
        auth: AuthData?,
        subtitle: SubtitleEntity
    ): String? {
        return subtitle.data
    }

    // Do not modify unless Addic7ed changes them!
    // as they are the exact values from their website
    private val langTagIETF2Addic7ed = mapOf(
        "ar"      to Pair("38", "Arabic"),
        "az"      to Pair("48", "Azerbaijani"),
        "bg"      to Pair("35", "Bulgarian"),
        "bn"      to Pair("47", "Bengali"),
        "bs"      to Pair("44", "Bosnian"),
        "ca"      to Pair("12", "Català"),
        "cs"      to Pair("14", "Czech"),
        "cy"      to Pair("65", "Welsh"),
        "da"      to Pair("30", "Danish"),
        "de"      to Pair("11", "German"),
        "el"      to Pair("27", "Greek"),
        "en"      to Pair("1", "English"),
        "es-419"  to Pair("6", "Spanish (Latin America)"),
        "es-ar"   to Pair("69", "Spanish (Argentina)"),
        "es-es"   to Pair("5", "Spanish (Spain)"),
        "es"      to Pair("4", "Spanish"),
        "et"      to Pair("54", "Estonian"),
        "eu"      to Pair("13", "Euskera"),
        "fa"      to Pair("43", "Persian"),
        "fi"      to Pair("28", "Finnish"),
        "fr-ca"   to Pair("53", "French (Canadian)"),
        "fr"      to Pair("8", "French"),
        "gl"      to Pair("15", "Galego"),
        "he"      to Pair("23", "Hebrew"),
        "hi"      to Pair("55", "Hindi"),
        "hr"      to Pair("31", "Croatian"),
        "hu"      to Pair("20", "Hungarian"),
        "hy"      to Pair("50", "Armenian"),
        "id"      to Pair("37", "Indonesian"),
        "is"      to Pair("56", "Icelandic"),
        "it"      to Pair("7", "Italian"),
        "ja"      to Pair("32", "Japanese"),
        "kn"      to Pair("66", "Kannada"),
        "ko"      to Pair("42", "Korean"),
        "lt"      to Pair("58", "Lithuanian"),
        "lv"      to Pair("57", "Latvian"),
        "mk"      to Pair("49", "Macedonian"),
        "ml"      to Pair("67", "Malayalam"),
        "mr"      to Pair("62", "Marathi"),
        "ms"      to Pair("40", "Malay"),
        "nl"      to Pair("17", "Dutch"),
        "no"      to Pair("29", "Norwegian"),
        "pl"      to Pair("21", "Polish"),
        "pt-br"   to Pair("10", "Portuguese (Brazilian)"),
        "pt"      to Pair("9", "Portuguese"),
        "ro"      to Pair("26", "Romanian"),
        "ru"      to Pair("19", "Russian"),
        "si"      to Pair("60", "Sinhala"),
        "sk"      to Pair("25", "Slovak"),
        "sl"      to Pair("22", "Slovenian"),
        "sq"      to Pair("52", "Albanian"),
        "sr-latn" to Pair("36", "Serbian (Latin)"),
        "sr"      to Pair("39", "Serbian (Cyrillic)"),
        "sv"      to Pair("18", "Swedish"),
        "ta"      to Pair("59", "Tamil"),
        "te"      to Pair("63", "Telugu"),
        "th"      to Pair("46", "Thai"),
        "tl"      to Pair("68", "Tagalog"),
        "tlh"     to Pair("61", "Klingon"),
        "tr"      to Pair("16", "Turkish"),
        "uk"      to Pair("51", "Ukrainian"),
        "vi"      to Pair("45", "Vietnamese"),
        "yue"     to Pair("64", "Cantonese"),
        "zh-hans" to Pair("41", "Chinese (Simplified)"),
        "zh-hant" to Pair("24", "Chinese (Traditional)"),
    )
}
