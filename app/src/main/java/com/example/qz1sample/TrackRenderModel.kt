package com.example.qz1sample

data class TrackContent(
    val track: Track,
    val renderModel: TrackRenderModel,
    val phoneComparison: PhoneComparison = PhoneComparison.None
)

sealed interface PhoneComparison {
    data object None : PhoneComparison
    data class Available(
        val track: PhoneTrack,
        val renderModel: TrackRenderModel,
        val separation: TrackComparisonSummary?
    ) : PhoneComparison
}

data class TrackRenderModel(
    val points: List<RenderedTrackPoint>,
    val segments: List<RenderedTrackSegment>,
    val bounds: TrackBounds
)

data class RenderedTrackPoint(
    val sourceIndex: Int,
    val coordinate: GeoCoordinate
)

data class GeoCoordinate(val latitude: Double, val longitude: Double)

data class RenderedTrackSegment(
    val start: RenderedTrackPoint,
    val end: RenderedTrackPoint,
    val sourceIndexRange: IntRange
)

data class TrackBounds(
    val minimumLatitude: Double,
    val minimumLongitude: Double,
    val maximumLatitude: Double,
    val maximumLongitude: Double
) {
    fun include(other: TrackBounds): TrackBounds {
        return TrackBounds(
            minimumLatitude = minOf(minimumLatitude, other.minimumLatitude),
            minimumLongitude = minOf(minimumLongitude, other.minimumLongitude),
            maximumLatitude = maxOf(maximumLatitude, other.maximumLatitude),
            maximumLongitude = maxOf(maximumLongitude, other.maximumLongitude)
        )
    }
}

object TrackRenderProjector {
    fun project(track: Track, maximumPointCount: Int = 5_000): TrackRenderModel {
        return project(
            coordinates = track.points.map { GeoCoordinate(it.latitude, it.longitude) },
            maximumPointCount = maximumPointCount
        )
    }

    fun project(track: PhoneTrack, maximumPointCount: Int = 5_000): TrackRenderModel {
        return project(
            coordinates = track.points.map { GeoCoordinate(it.latitude, it.longitude) },
            maximumPointCount = maximumPointCount
        )
    }

    private fun project(
        coordinates: List<GeoCoordinate>,
        maximumPointCount: Int
    ): TrackRenderModel {
        require(coordinates.isNotEmpty())
        require(maximumPointCount >= 2)

        val renderedPoints = sample(coordinates, maximumPointCount)
        return TrackRenderModel(
            points = renderedPoints,
            segments = renderedPoints.zipWithNext { start, end ->
                RenderedTrackSegment(
                    start = start,
                    end = end,
                    sourceIndexRange = start.sourceIndex..end.sourceIndex
                )
            },
            bounds = TrackBounds(
                minimumLatitude = coordinates.minOf(GeoCoordinate::latitude),
                minimumLongitude = coordinates.minOf(GeoCoordinate::longitude),
                maximumLatitude = coordinates.maxOf(GeoCoordinate::latitude),
                maximumLongitude = coordinates.maxOf(GeoCoordinate::longitude)
            )
        )
    }

    private fun sample(
        points: List<GeoCoordinate>,
        maximumPointCount: Int
    ): List<RenderedTrackPoint> {
        if (points.size <= maximumPointCount) {
            return points.mapIndexed(::RenderedTrackPoint)
        }

        val step = points.lastIndex.toDouble() / (maximumPointCount - 1)
        return buildList(maximumPointCount) {
            repeat(maximumPointCount) { index ->
                val sourceIndex = if (index == maximumPointCount - 1) {
                    points.lastIndex
                } else {
                    (index * step).toInt().coerceAtMost(points.lastIndex)
                }
                add(RenderedTrackPoint(sourceIndex, points[sourceIndex]))
            }
        }
    }
}
