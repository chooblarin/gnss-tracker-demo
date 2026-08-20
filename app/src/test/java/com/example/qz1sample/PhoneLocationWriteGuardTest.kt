package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneLocationWriteGuardTest {
    @Test
    fun reportsFirstWriteFailureAndSkipsLaterSamples() {
        var writeCount = 0
        val failures = mutableListOf<String>()
        val guard = PhoneLocationWriteGuard(
            write = {
                writeCount += 1
                error("disk full")
            },
            onFailure = failures::add
        )

        assertFalse(guard.append(sample()))
        assertFalse(guard.append(sample()))
        assertEquals(1, writeCount)
        assertEquals(1, failures.size)
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
