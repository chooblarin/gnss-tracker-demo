package com.example.qz1sample

import java.util.Locale

object NmeaParser {
    fun parse(line: String): NmeaParseResult {
        val trimmed = line.trim()
        if (!trimmed.startsWith("$") && !trimmed.startsWith("!")) {
            return NmeaParseResult.Malformed
        }

        if (!hasValidChecksum(trimmed)) {
            return NmeaParseResult.InvalidChecksum
        }

        val body = trimmed
            .drop(1)
            .substringBefore('*')
        val fields = body.split(',')
        val header = fields.firstOrNull().orEmpty()
        if (header.length < 3) return NmeaParseResult.Malformed

        return when (header.takeLast(3).uppercase(Locale.US)) {
            "GGA" -> parseGga(header, fields)
            "RMC" -> parseRmc(header, fields)
            else -> NmeaParseResult.Unsupported(header)
        }
    }

    private fun parseGga(header: String, fields: List<String>): NmeaParseResult {
        if (fields.size < 10) return NmeaParseResult.Malformed
        return NmeaParseResult.Parsed(
            NmeaEvent.Gga(
                sentenceType = header,
                utcTime = fields[1].ifBlank { null },
                latitude = parseCoordinate(fields[2], fields[3]),
                longitude = parseCoordinate(fields[4], fields[5]),
                fixQuality = fields[6].toIntOrNull(),
                satellitesUsed = fields[7].toIntOrNull(),
                hdop = fields[8].toDoubleOrNull(),
                altitudeMeters = fields[9].toDoubleOrNull()
            )
        )
    }

    private fun parseRmc(header: String, fields: List<String>): NmeaParseResult {
        if (fields.size < 10) return NmeaParseResult.Malformed
        return NmeaParseResult.Parsed(
            NmeaEvent.Rmc(
                sentenceType = header,
                utcTime = fields[1].ifBlank { null },
                status = fields[2].ifBlank { null },
                latitude = parseCoordinate(fields[3], fields[4]),
                longitude = parseCoordinate(fields[5], fields[6]),
                speedKnots = fields[7].toDoubleOrNull(),
                courseDegrees = fields[8].toDoubleOrNull(),
                utcDate = fields[9].ifBlank { null }
            )
        )
    }

    private fun parseCoordinate(value: String, hemisphere: String): Double? {
        if (value.isBlank() || hemisphere.isBlank()) return null
        val raw = value.toDoubleOrNull() ?: return null
        val degrees = (raw / 100).toInt()
        val minutes = raw - degrees * 100
        val decimal = degrees + minutes / 60.0
        return when (hemisphere.uppercase(Locale.US)) {
            "N", "E" -> decimal
            "S", "W" -> -decimal
            else -> null
        }
    }

    private fun hasValidChecksum(line: String): Boolean {
        val checksumText = line.substringAfter('*', missingDelimiterValue = "")
        if (checksumText.isBlank()) return true
        if (checksumText.length < 2) return false

        val expected = checksumText.take(2).toIntOrNull(radix = 16) ?: return false
        val body = line.drop(1).substringBefore('*')
        val actual = body.fold(0) { checksum, char -> checksum xor char.code }
        return actual == expected
    }
}

sealed interface NmeaParseResult {
    data class Parsed(val event: NmeaEvent) : NmeaParseResult
    data class Unsupported(val sentenceType: String) : NmeaParseResult
    data object InvalidChecksum : NmeaParseResult
    data object Malformed : NmeaParseResult
}

sealed interface NmeaEvent {
    val sentenceType: String

    data class Gga(
        override val sentenceType: String,
        val utcTime: String?,
        val latitude: Double?,
        val longitude: Double?,
        val fixQuality: Int?,
        val satellitesUsed: Int?,
        val hdop: Double?,
        val altitudeMeters: Double?
    ) : NmeaEvent

    data class Rmc(
        override val sentenceType: String,
        val utcTime: String?,
        val status: String?,
        val latitude: Double?,
        val longitude: Double?,
        val speedKnots: Double?,
        val courseDegrees: Double?,
        val utcDate: String?
    ) : NmeaEvent
}
