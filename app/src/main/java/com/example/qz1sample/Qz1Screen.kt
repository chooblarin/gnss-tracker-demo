package com.example.qz1sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Share
import com.example.qz1sample.ui.theme.Qz1SampleTheme
import java.util.Locale

@Composable
fun Qz1Screen(
    uiState: Qz1UiState,
    onRequestPermission: () -> Unit,
    onRequestPhoneLocation: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit,
    onRefreshLogs: () -> Unit,
    onOpenTrack: (String) -> Unit,
    onShareLog: (String) -> Unit,
    onDeleteLog: (String) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(Qz1Tab.Live.ordinal) }
    val selectedTab = Qz1Tab.entries.getOrElse(selectedTabIndex) { Qz1Tab.Live }
    var showRaw by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<RecordingSession?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "QZ1 SPP Monitor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                StatusCard(
                    uiState = uiState,
                    onRequestPermission = onRequestPermission,
                    onRefresh = onRefresh,
                    onClear = onClear
                )
            }

            item {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    Qz1Tab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTabIndex = tab.ordinal },
                            text = { Text(tab.label) }
                        )
                    }
                }
            }

            when (selectedTab) {
                Qz1Tab.Live -> {
                    item {
                        SectionTitle("Paired Devices")
                        if (uiState.devices.isEmpty()) {
                            Text("Pair QZ1 in Android Bluetooth settings, then tap Refresh.")
                        }
                    }

                    items(uiState.devices, key = { it.address }) { device ->
                        DeviceCard(
                            device = device,
                            selected = device.address == uiState.selectedAddress,
                            onSelect = { onSelect(device.address) }
                        )
                    }

                    item {
                        RecordingControls(
                            uiState = uiState,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect
                        )
                    }

                    item {
                        ParsedStatusCard(gnss = uiState.recording.gnss)
                    }

                    item {
                        PhoneGnssStatusCard(
                            state = uiState.recording.phoneRecording,
                            locationStatus = uiState.locationStatus,
                            onRequestLocation = onRequestPhoneLocation
                        )
                    }

                    item {
                        SectionCard(title = "NMEA Lines") {
                            BoundedLogText(
                                text = uiState.recording.nmeaLog,
                                emptyText = "Waiting for complete NMEA sentences."
                            )
                        }
                    }

                    item {
                        OutlinedButton(onClick = { showRaw = !showRaw }) {
                            Text(if (showRaw) "Hide Raw" else "Show Raw")
                        }
                    }

                    if (showRaw) {
                        item {
                            SectionCard(title = "Raw Stream") {
                                BoundedLogText(
                                    text = uiState.recording.rawLog,
                                    emptyText = "Waiting for SPP bytes."
                                )
                            }
                        }
                    }
                }

                Qz1Tab.Logs -> {
                    item {
                        SectionCard(title = "Saved Logs") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onRefreshLogs) {
                                    Text("Refresh")
                                }
                            }
                            if (uiState.savedLogs.isEmpty()) {
                                Text("No saved NMEA logs yet.")
                            }
                        }
                    }

                    items(uiState.savedLogs, key = { it.fileName }) { log ->
                        LogFileCard(
                            log = log,
                            isActive = uiState.isRecording(log.fileName),
                            onOpenTrack = { onOpenTrack(log.fileName) },
                            onShare = { onShareLog(log.fileName) },
                            onDelete = { deleteCandidate = log }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    deleteCandidate?.let { log ->
        DeleteLogDialog(
            log = log,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                deleteCandidate = null
                onDeleteLog(log.fileName)
            }
        )
    }
}

private enum class Qz1Tab(val label: String) {
    Live("Live"),
    Logs("Logs")
}

@Composable
private fun DeleteLogDialog(
    log: RecordingSession,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete NMEA log?") },
        text = {
            Text("${log.fileName}\n${log.sizeLabel} / ${log.modifiedLabel}")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ParsedStatusCard(gnss: GnssSnapshot) {
    SectionCard(title = "Parsed Status") {
        Text("Fix: ${gnss.fixStatus.label}")
        Text("Lat/Lon: ${formatCoordinatePair(gnss.latitude, gnss.longitude)}")
        Text("Altitude: ${formatMeters(gnss.altitudeMeters)}")
        Text("Speed: ${formatKmh(gnss.speedKmh)}")
        Text("Course: ${formatDegrees(gnss.courseDegrees)}")
        Text("Satellites: ${gnss.satellitesUsed?.toString() ?: "-"}")
        Text("HDOP: ${gnss.hdop?.format(1) ?: "-"}")
        Text("UTC: ${formatUtc(gnss.utcDate, gnss.utcTime)}")
        Text("Last sentence: ${gnss.lastSentenceType ?: "-"}")
    }
}

@Composable
private fun PhoneGnssStatusCard(
    state: PhoneRecordingState,
    locationStatus: LocationPermissionStatus,
    onRequestLocation: () -> Unit
) {
    SectionCard(title = "Phone GNSS") {
        when (state) {
            PhoneRecordingState.NotStarted -> Text("Not recording")
            PhoneRecordingState.Preparing -> Text("Preparing phone comparison")
            is PhoneRecordingState.Unavailable -> Text("Comparison unavailable: ${state.reason}")
            is PhoneRecordingState.Active -> {
                val phoneGnss = state.snapshot
                Text("Lat/Lon: ${formatCoordinatePair(phoneGnss.latitude, phoneGnss.longitude)}")
                Text("Accuracy: ${formatMeters(phoneGnss.accuracyMeters)}")
                Text("Saved: ${phoneGnss.savedPoints} points")
                Text(
                    "Satellites: ${phoneGnss.satellites.used} / " +
                        phoneGnss.satellites.visible
                )
                Text("Used GPS: ${phoneGnss.satellites.gpsUsed}")
                Text("Used QZSS: ${phoneGnss.satellites.qzssUsed}")
            }
        }
        if (locationStatus !is LocationPermissionStatus.Granted) {
            OutlinedButton(onClick = onRequestLocation) {
                Text("Enable phone comparison")
            }
        }
    }
}

@Composable
private fun StatusCard(
    uiState: Qz1UiState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    SectionCard(title = "Status") {
        Text(uiState.bluetoothStatus.displayText())
        Text(uiState.locationStatus.displayText())
        Text(uiState.notificationStatus.displayText())
        Text(uiState.recording.sessionState.displayText())
        uiState.selectionMessage?.let { Text(it) }
        Text("Received: ${uiState.recording.receivedBytes} bytes")
        Text("Saved: ${uiState.recording.savedLines} lines")
        Text("File: ${uiState.recording.fileName ?: "-"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequestPermission) {
                Text("Permission")
            }
            OutlinedButton(onClick = onRefresh) {
                Text("Refresh")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = uiState.canClearPreview
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun RecordingControls(
    uiState: Qz1UiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onConnect,
            enabled = uiState.canConnect,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (uiState.recording.sessionState is SessionState.Connecting) "Starting" else "Start")
        }
        OutlinedButton(
            onClick = onDisconnect,
            enabled = uiState.canDisconnect,
            modifier = Modifier.weight(1f)
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun BoundedLogText(
    text: String,
    emptyText: String
) {
    val scrollState = rememberScrollState()

    Text(
        text = text.ifEmpty { emptyText },
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .verticalScroll(scrollState),
        color = if (text.isEmpty()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun LogFileCard(
    log: RecordingSession,
    isActive: Boolean,
    onOpenTrack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = log.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("${log.sizeLabel} / ${log.modifiedLabel}")
            if (log.files.phoneLocation is PhoneLocationSidecar.Available) {
                Text(
                    text = "Phone comparison available",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isActive) {
                Text(
                    text = "Recording",
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                log.summary?.let { summary ->
                    Text(
                        text = "${formatTrackDuration(summary.durationMillis)} / " +
                            "${formatTrackDistance(summary.distanceMeters)} / " +
                            "${summary.pointCount} points",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    text = "Summary pending",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenTrack,
                    enabled = !isActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null)
                    Text("View track", modifier = Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export NMEA log")
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !isActive
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete NMEA log")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DeviceCard(
    device: DeviceRow,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (selected) "*" else "-")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (device.isQzCandidate) {
                Text(
                    text = "QZ1",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val FixStatus.label: String
    get() = when (this) {
        FixStatus.Unknown -> "Unknown"
        FixStatus.Invalid -> "Invalid"
        FixStatus.Valid -> "Valid"
    }

private fun formatCoordinatePair(latitude: Double?, longitude: Double?): String {
    if (latitude == null || longitude == null) return "-"
    return "${latitude.format(6)}, ${longitude.format(6)}"
}

private fun formatMeters(value: Double?): String {
    return value?.let { "${it.format(1)} m" } ?: "-"
}

private fun formatKmh(value: Double?): String {
    return value?.let { "${it.format(1)} km/h" } ?: "-"
}

private fun formatDegrees(value: Double?): String {
    return value?.let { "${it.format(1)} deg" } ?: "-"
}

private fun formatUtc(date: String?, time: String?): String {
    return listOfNotNull(date?.takeIf { it.isNotBlank() }, time?.takeIf { it.isNotBlank() })
        .joinToString(" ")
        .ifBlank { "-" }
}

private fun Double.format(digits: Int): String {
    return "%.${digits}f".format(Locale.US, this)
}

@Preview(showBackground = true)
@Composable
private fun Qz1ScreenPreview() {
    Qz1SampleTheme {
        Qz1Screen(
            uiState = Qz1UiState(
                bluetoothStatus = BluetoothStatus.Ready("Paired devices: 1"),
                devices = listOf(
                    DeviceRow(name = "QZ1-001", address = "00:11:22:33:44:55", isQzCandidate = true)
                ),
                selectedAddress = "00:11:22:33:44:55",
                savedLogs = listOf(
                    RecordingSession(
                        files = RecordingSessionFiles(
                            primary = NmeaLogFile(
                                fileName = "20260807_120000_QZ1.nmea",
                                sizeBytes = 2048,
                                modifiedAtMillis = 1_786_070_400_000
                            ),
                            phoneLocation = PhoneLocationSidecar.None
                        ),
                        summary = SavedLogSummary(
                            durationMillis = 3_600_000,
                            pointCount = 3_600,
                            distanceMeters = 12_450.0
                        )
                    )
                ),
                recording = RecordingSnapshot(
                    fileName = "20260807_120000_QZ1.nmea",
                    rawLog = "$" + "GNRMC,1*00\r\n",
                    nmeaLog = "$" + "GNRMC,1*00\n$" + "GNGGA,2*00\n",
                    receivedBytes = 14,
                    savedLines = 2,
                    gnss = GnssSnapshot(
                        latitude = 35.681236,
                        longitude = 139.767125,
                        altitudeMeters = 42.3,
                        speedKmh = 0.7,
                        courseDegrees = 182.1,
                        utcDate = "070826",
                        utcTime = "031422.00",
                        fixStatus = FixStatus.Valid,
                        satellitesUsed = 12,
                        hdop = 0.8,
                        lastSentenceType = "GNGGA"
                    )
                )
            ),
            onRequestPermission = {},
            onRequestPhoneLocation = {},
            onRefresh = {},
            onSelect = {},
            onConnect = {},
            onDisconnect = {},
            onClear = {},
            onRefreshLogs = {},
            onOpenTrack = {},
            onShareLog = {},
            onDeleteLog = {}
        )
    }
}
