package com.example.qz1sample

import org.junit.Assert.assertTrue
import org.junit.Test

class Qz1UiStateTest {
    @Test
    fun qz1CanConnectWithoutPhoneLocationPermission() {
        val state = Qz1UiState(
            bluetoothStatus = BluetoothStatus.Ready("ready"),
            locationStatus = LocationPermissionStatus.Denied,
            selectedAddress = "00:11:22:33:44:55"
        )

        assertTrue(state.canConnect)
    }
}
