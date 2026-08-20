package com.example.qz1sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TrackSummaryPanel(content: TrackContent) {
    val summary = content.track.summary
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackSummaryMetric(
                    "Distance",
                    formatTrackDistance(summary.distanceMeters),
                    Modifier.weight(1f)
                )
                TrackSummaryMetric(
                    "Duration",
                    formatTrackDuration(summary.durationMillis),
                    Modifier.weight(1f)
                )
                TrackSummaryMetric("Points", summary.pointCount.toString(), Modifier.weight(1f))
            }
            HorizontalDivider()
            TrackSummaryRow("Start UTC", summary.startedUtc ?: "-")
            TrackSummaryRow("End UTC", summary.endedUtc ?: "-")
            TrackSummaryRow(
                "Altitude",
                formatTrackRange(
                    summary.minimumAltitudeMeters,
                    summary.maximumAltitudeMeters,
                    "m"
                )
            )
            TrackSummaryRow(
                "Max speed",
                summary.maximumSpeedKmh?.let { "${it.formatTrack(1)} km/h" } ?: "-"
            )
            val phoneComparison = content.phoneComparison
            if (phoneComparison is PhoneComparison.Available) {
                val phoneTrack = phoneComparison.track
                HorizontalDivider()
                Text(
                    "QZ1 / Phone comparison",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TrackLegend()
                TrackSummaryRow("Phone points", phoneTrack.summary.pointCount.toString())
                TrackSummaryRow(
                    "Phone distance",
                    formatTrackDistance(phoneTrack.summary.distanceMeters)
                )
                val comparison = phoneComparison.separation
                if (comparison == null) {
                    TrackSummaryRow("Time-aligned samples", "-")
                } else {
                    TrackSummaryRow(
                        "Time-aligned samples",
                        comparison.pairedPointCount.toString()
                    )
                    TrackSummaryRow(
                        "Median separation",
                        formatTrackDistance(comparison.medianSeparationMeters)
                    )
                    TrackSummaryRow(
                        "95% separation",
                        formatTrackDistance(comparison.percentile95SeparationMeters)
                    )
                    TrackSummaryRow(
                        "Max separation",
                        formatTrackDistance(comparison.maximumSeparationMeters)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TrackLegendItem("QZ1", MaterialTheme.colorScheme.primary)
        TrackLegendItem("Phone", PHONE_TRACK_COLOR)
    }
}

@Composable
private fun TrackLegendItem(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TrackSummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TrackSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(2f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}
