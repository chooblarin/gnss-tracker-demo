package com.example.qz1sample

data class SourceFingerprint(
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
) {
    companion object {
        fun from(logFile: NmeaLogFile) = SourceFingerprint(
            fileName = logFile.fileName,
            sizeBytes = logFile.sizeBytes,
            lastModifiedMillis = logFile.modifiedAtMillis
        )
    }
}

data class SavedLogSummary(
    val durationMillis: Long?,
    val pointCount: Int,
    val distanceMeters: Double
)

data class SessionMetadata(
    val schemaVersion: Int,
    val parserVersion: Int,
    val source: SourceFingerprint,
    val summary: SavedLogSummary
) {
    fun isValidFor(logFile: NmeaLogFile): Boolean {
        return schemaVersion == CURRENT_SCHEMA_VERSION &&
            parserVersion == CURRENT_PARSER_VERSION &&
            source == SourceFingerprint.from(logFile)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val CURRENT_PARSER_VERSION = 1
    }
}

data class RecordingSession(
    val files: RecordingSessionFiles,
    val summary: SavedLogSummary? = null
) {
    val fileName: String
        get() = files.fileName

    val sizeLabel: String
        get() = files.primary.sizeLabel

    val modifiedLabel: String
        get() = files.primary.modifiedLabel
}

object SessionMetadataIndexer {
    fun build(track: Track, source: SourceFingerprint): SessionMetadata {
        return SessionMetadata(
            schemaVersion = SessionMetadata.CURRENT_SCHEMA_VERSION,
            parserVersion = SessionMetadata.CURRENT_PARSER_VERSION,
            source = source,
            summary = SavedLogSummary(
                durationMillis = track.summary.durationMillis,
                pointCount = track.summary.pointCount,
                distanceMeters = track.summary.distanceMeters
            )
        )
    }
}
