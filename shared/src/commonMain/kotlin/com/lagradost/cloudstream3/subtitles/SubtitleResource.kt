package com.lagradost.cloudstream3.subtitles

import com.lagradost.cloudstream3.app
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.util.zip.ZipInputStream

enum class SubtitleOrigin {
    URL,
    DOWNLOADED_FILE,
    EMBEDDED_IN_VIDEO
}

/**
 * A builder for subtitle files.
 * @see addUrl
 * @see addFile
 */
class SubtitleResource {
    data class SingleSubtitleResource(
        val name: String?,
        val url: String,
        val origin: SubtitleOrigin
    )

    private var resources: MutableList<SingleSubtitleResource> = mutableListOf()

    fun getSubtitles(): List<SingleSubtitleResource> = resources.toList()

    fun addUrl(url: String?, name: String? = null) {
        if (url == null) return
        this.resources.add(
            SingleSubtitleResource(name, url, SubtitleOrigin.URL)
        )
    }

    fun addFile(file: File, name: String? = null) {
        this.resources.add(
            SingleSubtitleResource(name, file.toURI().toString(), SubtitleOrigin.DOWNLOADED_FILE)
        )
    }

    fun downloadFile(source: BufferedSource): File {
        val file = File.createTempFile("temp-subtitle", ".tmp").apply {
            deleteOnExit()
        }
        val sink = file.sink().buffer()
        sink.writeAll(source)
        sink.close()
        source.close()
        return file
    }

    private fun unzip(file: File): List<Pair<String, File>> {
        val entries = mutableListOf<Pair<String, File>>()
        ZipInputStream(file.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                val tempFile = File.createTempFile("unzipped-subtitle", ".tmp").apply {
                    deleteOnExit()
                }
                entries.add(zipEntry.name to tempFile)
                tempFile.sink().buffer().use { buffer ->
                    buffer.writeAll(zipInputStream.source())
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
        return entries
    }

    suspend fun addZipUrl(
        url: String,
        nameGenerator: (String, File) -> String? = { _, _ -> null }
    ) {
        val source = app.get(url).okhttpResponse.body.source()
        val zip = downloadFile(source)
        val realFiles = unzip(zip)
        zip.deleteRecursively()
        realFiles.forEach { (name, subtitleFile) ->
            addFile(subtitleFile, nameGenerator(name, subtitleFile))
        }
    }
}
