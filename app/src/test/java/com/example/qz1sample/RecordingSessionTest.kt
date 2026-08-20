package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionTest {
    @Test
    fun metadataIsValidOnlyForCurrentVersionsAndExactSource() {
        val logFile = NmeaLogFile(
            fileName = "sample.nmea",
            sizeBytes = 120,
            modifiedAtMillis = 1_000
        )
        val metadata = metadataFor(logFile)

        assertTrue(metadata.isValidFor(logFile))
        assertFalse(metadata.isValidFor(logFile.copy(sizeBytes = 121)))
        assertFalse(metadata.isValidFor(logFile.copy(modifiedAtMillis = 1_001)))
        assertFalse(metadata.copy(parserVersion = 0).isValidFor(logFile))
        assertFalse(metadata.copy(schemaVersion = 0).isValidFor(logFile))
    }

    @Test
    fun indexerKeepsOnlyListSummaryFields() {
        val track = TrackBuilder.build(
            fileName = "sample.nmea",
            lines = sequenceOf(
                "$" + "GPRMC,120000.000,A,3500.000,N,13900.000,E,10.0,90.0,070826,,",
                "$" + "GPRMC,120001.000,A,3500.060,N,13900.000,E,12.0,90.0,070826,,"
            )
        )

        val metadata = SessionMetadataIndexer.build(
            track = track,
            source = SourceFingerprint("sample.nmea", 120, 1_000)
        )

        assertEquals(2, metadata.summary.pointCount)
        assertEquals(1_000L, metadata.summary.durationMillis)
        assertTrue(metadata.summary.distanceMeters > 100.0)
    }

    @Test
    fun indexerSupportsLogsWithoutValidPositions() {
        val metadata = SessionMetadataIndexer.build(
            track = TrackBuilder.build("empty.nmea", sequenceOf("not nmea")),
            source = SourceFingerprint("empty.nmea", 8, 1_000)
        )

        assertEquals(0, metadata.summary.pointCount)
        assertEquals(null, metadata.summary.durationMillis)
        assertEquals(0.0, metadata.summary.distanceMeters, 0.0)
    }

    @Test
    fun uniqueIndexWorkNameIsStablePerFile() {
        assertEquals(sessionIndexWorkName("sample.nmea"), sessionIndexWorkName("sample.nmea"))
        assertFalse(sessionIndexWorkName("sample.nmea") == sessionIndexWorkName("other.nmea"))
    }

    private fun metadataFor(logFile: NmeaLogFile) = SessionMetadata(
        schemaVersion = SessionMetadata.CURRENT_SCHEMA_VERSION,
        parserVersion = SessionMetadata.CURRENT_PARSER_VERSION,
        source = SourceFingerprint.from(logFile),
        summary = SavedLogSummary(
            durationMillis = null,
            pointCount = 0,
            distanceMeters = 0.0
        )
    )
}
