package com.example.qz1sample

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object TrackBuilder {
    fun build(fileName: String, lines: Sequence<String>): Track {
        var lastUtcDate: String? = null
        val points = mutableListOf<TrackPoint>()

        lines.forEach { line ->
            val event = when (val result = NmeaParser.parse(line)) {
                is NmeaParseResult.Parsed -> result.event
                is NmeaParseResult.Unsupported,
                NmeaParseResult.InvalidChecksum,
                NmeaParseResult.Malformed -> return@forEach
            }
            if (event is NmeaEvent.Rmc && event.utcDate != null) {
                lastUtcDate = event.utcDate
            }
            val point = event.toTrackPoint(inheritedUtcDate = lastUtcDate)
                ?: return@forEach

            val previous = points.lastOrNull()
            if (previous != null && previous.isSameSample(point)) {
                points[points.lastIndex] = previous.merge(point)
            } else {
                points += point
            }
        }

        return TrackAssembler.build(fileName, points)
    }

    private fun NmeaEvent.toTrackPoint(inheritedUtcDate: String?): TrackPoint? {
        return when (this) {
            is NmeaEvent.Gga -> TrackPoint(
                latitude = latitude?.takeIf { fixQuality != null && fixQuality > 0 } ?: return null,
                longitude = longitude ?: return null,
                altitudeMeters = altitudeMeters,
                utcDate = inheritedUtcDate,
                utcTime = utcTime,
                satellitesUsed = satellitesUsed,
                hdop = hdop,
                fixQuality = fixQuality
            )
            is NmeaEvent.Rmc -> TrackPoint(
                latitude = latitude?.takeIf { status == "A" } ?: return null,
                longitude = longitude ?: return null,
                speedKmh = speedKnots?.let(::knotsToKilometersPerHour),
                courseDegrees = courseDegrees,
                utcDate = utcDate,
                utcTime = utcTime
            )
        }
    }

    private fun TrackPoint.isSameSample(other: TrackPoint): Boolean {
        return utcTime != null && utcTime == other.utcTime
    }

    private fun TrackPoint.merge(other: TrackPoint): TrackPoint {
        return TrackPoint(
            latitude = other.latitude,
            longitude = other.longitude,
            altitudeMeters = other.altitudeMeters ?: altitudeMeters,
            speedKmh = other.speedKmh ?: speedKmh,
            courseDegrees = other.courseDegrees ?: courseDegrees,
            utcDate = other.utcDate ?: utcDate,
            utcTime = other.utcTime ?: utcTime,
            distanceFromStartMeters = distanceFromStartMeters,
            satellitesUsed = other.satellitesUsed ?: satellitesUsed,
            hdop = other.hdop ?: hdop,
            fixQuality = other.fixQuality ?: fixQuality
        )
    }

}

data class Track(
    val fileName: String,
    val points: List<TrackPoint>,
    val summary: TrackSummary
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val speedKmh: Double? = null,
    val courseDegrees: Double? = null,
    val utcDate: String? = null,
    val utcTime: String? = null,
    val distanceFromStartMeters: Double = 0.0,
    val satellitesUsed: Int? = null,
    val hdop: Double? = null,
    val fixQuality: Int? = null
) {
    val utcLabel: String?
        get() = listOfNotNull(utcDate, utcTime).joinToString(" ").ifBlank { null }

    val epochMillis: Long?
        get() {
            val date = utcDate?.let(::parseNmeaDate) ?: return null
            val time = utcTime?.let(::parseNmeaTime) ?: return null
            return LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
}

data class TrackSummary(
    val pointCount: Int,
    val distanceMeters: Double,
    val durationMillis: Long?,
    val startedUtc: String?,
    val endedUtc: String?,
    val minimumAltitudeMeters: Double?,
    val maximumAltitudeMeters: Double?,
    val maximumSpeedKmh: Double?
)

private val nmeaDateFormatter = DateTimeFormatter.ofPattern("ddMMyy", Locale.US)
private val nmeaTimeFormatter = DateTimeFormatter.ofPattern("HHmmss[.SSS][.SS]", Locale.US)

private fun parseNmeaDate(value: String): LocalDate? {
    return try {
        LocalDate.parse(value, nmeaDateFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun parseNmeaTime(value: String): LocalTime? {
    return try {
        LocalTime.parse(value, nmeaTimeFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}
