package com.example.qz1sample

object GnssStateReducer {
    fun reduce(snapshot: GnssSnapshot, event: NmeaEvent, updatedAtMillis: Long): GnssSnapshot {
        return when (event) {
            is NmeaEvent.Gga -> {
                val nextStatus = fixStatusFromGga(event.fixQuality, snapshot.fixStatus)
                snapshot.copy(
                    latitude = event.latitude ?: snapshot.latitude,
                    longitude = event.longitude ?: snapshot.longitude,
                    altitudeMeters = event.altitudeMeters ?: snapshot.altitudeMeters,
                    utcTime = event.utcTime ?: snapshot.utcTime,
                    fixQuality = event.fixQuality ?: snapshot.fixQuality,
                    satellitesUsed = event.satellitesUsed ?: snapshot.satellitesUsed,
                    hdop = event.hdop ?: snapshot.hdop,
                    lastSentenceType = event.sentenceType,
                    lastUpdatedMillis = updatedAtMillis
                ).withFixStatus(nextStatus)
            }
            is NmeaEvent.Rmc -> {
                val nextStatus = fixStatusFromRmc(event.status, snapshot.fixStatus)
                snapshot.copy(
                    latitude = event.latitude ?: snapshot.latitude,
                    longitude = event.longitude ?: snapshot.longitude,
                    speedKmh = event.speedKnots?.let(::knotsToKilometersPerHour) ?: snapshot.speedKmh,
                    courseDegrees = event.courseDegrees ?: snapshot.courseDegrees,
                    utcTime = event.utcTime ?: snapshot.utcTime,
                    utcDate = event.utcDate ?: snapshot.utcDate,
                    lastSentenceType = event.sentenceType,
                    lastUpdatedMillis = updatedAtMillis
                ).withFixStatus(nextStatus)
            }
        }
    }

    private fun fixStatusFromGga(fixQuality: Int?, fallback: FixStatus): FixStatus {
        return when (fixQuality) {
            0 -> FixStatus.Invalid
            null -> fallback
            else -> FixStatus.Valid
        }
    }

    private fun fixStatusFromRmc(status: String?, fallback: FixStatus): FixStatus {
        return when (status) {
            "A" -> FixStatus.Valid
            "V" -> FixStatus.Invalid
            else -> fallback
        }
    }

    private fun GnssSnapshot.withFixStatus(nextStatus: FixStatus): GnssSnapshot {
        return when (nextStatus) {
            FixStatus.Invalid -> copy(
                latitude = null,
                longitude = null,
                altitudeMeters = null,
                speedKmh = null,
                courseDegrees = null,
                fixStatus = nextStatus
            )
            else -> copy(fixStatus = nextStatus)
        }
    }
}

data class GnssSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val speedKmh: Double? = null,
    val courseDegrees: Double? = null,
    val utcTime: String? = null,
    val utcDate: String? = null,
    val fixStatus: FixStatus = FixStatus.Unknown,
    val fixQuality: Int? = null,
    val satellitesUsed: Int? = null,
    val hdop: Double? = null,
    val lastSentenceType: String? = null,
    val lastUpdatedMillis: Long? = null
)

enum class FixStatus {
    Unknown,
    Invalid,
    Valid
}
