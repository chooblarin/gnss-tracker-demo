package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssStateReducerTest {
    @Test
    fun combinesGgaAndRmcIntoSnapshot() {
        val gga = NmeaEvent.Gga(
            sentenceType = "GNGGA",
            utcTime = "123519",
            latitude = 48.1173,
            longitude = 11.516666,
            fixQuality = 1,
            satellitesUsed = 8,
            hdop = 0.9,
            altitudeMeters = 545.4
        )
        val rmc = NmeaEvent.Rmc(
            sentenceType = "GNRMC",
            utcTime = "123520",
            status = "A",
            latitude = 48.1174,
            longitude = 11.5167,
            speedKnots = 10.0,
            courseDegrees = 84.4,
            utcDate = "230394"
        )

        val afterGga = GnssStateReducer.reduce(GnssSnapshot(), gga, 1000)
        val afterRmc = GnssStateReducer.reduce(afterGga, rmc, 2000)

        assertEquals(48.1174, afterRmc.latitude ?: 0.0, 0.000001)
        assertEquals(11.5167, afterRmc.longitude ?: 0.0, 0.000001)
        assertEquals(545.4, afterRmc.altitudeMeters ?: 0.0, 0.000001)
        assertEquals(18.52, afterRmc.speedKmh ?: 0.0, 0.000001)
        assertEquals(84.4, afterRmc.courseDegrees ?: 0.0, 0.000001)
        assertEquals(8, afterRmc.satellitesUsed)
        assertEquals(0.9, afterRmc.hdop ?: 0.0, 0.000001)
        assertEquals(FixStatus.Valid, afterRmc.fixStatus)
        assertEquals("GNRMC", afterRmc.lastSentenceType)
    }

    @Test
    fun invalidFixClearsCurrentPositionAndMotionFields() {
        val valid = GnssSnapshot(
            latitude = 48.1174,
            longitude = 11.5167,
            altitudeMeters = 545.4,
            speedKmh = 18.52,
            courseDegrees = 84.4,
            fixStatus = FixStatus.Valid
        )
        val invalid = NmeaEvent.Rmc(
            sentenceType = "GNRMC",
            utcTime = "123520",
            status = "V",
            latitude = null,
            longitude = null,
            speedKnots = null,
            courseDegrees = null,
            utcDate = "230394"
        )

        val reduced = GnssStateReducer.reduce(valid, invalid, 2000)

        assertEquals(FixStatus.Invalid, reduced.fixStatus)
        assertEquals(null, reduced.latitude)
        assertEquals(null, reduced.longitude)
        assertEquals(null, reduced.altitudeMeters)
        assertEquals(null, reduced.speedKmh)
        assertEquals(null, reduced.courseDegrees)
    }
}
