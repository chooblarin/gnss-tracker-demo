package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackRenderProjectorTest {
    @Test
    fun trackContentDefaultsToNoPhoneComparison() {
        val track = trackWith(listOf(TrackPoint(latitude = 35.0, longitude = 139.0)))

        val content = TrackContent(track, TrackRenderProjector.project(track))

        assertEquals(PhoneComparison.None, content.phoneComparison)
    }

    @Test
    fun keepsEndpointIndicesAndBuildsSourceRanges() {
        val track = trackWith(
            List(10) { index ->
                TrackPoint(latitude = 35.0 + index * 0.001, longitude = 139.0)
            }
        )

        val model = TrackRenderProjector.project(track, maximumPointCount = 3)

        assertEquals(listOf(0, 4, 9), model.points.map(RenderedTrackPoint::sourceIndex))
        assertEquals(listOf(0..4, 4..9), model.segments.map(RenderedTrackSegment::sourceIndexRange))
    }

    @Test
    fun boundsIncludeSourcePointsOmittedBySampling() {
        val track = trackWith(
            listOf(
                TrackPoint(latitude = 35.0, longitude = 139.0),
                TrackPoint(latitude = 36.0, longitude = 140.0),
                TrackPoint(latitude = 35.2, longitude = 139.2),
                TrackPoint(latitude = 35.3, longitude = 139.3)
            )
        )

        val bounds = TrackRenderProjector.project(track, maximumPointCount = 2).bounds

        assertEquals(35.0, bounds.minimumLatitude, 0.0)
        assertEquals(36.0, bounds.maximumLatitude, 0.0)
        assertEquals(139.0, bounds.minimumLongitude, 0.0)
        assertEquals(140.0, bounds.maximumLongitude, 0.0)
    }

    private fun trackWith(points: List<TrackPoint>) = Track(
        fileName = "render.nmea",
        points = points,
        summary = TrackSummary(
            pointCount = points.size,
            distanceMeters = 0.0,
            durationMillis = null,
            startedUtc = null,
            endedUtc = null,
            minimumAltitudeMeters = null,
            maximumAltitudeMeters = null,
            maximumSpeedKmh = null
        )
    )
}
