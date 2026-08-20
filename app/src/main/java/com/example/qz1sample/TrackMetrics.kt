package com.example.qz1sample

import java.time.Instant

internal data class TrackMetricSample(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long?,
    val utcLabel: String?,
    val altitudeMeters: Double?,
    val speedKmh: Double?
)

internal data class CalculatedTrackMetrics(
    val cumulativeDistances: List<Double>,
    val summary: TrackSummary
)

internal object TrackMetricsCalculator {
    fun calculate(samples: List<TrackMetricSample>): CalculatedTrackMetrics {
        var distance = 0.0
        val cumulativeDistances = samples.mapIndexed { index, sample ->
            if (index > 0) {
                val previous = samples[index - 1]
                distance += GeoDistance.meters(
                    firstLatitude = previous.latitude,
                    firstLongitude = previous.longitude,
                    secondLatitude = sample.latitude,
                    secondLongitude = sample.longitude
                )
            }
            distance
        }
        val startMillis = samples.firstNotNullOfOrNull(TrackMetricSample::epochMillis)
        val endMillis = samples.asReversed()
            .firstNotNullOfOrNull(TrackMetricSample::epochMillis)
        return CalculatedTrackMetrics(
            cumulativeDistances = cumulativeDistances,
            summary = TrackSummary(
                pointCount = samples.size,
                distanceMeters = cumulativeDistances.lastOrNull() ?: 0.0,
                durationMillis = if (
                    startMillis != null && endMillis != null && endMillis >= startMillis
                ) {
                    endMillis - startMillis
                } else {
                    null
                },
                startedUtc = samples.firstOrNull()?.utcLabel,
                endedUtc = samples.lastOrNull()?.utcLabel,
                minimumAltitudeMeters = samples.mapNotNull {
                    it.altitudeMeters
                }.minOrNull(),
                maximumAltitudeMeters = samples.mapNotNull {
                    it.altitudeMeters
                }.maxOrNull(),
                maximumSpeedKmh = samples.mapNotNull { it.speedKmh }.maxOrNull()
            )
        )
    }
}

internal object TrackAssembler {
    fun build(fileName: String, points: List<TrackPoint>): Track {
        val metrics = TrackMetricsCalculator.calculate(
            points.map { point ->
                TrackMetricSample(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    epochMillis = point.epochMillis,
                    utcLabel = point.utcLabel,
                    altitudeMeters = point.altitudeMeters,
                    speedKmh = point.speedKmh
                )
            }
        )
        return Track(
            fileName = fileName,
            points = points.mapIndexed { index, point ->
                point.copy(distanceFromStartMeters = metrics.cumulativeDistances[index])
            },
            summary = metrics.summary
        )
    }
}

data class PhoneTrack(
    val fileName: String,
    val points: List<PhoneTrackPoint>,
    val summary: TrackSummary
)

data class PhoneTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long,
    val altitudeMeters: Double?,
    val accuracyMeters: Double?,
    val speedKmh: Double?,
    val courseDegrees: Double?,
    val satellitesUsed: Int?,
    val distanceFromStartMeters: Double = 0.0
)

internal object PhoneTrackAssembler {
    fun build(fileName: String, points: List<PhoneTrackPoint>): PhoneTrack {
        val metrics = TrackMetricsCalculator.calculate(
            points.map { point ->
                TrackMetricSample(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    epochMillis = point.epochMillis,
                    utcLabel = Instant.ofEpochMilli(point.epochMillis).toString(),
                    altitudeMeters = point.altitudeMeters,
                    speedKmh = point.speedKmh
                )
            }
        )
        return PhoneTrack(
            fileName = fileName,
            points = points.mapIndexed { index, point ->
                point.copy(distanceFromStartMeters = metrics.cumulativeDistances[index])
            },
            summary = metrics.summary
        )
    }
}
