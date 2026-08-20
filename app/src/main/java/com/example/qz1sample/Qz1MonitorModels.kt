package com.example.qz1sample

data class DeviceRow(
    val name: String,
    val address: String,
    val isQzCandidate: Boolean
)

data class Qz1UiState(
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.PermissionRequired,
    val locationStatus: LocationPermissionStatus = LocationPermissionStatus.Required,
    val notificationStatus: NotificationStatus = NotificationStatus.Unknown,
    val devices: List<DeviceRow> = emptyList(),
    val savedLogs: List<RecordingSession> = emptyList(),
    val selectedAddress: String? = null,
    val selectionMessage: String? = null,
    val recording: RecordingSnapshot = RecordingSnapshot()
) {
    val canConnect: Boolean
        get() = selectedAddress != null &&
            bluetoothStatus is BluetoothStatus.Ready &&
            recording.sessionState !is SessionState.Connecting &&
            recording.sessionState !is SessionState.Connected

    val canDisconnect: Boolean
        get() = recording.sessionState is SessionState.Connecting ||
            recording.sessionState is SessionState.Connected

    val canClearPreview: Boolean
        get() = recording.sessionState !is SessionState.Connecting &&
            recording.sessionState !is SessionState.Connected

    fun isRecording(fileName: String): Boolean {
        return recording.fileName == fileName && canDisconnect
    }
}

sealed interface BluetoothStatus {
    data object PermissionRequired : BluetoothStatus
    data object PermissionDenied : BluetoothStatus
    data object Unavailable : BluetoothStatus
    data object Off : BluetoothStatus
    data class Ready(val message: String) : BluetoothStatus
}

sealed interface NotificationStatus {
    data object Unknown : NotificationStatus
    data object Granted : NotificationStatus
    data object Denied : NotificationStatus
}

sealed interface LocationPermissionStatus {
    data object Required : LocationPermissionStatus
    data object Denied : LocationPermissionStatus
    data object Granted : LocationPermissionStatus
}

sealed interface SessionState {
    data object Idle : SessionState
    data class Connecting(val deviceLabel: String) : SessionState
    data class Connected(val deviceLabel: String) : SessionState
    data class Disconnected(val reason: String) : SessionState
    data class Failed(val message: String) : SessionState
}

fun BluetoothStatus.displayText(): String {
    return when (this) {
        BluetoothStatus.PermissionRequired -> "Nearby devices permission required"
        BluetoothStatus.PermissionDenied -> "Nearby devices permission denied"
        BluetoothStatus.Unavailable -> "Bluetooth is not available"
        BluetoothStatus.Off -> "Bluetooth is off"
        is BluetoothStatus.Ready -> message
    }
}

fun NotificationStatus.displayText(): String {
    return when (this) {
        NotificationStatus.Unknown -> "Notification permission unknown"
        NotificationStatus.Granted -> "Notification permission granted"
        NotificationStatus.Denied -> "Notification permission denied"
    }
}

fun LocationPermissionStatus.displayText(): String {
    return when (this) {
        LocationPermissionStatus.Required -> "Phone comparison needs precise location"
        LocationPermissionStatus.Denied -> "Phone comparison unavailable: location denied"
        LocationPermissionStatus.Granted -> "Phone GNSS recording enabled"
    }
}

fun SessionState.displayText(): String {
    return when (this) {
        SessionState.Idle -> "Not recording"
        is SessionState.Connecting -> "Connecting to $deviceLabel"
        is SessionState.Connected -> "Connected to $deviceLabel"
        is SessionState.Disconnected -> "Disconnected: $reason"
        is SessionState.Failed -> message
    }
}
