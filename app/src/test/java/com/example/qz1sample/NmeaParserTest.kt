package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaParserTest {
    @Test
    fun parsesGgaPositionAndFixFields() {
        val result = NmeaParser.parse(
            "$" + "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"
        )

        assertTrue(result is NmeaParseResult.Parsed)
        val event = (result as NmeaParseResult.Parsed).event
        assertTrue(event is NmeaEvent.Gga)
        val gga = event as NmeaEvent.Gga
        assertEquals(48.1173, gga.latitude ?: 0.0, 0.000001)
        assertEquals(11.516666, gga.longitude ?: 0.0, 0.000001)
        assertEquals(1, gga.fixQuality)
        assertEquals(8, gga.satellitesUsed)
        assertEquals(0.9, gga.hdop ?: 0.0, 0.000001)
        assertEquals(545.4, gga.altitudeMeters ?: 0.0, 0.000001)
    }

    @Test
    fun parsesRmcSpeedAndCourse() {
        val result = NmeaParser.parse(
            "$" + "GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        )

        assertTrue(result is NmeaParseResult.Parsed)
        val event = (result as NmeaParseResult.Parsed).event
        assertTrue(event is NmeaEvent.Rmc)
        val rmc = event as NmeaEvent.Rmc
        assertEquals("A", rmc.status)
        assertEquals(22.4, rmc.speedKnots ?: 0.0, 0.000001)
        assertEquals(84.4, rmc.courseDegrees ?: 0.0, 0.000001)
        assertEquals("230394", rmc.utcDate)
    }

    @Test
    fun rejectsInvalidChecksum() {
        val result = NmeaParser.parse(
            "$" + "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00"
        )

        assertEquals(NmeaParseResult.InvalidChecksum, result)
    }

    @Test
    fun acceptsSentenceWithoutChecksum() {
        val result = NmeaParser.parse(
            "$" + "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"
        )

        assertTrue(result is NmeaParseResult.Parsed)
    }
}
