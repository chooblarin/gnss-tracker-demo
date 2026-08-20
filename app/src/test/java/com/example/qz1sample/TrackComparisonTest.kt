package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackComparisonTest {
    @Test
    fun phoneTrackPreservesTimestampsAndAccuracy() {
        val track = PhoneTrackBuilder.build(
            fileName = "sample.nmea",
            samples = listOf(
                sample(1_000, 35.0, 139.0, accuracyMeters = 4.0),
                sample(2_000, 35.001, 139.0, accuracyMeters = 3.0)
            )
        )

        assertEquals(2, track.points.size)
        assertEquals(1_000L, track.points.first().epochMillis)
        assertEquals(4.0, track.points.first().accuracyMeters ?: 0.0, 0.0)
        assertEquals(1_000L, track.summary.durationMillis)
        assertTrue(track.summary.distanceMeters > 100.0)
    }

    @Test
    fun comparisonPairsNearestSamplesAndSummarizesSeparation() {
        val comparison = TrackComparisonBuilder.build(
            qz1Points = listOf(
                TimedGeoPoint(35.0, 139.0, 1_000),
                TimedGeoPoint(35.0, 139.0, 2_000)
            ),
            phonePoints = listOf(
                TimedGeoPoint(35.0, 139.0, 1_100),
                TimedGeoPoint(35.001, 139.0, 2_100)
            ),
            maximumTimeDeltaMillis = 1_500
        )

        requireNotNull(comparison)
        assertEquals(2, comparison.pairedPointCount)
        assertTrue(comparison.medianSeparationMeters in 50.0..60.0)
        assertTrue(comparison.percentile95SeparationMeters > 100.0)
        assertEquals(
            comparison.percentile95SeparationMeters,
            comparison.maximumSeparationMeters,
            0.001
        )
    }

    @Test
    fun comparisonRejectsSamplesOutsideTimeWindow() {
        val comparison = TrackComparisonBuilder.build(
            qz1Points = listOf(TimedGeoPoint(35.0, 139.0, 1_000)),
            phonePoints = listOf(TimedGeoPoint(35.0, 139.0, 3_000)),
            maximumTimeDeltaMillis = 500
        )

        assertNull(comparison)
    }

    @Test
    fun comparisonDoesNotReusePhoneSampleForFasterQz1Stream() {
        val comparison = TrackComparisonBuilder.build(
            qz1Points = listOf(
                TimedGeoPoint(35.0, 139.0, 0),
                TimedGeoPoint(35.0, 139.0, 100),
                TimedGeoPoint(35.0, 139.0, 1_000)
            ),
            phonePoints = listOf(
                TimedGeoPoint(35.0, 139.0, 50),
                TimedGeoPoint(35.0, 139.0, 1_050)
            ),
            maximumTimeDeltaMillis = 500
        )

        requireNotNull(comparison)
        assertEquals(2, comparison.pairedPointCount)
    }

    private fun sample(
        epochMillis: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?
    ) = PhoneLocationSample(
        epochMillis = epochMillis,
        elapsedRealtimeNanos = epochMillis * 1_000_000,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = null,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = null,
        bearingDegrees = null,
        satellitesVisible = 20,
        satellitesUsed = 12,
        gpsUsed = 8,
        qzssUsed = 2
    )
}
