package com.example.qz1sample

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

object TrackSelectionPolicy {
    fun tapRadiusMeters(latitude: Double, zoom: Float): Double {
        val metersPerPixel = 156_543.03392 *
            cos(Math.toRadians(latitude)) /
            2.0.pow(zoom.toDouble())
        return max(5.0, metersPerPixel * 32.0)
    }
}
