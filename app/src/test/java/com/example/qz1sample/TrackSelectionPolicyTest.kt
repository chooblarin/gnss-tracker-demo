package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSelectionPolicyTest {
    @Test
    fun selectionRadiusGrowsWhenMapIsZoomedOut() {
        val zoomedIn = TrackSelectionPolicy.tapRadiusMeters(latitude = 35.0, zoom = 18f)
        val zoomedOut = TrackSelectionPolicy.tapRadiusMeters(latitude = 35.0, zoom = 12f)

        assertTrue(zoomedOut > zoomedIn)
    }

    @Test
    fun selectionRadiusHasFiveMeterMinimum() {
        val radius = TrackSelectionPolicy.tapRadiusMeters(latitude = 35.0, zoom = 30f)

        assertEquals(5.0, radius, 0.001)
    }
}
