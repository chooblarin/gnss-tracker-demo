package com.example.qz1sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TrackPointDetailPanel(
    track: Track,
    selection: TrackSelection,
    onSelectPoint: (Int) -> Unit,
    onCenterPoint: () -> Unit,
    onClearSelection: () -> Unit
) {
    val point = track.points[selection.pointIndex]
    val sliderMaximum = max(track.points.lastIndex, 1).toFloat()

    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = point.utcLabel?.let { "$it UTC" } ?: "UTC unavailable",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${selection.pointIndex + 1} / ${track.points.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Slider(
                value = selection.pointIndex.toFloat(),
                onValueChange = { onSelectPoint(it.roundToInt()) },
                modifier = Modifier.semantics {
                    stateDescription = "Point ${selection.pointIndex + 1} of ${track.points.size}"
                },
                enabled = track.points.size > 1,
                valueRange = 0f..sliderMaximum
            )

            Text(
                text = "${point.latitude.formatTrack(6)}, ${point.longitude.formatTrack(6)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            TrackSummaryRow("Speed", point.speedKmh?.let { "${it.formatTrack(1)} km/h" } ?: "-")
            TrackSummaryRow("Altitude", point.altitudeMeters?.let { "${it.formatTrack(1)} m" } ?: "-")
            TrackSummaryRow("Course", point.courseDegrees?.let { "${it.formatTrack(1)} deg" } ?: "-")
            TrackSummaryRow("From start", formatTrackDistance(point.distanceFromStartMeters))
            TrackSummaryRow("Satellites", point.satellitesUsed?.toString() ?: "-")
            TrackSummaryRow("HDOP", point.hdop?.formatTrack(1) ?: "-")
            TrackSummaryRow("Fix quality", point.fixQuality?.toString() ?: "-")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onCenterPoint, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null)
                    Text("Center", modifier = Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear point selection")
                }
            }
        }
    }
}
