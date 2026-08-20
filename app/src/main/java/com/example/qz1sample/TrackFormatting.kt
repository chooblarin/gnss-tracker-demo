package com.example.qz1sample

import java.util.Locale

fun formatTrackDistance(meters: Double): String {
    return if (meters >= 1_000) {
        "${(meters / 1_000).formatTrack(2)} km"
    } else {
        "${meters.formatTrack(0)} m"
    }
}

fun formatTrackDuration(milliseconds: Long?): String {
    if (milliseconds == null) return "-"
    val totalSeconds = milliseconds / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

fun formatTrackRange(minimum: Double?, maximum: Double?, unit: String): String {
    if (minimum == null || maximum == null) return "-"
    return "${minimum.formatTrack(1)}-${maximum.formatTrack(1)} $unit"
}

fun Double.formatTrack(digits: Int): String = "%.${digits}f".format(Locale.US, this)
