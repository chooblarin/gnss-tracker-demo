package com.example.qz1sample

import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticSampleTest {
    @Test
    fun buildsTrackAndComparisonFromPublishedSamples() {
        val nmeaLines = resourceLines("synthetic_track.nmea")
        val phoneSamples = resourceLines("synthetic_track.phone.csv")
            .drop(1)
            .mapNotNull(PhoneLocationCsvCodec::decode)

        val qz1Track = TrackBuilder.build("synthetic_track.nmea", nmeaLines.asSequence())
        val phoneTrack = PhoneTrackBuilder.build("synthetic_track.phone.csv", phoneSamples)
        val comparison = requireNotNull(
            TrackComparisonBuilder.build(qz1Track, phoneTrack)
        )

        assertEquals(10, qz1Track.points.size)
        assertEquals(10, phoneTrack.points.size)
        assertEquals(10, comparison.pairedPointCount)
        assertTrue(comparison.medianSeparationMeters > 0.0)
    }

    private fun resourceLines(name: String): List<String> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing sample resource: $name"
        }
        return BufferedReader(InputStreamReader(stream)).use(BufferedReader::readLines)
    }
}
