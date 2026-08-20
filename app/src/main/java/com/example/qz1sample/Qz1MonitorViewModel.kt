package com.example.qz1sample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Qz1MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(Qz1UiState())
    val uiState: StateFlow<Qz1UiState> = _uiState
    private val sessionRepository = RecordingSessionRepository(application)
    private var savedLogsJob: Job? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() = getApplication<Application>()
            .getSystemService(BluetoothManager::class.java)
            ?.adapter

    init {
        viewModelScope.launch {
            WorkManager.getInstance(application)
                .getWorkInfosByTagFlow(SESSION_INDEX_WORK_TAG)
                .map { workInfos ->
                    workInfos
                        .filter { it.state == WorkInfo.State.SUCCEEDED }
                        .map { it.id }
                        .toSet()
                }
                .distinctUntilChanged()
                .collect { refreshSavedLogs() }
        }
        viewModelScope.launch {
            Qz1RecordingStatusStore.snapshot.collect { recording ->
                val previous = _uiState.value.recording
                _uiState.update {
                    it.copy(recording = recording)
                }
                if (
                    previous.fileName != recording.fileName ||
                    recording.sessionState is SessionState.Disconnected ||
                    recording.sessionState is SessionState.Failed
                ) {
                    refreshSavedLogs()
                }
            }
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPreciseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update { it.copy(bluetoothStatus = BluetoothStatus.PermissionDenied) }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                notificationStatus = if (granted) {
                    NotificationStatus.Granted
                } else {
                    NotificationStatus.Denied
                }
            )
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                locationStatus = if (granted) {
                    LocationPermissionStatus.Granted
                } else {
                    LocationPermissionStatus.Denied
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        when (val ready = resolveBluetooth()) {
            is BluetoothReady.NotReady -> {
                _uiState.update {
                    it.copy(
                        bluetoothStatus = ready.bluetoothStatus,
                        devices = emptyList(),
                        selectedAddress = null,
                        selectionMessage = null
                    )
                }
            }
            is BluetoothReady.Ready -> {
                val rows = ready.adapter.bondedDevices
                    .map { device ->
                        DeviceRow(
                            name = device.name ?: "Unknown device",
                            address = device.address,
                            isQzCandidate = device.name?.contains("QZ1", ignoreCase = true) == true
                        )
                    }
                    .sortedWith(
                        compareByDescending<DeviceRow> { it.isQzCandidate }.thenBy { it.name }
                    )

                _uiState.update { state ->
                    val selectedAddress = state.selectedAddress?.takeIf { address ->
                        rows.any { it.address == address }
                    } ?: rows.firstOrNull { it.isQzCandidate }?.address

                    state.copy(
                        bluetoothStatus = if (rows.isEmpty()) {
                            BluetoothStatus.Ready(
                                "No paired devices. Pair QZ1 in Android Bluetooth settings first."
                            )
                        } else {
                            BluetoothStatus.Ready("Paired devices: ${rows.size}")
                        },
                        devices = rows,
                        selectedAddress = selectedAddress,
                        selectionMessage = null
                    )
                }
            }
        }
    }

    fun selectDevice(address: String) {
        _uiState.update { it.copy(selectedAddress = address, selectionMessage = null) }
    }

    @SuppressLint("MissingPermission")
    fun connectSelectedDevice() {
        when (val ready = resolveBluetooth()) {
            is BluetoothReady.NotReady -> {
                _uiState.update { it.copy(bluetoothStatus = ready.bluetoothStatus) }
                return
            }
            is BluetoothReady.Ready -> {
                val address = _uiState.value.selectedAddress
                if (address == null) {
                    _uiState.update {
                        it.copy(selectionMessage = "Select a paired QZ1 device")
                    }
                    return
                }

                val device = ready.adapter.bondedDevices.firstOrNull { it.address == address }
                if (device == null) {
                    _uiState.update {
                        it.copy(selectionMessage = "Selected device is no longer paired")
                    }
                    refreshPairedDevices()
                    return
                }

                Qz1RecordingService.start(
                    context = getApplication(),
                    address = device.address,
                    deviceName = device.name ?: device.address
                )
            }
        }
    }

    fun disconnect() {
        Qz1RecordingService.stop(getApplication())
    }

    fun clearLogs() {
        if (_uiState.value.canClearPreview) {
            Qz1RecordingStatusStore.clearPreviewLogs()
        }
    }

    fun refreshSavedLogs() {
        savedLogsJob?.cancel()
        savedLogsJob = viewModelScope.launch {
            val sessions = sessionRepository.scan()
            _uiState.update { it.copy(savedLogs = sessions) }
        }
    }

    fun shareLog(fileName: String) {
        val context = getApplication<Application>()
        val uri = runCatching {
            NmeaLogStore.shareUri(context, fileName)
        }.getOrElse {
            _uiState.update { state ->
                state.copy(selectionMessage = "Log file not found: $fileName")
            }
            return
        }

        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, fileName)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sendIntent.clipData = ClipData.newUri(context.contentResolver, fileName, uri)

        val chooser = Intent.createChooser(sendIntent, "Export NMEA log")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(chooser)
    }

    fun deleteLog(fileName: String) {
        if (_uiState.value.isRecording(fileName)) {
            _uiState.update {
                it.copy(selectionMessage = "Stop recording before deleting the active log")
            }
            return
        }

        savedLogsJob?.cancel()
        viewModelScope.launch {
            sessionRepository.delete(fileName)
            refreshSavedLogs()
        }
    }

    private fun resolveBluetooth(): BluetoothReady {
        if (!hasBluetoothPermission()) {
            return BluetoothReady.NotReady(BluetoothStatus.PermissionRequired)
        }

        val adapter = bluetoothAdapter
            ?: return BluetoothReady.NotReady(BluetoothStatus.Unavailable)

        return if (adapter.isEnabled) {
            BluetoothReady.Ready(adapter)
        } else {
            BluetoothReady.NotReady(BluetoothStatus.Off)
        }
    }
}

private sealed interface BluetoothReady {
    data class Ready(val adapter: BluetoothAdapter) : BluetoothReady
    data class NotReady(val bluetoothStatus: BluetoothStatus) : BluetoothReady
}
