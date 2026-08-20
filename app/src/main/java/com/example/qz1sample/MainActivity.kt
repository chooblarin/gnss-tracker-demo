package com.example.qz1sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.qz1sample.ui.theme.Qz1SampleTheme

class MainActivity : ComponentActivity() {
    private lateinit var monitorViewModel: Qz1MonitorViewModel
    private lateinit var trackViewModel: TrackViewModel

    private val appPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        grants[Manifest.permission.BLUETOOTH_CONNECT]?.let { granted ->
            monitorViewModel.onBluetoothPermissionResult(granted)
        }
        grants[Manifest.permission.POST_NOTIFICATIONS]?.let { granted ->
            monitorViewModel.onNotificationPermissionResult(granted)
        }
        if (
            grants.containsKey(Manifest.permission.ACCESS_FINE_LOCATION) ||
            grants.containsKey(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            monitorViewModel.onLocationPermissionResult(
                monitorViewModel.hasPreciseLocationPermission()
            )
        }
        if (monitorViewModel.hasBluetoothPermission()) {
            monitorViewModel.refreshPairedDevices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitorViewModel = ViewModelProvider(this)[Qz1MonitorViewModel::class.java]
        trackViewModel = ViewModelProvider(this)[TrackViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            val uiState by monitorViewModel.uiState.collectAsState()
            val trackUiState by trackViewModel.uiState.collectAsState()
            val monitorActions = remember {
                Qz1MonitorActions(
                    requestPermission = ::requestCorePermissions,
                    requestPhoneLocation = ::requestPhoneLocationPermission,
                    refresh = monitorViewModel::refreshPairedDevices,
                    select = monitorViewModel::selectDevice,
                    connect = monitorViewModel::connectSelectedDevice,
                    disconnect = monitorViewModel::disconnect,
                    clear = monitorViewModel::clearLogs,
                    refreshLogs = monitorViewModel::refreshSavedLogs,
                    openTrack = trackViewModel::open,
                    shareLog = monitorViewModel::shareLog,
                    deleteLog = monitorViewModel::deleteLog
                )
            }
            val trackActions = remember {
                TrackActions(
                    navigateBack = trackViewModel::navigateBack,
                    selectPoint = trackViewModel::selectPoint,
                    selectNearestPoint = trackViewModel::selectNearestPoint,
                    clearSelection = trackViewModel::clearSelection
                )
            }
            Qz1SampleTheme {
                Qz1App(
                    monitorState = uiState,
                    trackState = trackUiState,
                    monitorActions = monitorActions,
                    trackActions = trackActions
                )
            }
        }

        monitorViewModel.onNotificationPermissionResult(hasNotificationPermission())
        monitorViewModel.onLocationPermissionResult(
            monitorViewModel.hasPreciseLocationPermission()
        )
        requestCorePermissions()
    }

    private fun requestCorePermissions() {
        val permissions = buildList {
            if (!monitorViewModel.hasBluetoothPermission()) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (!hasNotificationPermission()) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isEmpty()) {
            monitorViewModel.refreshPairedDevices()
        } else {
            appPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestPhoneLocationPermission() {
        if (monitorViewModel.hasPreciseLocationPermission()) {
            monitorViewModel.onLocationPermissionResult(true)
            return
        }
        appPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
