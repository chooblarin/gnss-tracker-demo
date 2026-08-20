package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLocationLogTest {
    @Test
    fun codecRoundTripsLocationAndSatelliteFields() {
        val sample = PhoneLocationSample(
            epochMillis = 1_723_000_000_123,
            elapsedRealtimeNanos = 987_654_321,
            latitude = 35.681236,
            longitude = 139.767125,
            altitudeMeters = 41.25,
            accuracyMeters = 3.5,
            speedMetersPerSecond = 2.25,
            bearingDegrees = 123.4,
            satellitesVisible = 24,
            satellitesUsed = 14,
            gpsUsed = 8,
            qzssUsed = 3
        )

        assertEquals(sample, PhoneLocationCsvCodec.decode(PhoneLocationCsvCodec.encode(sample)))
    }

    @Test
    fun codecKeepsOptionalFieldsEmpty() {
        val sample = PhoneLocationSample(
            epochMillis = 1_000,
            elapsedRealtimeNanos = 2_000,
            latitude = 35.0,
            longitude = 139.0,
            altitudeMeters = null,
            accuracyMeters = null,
            speedMetersPerSecond = null,
            bearingDegrees = null,
            satellitesVisible = null,
            satellitesUsed = null,
            gpsUsed = null,
            qzssUsed = null
        )

        assertEquals(sample, PhoneLocationCsvCodec.decode(PhoneLocationCsvCodec.encode(sample)))
    }

    @Test
    fun codecRejectsMalformedRows() {
        assertNull(PhoneLocationCsvCodec.decode("1000,broken"))
    }

    @Test
    fun sidecarRequiresAtLeastOneValidSample() {
        assertFalse(
            PhoneLocationLogStore.containsValidSample(
                sequenceOf(PhoneLocationCsvCodec.HEADER, "partial,row")
            )
        )
        assertTrue(
            PhoneLocationLogStore.containsValidSample(
                sequenceOf(PhoneLocationCsvCodec.HEADER, PhoneLocationCsvCodec.encode(sample()))
            )
        )
    }

    private fun sample() = PhoneLocationSample(
        epochMillis = 1_000,
        elapsedRealtimeNanos = 2_000,
        latitude = 35.0,
        longitude = 139.0,
        altitudeMeters = null,
        accuracyMeters = null,
        speedMetersPerSecond = null,
        bearingDegrees = null,
        satellitesVisible = null,
        satellitesUsed = null,
        gpsUsed = null,
        qzssUsed = null
    )
}
