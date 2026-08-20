package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneGnssStartPolicyTest {
    @Test
    fun missingLocationPermissionDisablesOnlyPhoneComparison() {
        val reason = PhoneGnssStartPolicy.unavailableReason(
            hasPreciseLocationPermission = false,
            hasLocationService = true,
            isGpsProviderEnabled = true
        )

        assertEquals("precise location permission not granted", reason)
    }

    @Test
    fun readyPhoneGnssHasNoUnavailableReason() {
        val reason = PhoneGnssStartPolicy.unavailableReason(
            hasPreciseLocationPermission = true,
            hasLocationService = true,
            isGpsProviderEnabled = true
        )

        assertNull(reason)
    }
}
