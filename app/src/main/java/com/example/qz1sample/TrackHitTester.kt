package com.example.qz1sample

import kotlin.math.cos
import kotlin.math.hypot

object TrackHitTester {
    fun findNearestPoint(
        content: TrackContent,
        latitude: Double,
        longitude: Double,
        maximumDistanceMeters: Double
    ): Int? {
        if (maximumDistanceMeters < 0.0) return null

        val nearestSegment = content.renderModel.segments.minByOrNull { segment ->
            distanceToSegmentMeters(
                latitude = latitude,
                longitude = longitude,
                segment = segment
            )
        }

        if (nearestSegment == null) {
            val onlyPoint = content.renderModel.points.singleOrNull() ?: return null
            return onlyPoint.sourceIndex.takeIf {
                GeoDistance.meters(
                    firstLatitude = latitude,
                    firstLongitude = longitude,
                    secondLatitude = onlyPoint.coordinate.latitude,
                    secondLongitude = onlyPoint.coordinate.longitude
                ) <= maximumDistanceMeters
            }
        }

        val segmentDistance = distanceToSegmentMeters(
            latitude = latitude,
            longitude = longitude,
            segment = nearestSegment
        )
        if (segmentDistance > maximumDistanceMeters) return null

        return nearestSegment.sourceIndexRange
            .asSequence()
            .filter { it in content.track.points.indices }
            .minByOrNull { sourceIndex ->
                val point = content.track.points[sourceIndex]
                GeoDistance.meters(
                    firstLatitude = latitude,
                    firstLongitude = longitude,
                    secondLatitude = point.latitude,
                    secondLongitude = point.longitude
                )
            }
    }

    private fun distanceToSegmentMeters(
        latitude: Double,
        longitude: Double,
        segment: RenderedTrackSegment
    ): Double {
        val start = segment.start.coordinate.projectAround(latitude, longitude)
        val end = segment.end.coordinate.projectAround(latitude, longitude)
        val deltaX = end.x - start.x
        val deltaY = end.y - start.y
        val lengthSquared = deltaX * deltaX + deltaY * deltaY
        if (lengthSquared == 0.0) return hypot(start.x, start.y)

        val fraction = (-(start.x * deltaX + start.y * deltaY) / lengthSquared)
            .coerceIn(0.0, 1.0)
        return hypot(
            start.x + fraction * deltaX,
            start.y + fraction * deltaY
        )
    }

    private fun GeoCoordinate.projectAround(
        originLatitude: Double,
        originLongitude: Double
    ): CartesianPoint {
        val longitudeDelta = normalizeLongitude(longitude - originLongitude)
        return CartesianPoint(
            x = Math.toRadians(longitudeDelta) *
                EARTH_RADIUS_METERS *
                cos(Math.toRadians(originLatitude)),
            y = Math.toRadians(latitude - originLatitude) * EARTH_RADIUS_METERS
        )
    }

    private fun normalizeLongitude(value: Double): Double {
        return ((value + 540.0) % 360.0) - 180.0
    }

    private data class CartesianPoint(val x: Double, val y: Double)

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
