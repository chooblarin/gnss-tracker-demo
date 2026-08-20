package com.example.qz1sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackDetailScreen(
    openState: TrackUiState.Open,
    onBack: () -> Unit,
    onSelectPoint: (Int) -> Unit,
    onSelectNearestPoint: (Double, Double, Double) -> Unit,
    onClearSelection: () -> Unit,
    onShare: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = openState.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export NMEA log")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val loadState = openState.loadState) {
            TrackLoadState.Loading -> TrackCenteredMessage(
                modifier = Modifier.padding(innerPadding),
                title = "Reading NMEA log",
                showProgress = true
            )
            TrackLoadState.NoLocationData -> TrackCenteredMessage(
                modifier = Modifier.padding(innerPadding),
                title = "No location data",
                message = "This log does not contain a valid GGA or RMC position."
            )
            is TrackLoadState.Failed -> TrackCenteredMessage(
                modifier = Modifier.padding(innerPadding),
                title = "Could not open track",
                message = loadState.message
            )
            is TrackLoadState.Ready -> TrackContent(
                ready = loadState,
                onSelectPoint = onSelectPoint,
                onSelectNearestPoint = onSelectNearestPoint,
                onClearSelection = onClearSelection,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun TrackContent(
    ready: TrackLoadState.Ready,
    onSelectPoint: (Int) -> Unit,
    onSelectNearestPoint: (Double, Double, Double) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = ready.content.track
    var centerRequest by remember(track.fileName) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        if (BuildConfig.MAPS_API_KEY_CONFIGURED) {
            TrackMap(
                content = ready.content,
                selectedPointIndex = ready.selection?.pointIndex,
                centerRequest = centerRequest,
                onSelectPoint = onSelectPoint,
                onSelectNearestPoint = onSelectNearestPoint,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            TrackCenteredMessage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                title = "Google Maps API key required",
                message = "Add MAPS_API_KEY to local.properties, then rebuild the app."
            )
        }

        val selection = ready.selection
        if (selection == null) {
            TrackSummaryPanel(ready.content)
        } else {
            TrackPointDetailPanel(
                track = track,
                selection = selection,
                onSelectPoint = onSelectPoint,
                onCenterPoint = { centerRequest += 1 },
                onClearSelection = onClearSelection
            )
        }
    }
}

@Composable
private fun TrackCenteredMessage(
    modifier: Modifier = Modifier,
    title: String,
    message: String? = null,
    showProgress: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
