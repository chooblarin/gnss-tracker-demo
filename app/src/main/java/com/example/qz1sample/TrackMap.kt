package com.example.qz1sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.max

@Composable
fun TrackMap(
    content: TrackContent,
    selectedPointIndex: Int?,
    centerRequest: Int,
    onSelectPoint: (Int) -> Unit,
    onSelectNearestPoint: (Double, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = content.track
    val coordinates = remember(content.renderModel) {
        content.renderModel.points.map {
            LatLng(it.coordinate.latitude, it.coordinate.longitude)
        }
    }
    val phoneComparison = content.phoneComparison as? PhoneComparison.Available
    val phoneCoordinates = remember(phoneComparison) {
        phoneComparison?.renderModel?.points
            ?.map { LatLng(it.coordinate.latitude, it.coordinate.longitude) }
            .orEmpty()
    }
    val allCoordinates = remember(coordinates, phoneCoordinates) {
        (coordinates + phoneCoordinates).distinct()
    }
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember(track.fileName) { mutableStateOf(false) }
    val selectedPoint = selectedPointIndex?.let { track.points.getOrNull(it) }
    val startCoordinate = remember(track) {
        track.points.first().let { LatLng(it.latitude, it.longitude) }
    }
    val endCoordinate = remember(track) {
        track.points.last().let { LatLng(it.latitude, it.longitude) }
    }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(mapLoaded, allCoordinates) {
        if (!mapLoaded || allCoordinates.isEmpty()) return@LaunchedEffect
        val update = if (allCoordinates.size == 1) {
            CameraUpdateFactory.newLatLngZoom(allCoordinates.first(), 17f)
        } else {
            val trackBounds = phoneComparison?.renderModel?.bounds?.let(
                content.renderModel.bounds::include
            ) ?: content.renderModel.bounds
            val bounds = LatLngBounds(
                LatLng(trackBounds.minimumLatitude, trackBounds.minimumLongitude),
                LatLng(trackBounds.maximumLatitude, trackBounds.maximumLongitude)
            )
            CameraUpdateFactory.newLatLngBounds(bounds, 96)
        }
        cameraPositionState.animate(update)
    }

    LaunchedEffect(centerRequest) {
        val point = selectedPoint ?: return@LaunchedEffect
        if (!mapLoaded || centerRequest == 0) return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                max(cameraPositionState.position.zoom, 17f)
            )
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        onMapLoaded = { mapLoaded = true },
        onMapClick = { position ->
            onSelectNearestPoint(
                position.latitude,
                position.longitude,
                TrackSelectionPolicy.tapRadiusMeters(
                    latitude = position.latitude,
                    zoom = cameraPositionState.position.zoom
                )
            )
        }
    ) {
        if (phoneCoordinates.size > 1) {
            Polyline(points = phoneCoordinates, color = PHONE_TRACK_COLOR, width = 7f)
        }
        if (coordinates.size > 1) {
            Polyline(points = coordinates, color = primaryColor, width = 8f)
        }
        Marker(
            state = rememberUpdatedMarkerState(startCoordinate),
            title = "Start",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
            onClick = {
                onSelectPoint(0)
                true
            }
        )
        if (coordinates.size > 1) {
            Marker(
                state = rememberUpdatedMarkerState(endCoordinate),
                title = "End",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                onClick = {
                    onSelectPoint(track.points.lastIndex)
                    true
                }
            )
        }
        selectedPoint?.let { point ->
            Marker(
                state = rememberUpdatedMarkerState(LatLng(point.latitude, point.longitude)),
                title = point.utcLabel ?: "Selected point",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }
    }
}

val PHONE_TRACK_COLOR = Color(0xFFD32F2F)
