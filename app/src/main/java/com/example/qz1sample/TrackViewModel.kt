package com.example.qz1sample

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TrackRepository = FileTrackRepository(application)
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<TrackUiState>(TrackUiState.Closed)
    val uiState: StateFlow<TrackUiState> = _uiState

    private var loadingJob: Job? = null
    private var nearestPointJob: Job? = null

    fun open(fileName: String) {
        loadingJob?.cancel()
        nearestPointJob?.cancel()
        _uiState.value = TrackUiState.Open(fileName, TrackLoadState.Loading)
        loadingJob = viewModelScope.launch {
            val loadState = when (val result = repository.load(fileName)) {
                is TrackRepositoryResult.Loaded -> {
                    if (result.track.points.isEmpty()) {
                        TrackLoadState.NoLocationData
                    } else {
                        val content = withContext(Dispatchers.Default) {
                            val phoneComparison = result.phoneTrack?.let { phoneTrack ->
                                PhoneComparison.Available(
                                    track = phoneTrack,
                                    renderModel = TrackRenderProjector.project(phoneTrack),
                                    separation = TrackComparisonBuilder.build(
                                        result.track,
                                        phoneTrack
                                    )
                                )
                            } ?: PhoneComparison.None
                            TrackContent(
                                track = result.track,
                                renderModel = TrackRenderProjector.project(result.track),
                                phoneComparison = phoneComparison
                            )
                        }
                        TrackLoadState.Ready(content)
                    }
                }
                TrackRepositoryResult.NotFound -> TrackLoadState.Failed("Log file not found")
                TrackRepositoryResult.ReadFailed -> {
                    TrackLoadState.Failed("Could not read the NMEA log")
                }
            }
            updateOpen(fileName) { loadState }
        }
    }

    fun close() {
        loadingJob?.cancel()
        nearestPointJob?.cancel()
        loadingJob = null
        nearestPointJob = null
        _uiState.value = TrackUiState.Closed
    }

    fun navigateBack() {
        val open = _uiState.value as? TrackUiState.Open
        val ready = open?.loadState as? TrackLoadState.Ready
        if (open != null && ready?.selection != null) {
            nearestPointJob?.cancel()
            _uiState.value = open.copy(loadState = ready.copy(selection = null))
        } else {
            close()
        }
    }

    fun selectPoint(index: Int) {
        val open = _uiState.value as? TrackUiState.Open ?: return
        val ready = open.loadState as? TrackLoadState.Ready ?: return
        if (ready.content.track.points.isEmpty()) return
        nearestPointJob?.cancel()
        val selection = TrackSelection(index.coerceIn(ready.content.track.points.indices))
        if (selection != ready.selection) {
            _uiState.value = open.copy(loadState = ready.copy(selection = selection))
        }
    }

    fun selectNearestPoint(
        latitude: Double,
        longitude: Double,
        maximumDistanceMeters: Double
    ) {
        val open = _uiState.value as? TrackUiState.Open ?: return
        val ready = open.loadState as? TrackLoadState.Ready ?: return
        nearestPointJob?.cancel()
        nearestPointJob = viewModelScope.launch {
            val index = withContext(Dispatchers.Default) {
                TrackHitTester.findNearestPoint(
                    content = ready.content,
                    latitude = latitude,
                    longitude = longitude,
                    maximumDistanceMeters = maximumDistanceMeters
                )
            } ?: return@launch

            val currentOpen = _uiState.value as? TrackUiState.Open ?: return@launch
            val currentReady = currentOpen.loadState as? TrackLoadState.Ready ?: return@launch
            if (currentOpen.fileName == open.fileName && currentReady.content === ready.content) {
                _uiState.value = currentOpen.copy(
                    loadState = currentReady.copy(selection = TrackSelection(index))
                )
            }
        }
    }

    fun clearSelection() {
        val open = _uiState.value as? TrackUiState.Open ?: return
        val ready = open.loadState as? TrackLoadState.Ready ?: return
        nearestPointJob?.cancel()
        if (ready.selection != null) {
            _uiState.value = open.copy(loadState = ready.copy(selection = null))
        }
    }

    private inline fun updateOpen(
        fileName: String,
        loadState: () -> TrackLoadState
    ) {
        val open = _uiState.value as? TrackUiState.Open ?: return
        if (open.fileName == fileName) {
            _uiState.value = open.copy(loadState = loadState())
        }
    }
}

data class TrackSelection(val pointIndex: Int)

sealed interface TrackUiState {
    data object Closed : TrackUiState
    data class Open(
        val fileName: String,
        val loadState: TrackLoadState
    ) : TrackUiState
}

sealed interface TrackLoadState {
    data object Loading : TrackLoadState
    data class Ready(
        val content: TrackContent,
        val selection: TrackSelection? = null
    ) : TrackLoadState
    data object NoLocationData : TrackLoadState
    data class Failed(val message: String) : TrackLoadState
}
