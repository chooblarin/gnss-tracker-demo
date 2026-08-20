package com.example.qz1sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

data class Qz1MonitorActions(
    val requestPermission: () -> Unit,
    val requestPhoneLocation: () -> Unit,
    val refresh: () -> Unit,
    val select: (String) -> Unit,
    val connect: () -> Unit,
    val disconnect: () -> Unit,
    val clear: () -> Unit,
    val refreshLogs: () -> Unit,
    val openTrack: (String) -> Unit,
    val shareLog: (String) -> Unit,
    val deleteLog: (String) -> Unit
)

data class TrackActions(
    val navigateBack: () -> Unit,
    val selectPoint: (Int) -> Unit,
    val selectNearestPoint: (Double, Double, Double) -> Unit,
    val clearSelection: () -> Unit
)

@Composable
fun Qz1App(
    monitorState: Qz1UiState,
    trackState: TrackUiState,
    monitorActions: Qz1MonitorActions,
    trackActions: TrackActions
) {
    val stateHolder = rememberSaveableStateHolder()

    when (trackState) {
        TrackUiState.Closed -> stateHolder.SaveableStateProvider(MONITOR_SCREEN_KEY) {
            Qz1Screen(
                uiState = monitorState,
                onRequestPermission = monitorActions.requestPermission,
                onRequestPhoneLocation = monitorActions.requestPhoneLocation,
                onRefresh = monitorActions.refresh,
                onSelect = monitorActions.select,
                onConnect = monitorActions.connect,
                onDisconnect = monitorActions.disconnect,
                onClear = monitorActions.clear,
                onRefreshLogs = monitorActions.refreshLogs,
                onOpenTrack = monitorActions.openTrack,
                onShareLog = monitorActions.shareLog,
                onDeleteLog = monitorActions.deleteLog
            )
        }
        is TrackUiState.Open -> stateHolder.SaveableStateProvider(TRACK_SCREEN_KEY) {
            TrackDetailScreen(
                openState = trackState,
                onBack = trackActions.navigateBack,
                onSelectPoint = trackActions.selectPoint,
                onSelectNearestPoint = trackActions.selectNearestPoint,
                onClearSelection = trackActions.clearSelection,
                onShare = { monitorActions.shareLog(trackState.fileName) }
            )
        }
    }
}

private const val MONITOR_SCREEN_KEY = "monitor"
private const val TRACK_SCREEN_KEY = "track"
