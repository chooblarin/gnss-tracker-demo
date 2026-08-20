package com.example.qz1sample

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Locale

data class PhoneLocationSample(
    val epochMillis: Long,
    val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?,
    val satellitesVisible: Int?,
    val satellitesUsed: Int?,
    val gpsUsed: Int?,
    val qzssUsed: Int?
)

data class PhoneSatelliteSnapshot(
    val visible: Int = 0,
    val used: Int = 0,
    val gpsUsed: Int = 0,
    val qzssUsed: Int = 0
)

data class PhoneGnssSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val savedPoints: Int = 0,
    val satellites: PhoneSatelliteSnapshot = PhoneSatelliteSnapshot()
)

class PhoneLocationLogWriter private constructor(
    val file: File,
    private val writer: BufferedWriter
) {

    fun append(sample: PhoneLocationSample) {
        writer.appendLine(PhoneLocationCsvCodec.encode(sample))
        writer.flush()
    }

    fun close() {
        writer.close()
    }

    fun discard() {
        runCatching { writer.close() }
        file.delete()
    }

    companion object {
        fun create(context: Context, nmeaFileName: String): PhoneLocationLogWriter {
            val file = PhoneLocationLogStore.fileFor(context, nmeaFileName)
                ?: error("Invalid NMEA file name: $nmeaFileName")
            val path = file.toPath()
            Files.createFile(path)
            var writer: BufferedWriter? = null
            return try {
                val openedWriter = BufferedWriter(
                    OutputStreamWriter(
                        Files.newOutputStream(path, StandardOpenOption.WRITE),
                        StandardCharsets.UTF_8
                    )
                )
                writer = openedWriter
                openedWriter.appendLine(PhoneLocationCsvCodec.HEADER)
                openedWriter.flush()
                PhoneLocationLogWriter(file, openedWriter)
            } catch (error: Exception) {
                runCatching { writer?.close() }
                Files.deleteIfExists(path)
                throw error
            }
        }
    }
}

object PhoneLocationLogStore {
    fun fileFor(context: Context, nmeaFileName: String): File? {
        if (File(nmeaFileName).name != nmeaFileName) return null
        if (!nmeaFileName.endsWith(NMEA_EXTENSION, ignoreCase = true)) return null
        val baseName = nmeaFileName.dropLast(NMEA_EXTENSION.length)
        return File(NmeaLogStore.directory(context), "$baseName$PHONE_EXTENSION")
    }

    fun read(context: Context, nmeaFileName: String): List<PhoneLocationSample>? {
        val file = fileFor(context, nmeaFileName)?.takeIf(File::isFile) ?: return null
        return file.useLines { lines ->
            lines.drop(1).mapNotNull(PhoneLocationCsvCodec::decode).toList()
        }
    }

    fun read(
        context: Context,
        sidecar: PhoneLocationSidecar.Available
    ): List<PhoneLocationSample>? {
        return read(context, sidecar.sessionFileName)
    }

    fun hasSamples(context: Context, nmeaFileName: String): Boolean {
        val file = fileFor(context, nmeaFileName)?.takeIf(File::isFile) ?: return false
        return runCatching {
            file.useLines(StandardCharsets.UTF_8) { lines ->
                containsValidSample(lines)
            }
        }.getOrDefault(false)
    }

    internal fun containsValidSample(lines: Sequence<String>): Boolean {
        return lines.drop(1).any { PhoneLocationCsvCodec.decode(it) != null }
    }

    fun delete(context: Context, nmeaFileName: String): Boolean {
        val file = fileFor(context, nmeaFileName) ?: return false
        return !file.exists() || file.delete()
    }

    private const val NMEA_EXTENSION = ".nmea"
    private const val PHONE_EXTENSION = ".phone.csv"
}

internal object PhoneLocationCsvCodec {
    const val HEADER = "epochMillis,elapsedRealtimeNanos,latitude,longitude," +
        "altitudeMeters,accuracyMeters,speedMetersPerSecond,bearingDegrees," +
        "satellitesVisible,satellitesUsed,gpsUsed,qzssUsed"

    fun encode(sample: PhoneLocationSample): String {
        return listOf(
            sample.epochMillis.toString(),
            sample.elapsedRealtimeNanos.toString(),
            sample.latitude.csv(),
            sample.longitude.csv(),
            sample.altitudeMeters?.csv().orEmpty(),
            sample.accuracyMeters?.csv().orEmpty(),
            sample.speedMetersPerSecond?.csv().orEmpty(),
            sample.bearingDegrees?.csv().orEmpty(),
            sample.satellitesVisible?.toString().orEmpty(),
            sample.satellitesUsed?.toString().orEmpty(),
            sample.gpsUsed?.toString().orEmpty(),
            sample.qzssUsed?.toString().orEmpty()
        ).joinToString(",")
    }

    fun decode(line: String): PhoneLocationSample? {
        val fields = line.split(',')
        if (fields.size != 12) return null
        return PhoneLocationSample(
            epochMillis = fields[0].toLongOrNull() ?: return null,
            elapsedRealtimeNanos = fields[1].toLongOrNull() ?: return null,
            latitude = fields[2].toDoubleOrNull() ?: return null,
            longitude = fields[3].toDoubleOrNull() ?: return null,
            altitudeMeters = fields[4].toDoubleOrNull(),
            accuracyMeters = fields[5].toDoubleOrNull(),
            speedMetersPerSecond = fields[6].toDoubleOrNull(),
            bearingDegrees = fields[7].toDoubleOrNull(),
            satellitesVisible = fields[8].toIntOrNull(),
            satellitesUsed = fields[9].toIntOrNull(),
            gpsUsed = fields[10].toIntOrNull(),
            qzssUsed = fields[11].toIntOrNull()
        )
    }

    private fun Double.csv(): String = String.format(Locale.US, "%.9f", this)
}
