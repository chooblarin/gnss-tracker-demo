package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackBuilderTest {
    @Test
    fun buildsTrackAndMergesGgaWithRmcAtTheSameTime() {
        val lines = sequenceOf(
            "$" + "GPRMC,120000.000,A,3500.000,N,13900.000,E,10.0,90.0,070826,,",
            "$" + "GPGGA,120000.000,3500.000,N,13900.000,E,1,10,0.8,42.0,M,,M,,",
            "$" + "GPRMC,120001.000,A,3500.060,N,13900.000,E,12.0,90.0,070826,,"
        )

        val track = TrackBuilder.build("sample.nmea", lines)

        assertEquals(2, track.points.size)
        assertEquals(42.0, track.points.first().altitudeMeters ?: 0.0, 0.001)
        assertEquals(10, track.points.first().satellitesUsed)
        assertEquals(0.8, track.points.first().hdop ?: 0.0, 0.001)
        assertEquals(1, track.points.first().fixQuality)
        assertEquals(0.0, track.points.first().distanceFromStartMeters, 0.001)
        assertEquals(
            track.summary.distanceMeters,
            track.points.last().distanceFromStartMeters,
            0.001
        )
        assertEquals(1_000L, track.summary.durationMillis)
        assertTrue(track.summary.distanceMeters > 100.0)
        assertEquals(22.224, track.summary.maximumSpeedKmh ?: 0.0, 0.001)
    }

    @Test
    fun ignoresInvalidAndMalformedPositions() {
        val lines = sequenceOf(
            "$" + "GPRMC,120000.000,V,3500.000,N,13900.000,E,10.0,90.0,070826,,",
            "$" + "GPGGA,120001.000,3500.000,N,13900.000,E,0,00,9.9,0.0,M,,M,,",
            "not nmea"
        )

        val track = TrackBuilder.build("invalid.nmea", lines)

        assertTrue(track.points.isEmpty())
        assertEquals(0, track.summary.pointCount)
    }

    @Test
    fun doesNotCarryGgaOnlyMetricsIntoTheNextRmcSample() {
        val lines = sequenceOf(
            "$" + "GPGGA,120000.000,3500.000,N,13900.000,E,1,10,0.8,42.0,M,,M,,",
            "$" + "GPRMC,120001.000,A,3500.060,N,13900.000,E,12.0,90.0,070826,,"
        )

        val track = TrackBuilder.build("separate-samples.nmea", lines)
        val rmcPoint = track.points.last()

        assertEquals(null, rmcPoint.altitudeMeters)
        assertEquals(null, rmcPoint.satellitesUsed)
        assertEquals(null, rmcPoint.hdop)
        assertEquals(null, rmcPoint.fixQuality)
    }
}
