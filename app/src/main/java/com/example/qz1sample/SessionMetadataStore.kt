package com.example.qz1sample

import android.content.Context
import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

interface SessionMetadataStore {
    fun read(fileName: String): SessionMetadata?
    fun write(metadata: SessionMetadata)
    fun delete(fileName: String)
}

class JsonSessionMetadataStore(context: Context) : SessionMetadataStore {
    private val directory = NmeaLogStore.directory(context)

    override fun read(fileName: String): SessionMetadata? {
        val metadataFile = metadataFile(fileName) ?: return null
        return try {
            AtomicFile(metadataFile).openRead().use { input ->
                JsonReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(
                    SessionMetadataJsonCodec::read
                )
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    override fun write(metadata: SessionMetadata) {
        val metadataFile = metadataFile(metadata.source.fileName)
            ?: throw IOException("Invalid NMEA file name")
        val atomicFile = AtomicFile(metadataFile)
        var output: FileOutputStream? = null
        try {
            val stream = atomicFile.startWrite()
            output = stream
            val writer = JsonWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8))
            SessionMetadataJsonCodec.write(writer, metadata)
            writer.flush()
            atomicFile.finishWrite(stream)
            output = null
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    override fun delete(fileName: String) {
        metadataFile(fileName)?.let { AtomicFile(it).delete() }
    }

    private fun metadataFile(fileName: String): File? {
        if (File(fileName).name != fileName) return null
        if (!fileName.endsWith(NMEA_EXTENSION, ignoreCase = true)) return null
        return File(directory, "${fileName.dropLast(NMEA_EXTENSION.length)}.meta.json")
    }

    private companion object {
        const val NMEA_EXTENSION = ".nmea"
    }
}

internal object SessionMetadataJsonCodec {
    fun write(writer: JsonWriter, metadata: SessionMetadata) {
        writer.beginObject()
        writer.name("schemaVersion").value(metadata.schemaVersion.toLong())
        writer.name("parserVersion").value(metadata.parserVersion.toLong())
        writer.name("fileName").value(metadata.source.fileName)
        writer.name("sizeBytes").value(metadata.source.sizeBytes)
        writer.name("lastModifiedMillis").value(metadata.source.lastModifiedMillis)
        writer.name("durationMillis").nullableValue(metadata.summary.durationMillis)
        writer.name("pointCount").value(metadata.summary.pointCount.toLong())
        writer.name("distanceMeters").value(metadata.summary.distanceMeters)
        writer.endObject()
    }

    fun read(reader: JsonReader): SessionMetadata? {
        var schemaVersion: Int? = null
        var parserVersion: Int? = null
        var fileName: String? = null
        var sizeBytes: Long? = null
        var lastModifiedMillis: Long? = null
        var durationMillis: Long? = null
        var pointCount: Int? = null
        var distanceMeters: Double? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "parserVersion" -> parserVersion = reader.nextInt()
                "fileName" -> fileName = reader.nextString()
                "sizeBytes" -> sizeBytes = reader.nextLong()
                "lastModifiedMillis" -> lastModifiedMillis = reader.nextLong()
                "durationMillis" -> durationMillis = reader.nextNullableLong()
                "pointCount" -> pointCount = reader.nextInt()
                "distanceMeters" -> distanceMeters = reader.nextDouble()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return SessionMetadata(
            schemaVersion = schemaVersion ?: return null,
            parserVersion = parserVersion ?: return null,
            source = SourceFingerprint(
                fileName = fileName ?: return null,
                sizeBytes = sizeBytes ?: return null,
                lastModifiedMillis = lastModifiedMillis ?: return null
            ),
            summary = SavedLogSummary(
                durationMillis = durationMillis,
                pointCount = pointCount ?: return null,
                distanceMeters = distanceMeters ?: return null
            )
        )
    }

    private fun JsonWriter.nullableValue(value: Long?) {
        if (value == null) nullValue() else value(value)
    }

    private fun JsonReader.nextNullableLong(): Long? {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextLong()
        }
    }
}
