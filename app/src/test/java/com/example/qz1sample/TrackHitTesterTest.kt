package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackHitTesterTest {
    @Test
    fun refinesNearestRenderedSegmentToOriginalSample() {
        val points = List(11) { index ->
            TrackPoint(latitude = 35.0, longitude = 139.0 + index * 0.001)
        }
        val content = contentWith(points, maximumPointCount = 2)

        val index = TrackHitTester.findNearestPoint(
            content = content,
            latitude = 35.0,
            longitude = 139.0071,
            maximumDistanceMeters = 20.0
        )

        assertEquals(7, index)
    }

    @Test
    fun returnsNullWhenTapIsTooFarFromEverySegment() {
        val content = contentWith(
            listOf(
                TrackPoint(latitude = 35.0, longitude = 139.0),
                TrackPoint(latitude = 35.001, longitude = 139.0)
            )
        )

        val index = TrackHitTester.findNearestPoint(
            content = content,
            latitude = 35.01,
            longitude = 139.0,
            maximumDistanceMeters = 20.0
        )

        assertNull(index)
    }

    @Test
    fun selectsSinglePointInsideMaximumDistance() {
        val content = contentWith(
            listOf(TrackPoint(latitude = 35.0, longitude = 139.0))
        )

        val index = TrackHitTester.findNearestPoint(
            content = content,
            latitude = 35.0,
            longitude = 139.0,
            maximumDistanceMeters = 1.0
        )

        assertEquals(0, index)
    }

    private fun contentWith(
        points: List<TrackPoint>,
        maximumPointCount: Int = 5_000
    ): TrackContent {
        val track = Track(
            fileName = "hit-test.nmea",
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
        return TrackContent(
            track = track,
            renderModel = TrackRenderProjector.project(track, maximumPointCount)
        )
    }
}
