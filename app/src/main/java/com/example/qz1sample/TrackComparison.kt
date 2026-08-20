package com.example.qz1sample

import kotlin.math.abs
import kotlin.math.ceil

data class TimedGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long
)

data class TrackComparisonSummary(
    val pairedPointCount: Int,
    val medianSeparationMeters: Double,
    val percentile95SeparationMeters: Double,
    val maximumSeparationMeters: Double
)

object PhoneTrackBuilder {
    fun build(fileName: String, samples: List<PhoneLocationSample>): PhoneTrack {
        val points = samples.map { sample ->
            PhoneTrackPoint(
                latitude = sample.latitude,
                longitude = sample.longitude,
                epochMillis = sample.epochMillis,
                altitudeMeters = sample.altitudeMeters,
                accuracyMeters = sample.accuracyMeters,
                speedKmh = sample.speedMetersPerSecond?.times(3.6),
                courseDegrees = sample.bearingDegrees,
                satellitesUsed = sample.satellitesUsed
            )
        }
        return PhoneTrackAssembler.build(fileName, points)
    }
}

object TrackComparisonBuilder {
    fun build(
        qz1Track: Track,
        phoneTrack: PhoneTrack,
        maximumTimeDeltaMillis: Long = 1_500L
    ): TrackComparisonSummary? {
        return build(
            qz1Points = qz1Track.points.mapNotNull { point ->
                point.epochMillis?.let { epochMillis ->
                    TimedGeoPoint(point.latitude, point.longitude, epochMillis)
                }
            },
            phonePoints = phoneTrack.points.map { point ->
                TimedGeoPoint(point.latitude, point.longitude, point.epochMillis)
            },
            maximumTimeDeltaMillis = maximumTimeDeltaMillis
        )
    }

    internal fun build(
        qz1Points: List<TimedGeoPoint>,
        phonePoints: List<TimedGeoPoint>,
        maximumTimeDeltaMillis: Long
    ): TrackComparisonSummary? {
        if (qz1Points.isEmpty() || phonePoints.isEmpty()) return null

        val sortedQz1Points = qz1Points.sortedBy(TimedGeoPoint::epochMillis)
        val sortedPhonePoints = phonePoints.sortedBy(TimedGeoPoint::epochMillis)
        var qz1Index = 0
        var phoneIndex = 0
        val separations = buildList {
            while (qz1Index < sortedQz1Points.size && phoneIndex < sortedPhonePoints.size) {
                val qz1Point = sortedQz1Points[qz1Index]
                val phonePoint = sortedPhonePoints[phoneIndex]
                val currentDelta = timeDelta(phonePoint, qz1Point.epochMillis)
                val nextQz1Delta = sortedQz1Points.getOrNull(qz1Index + 1)
                    ?.let { timeDelta(phonePoint, it.epochMillis) }
                    ?: Long.MAX_VALUE
                val nextPhoneDelta = sortedPhonePoints.getOrNull(phoneIndex + 1)
                    ?.let { timeDelta(it, qz1Point.epochMillis) }
                    ?: Long.MAX_VALUE

                val nearestAlternative = minOf(nextQz1Delta, nextPhoneDelta)
                if (nearestAlternative < currentDelta) {
                    if (nextQz1Delta <= nextPhoneDelta) {
                        qz1Index += 1
                    } else {
                        phoneIndex += 1
                    }
                    continue
                }

                if (currentDelta <= maximumTimeDeltaMillis) {
                    add(
                        GeoDistance.meters(
                            firstLatitude = qz1Point.latitude,
                            firstLongitude = qz1Point.longitude,
                            secondLatitude = phonePoint.latitude,
                            secondLongitude = phonePoint.longitude
                        )
                    )
                    qz1Index += 1
                    phoneIndex += 1
                } else if (qz1Point.epochMillis < phonePoint.epochMillis) {
                    qz1Index += 1
                } else {
                    phoneIndex += 1
                }
            }
        }.sorted()
        if (separations.isEmpty()) return null

        return TrackComparisonSummary(
            pairedPointCount = separations.size,
            medianSeparationMeters = separations.median(),
            percentile95SeparationMeters = separations.percentile(0.95),
            maximumSeparationMeters = separations.last()
        )
    }

    private fun timeDelta(point: TimedGeoPoint, epochMillis: Long): Long {
        return abs(point.epochMillis - epochMillis)
    }

    private fun List<Double>.percentile(value: Double): Double {
        val index = (ceil(size * value).toInt() - 1).coerceIn(indices)
        return this[index]
    }

    private fun List<Double>.median(): Double {
        val middle = size / 2
        return if (size % 2 == 0) {
            (this[middle - 1] + this[middle]) / 2.0
        } else {
            this[middle]
        }
    }
}
