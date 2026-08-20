package com.example.qz1sample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SppSession(private val device: BluetoothDevice) {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    suspend fun open() {
        socket = withContext(Dispatchers.IO) {
            connectSpp(device)
        }
    }

    suspend fun readLoop(onChunk: suspend (ReceivedChunk) -> Unit): ReadStopReason {
        val connectedSocket = socket ?: return ReadStopReason.NotConnected

        return withContext(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER_SIZE)
            var stopReason: ReadStopReason? = null
            while (stopReason == null) {
                ensureActive()
                val count = connectedSocket.inputStream.read(buffer)
                if (count < 0) {
                    stopReason = ReadStopReason.StreamClosed
                    continue
                }
                if (count == 0) continue

                onChunk(
                    ReceivedChunk(
                        text = String(buffer, 0, count, StandardCharsets.US_ASCII),
                        byteCount = count
                    )
                )
            }
            stopReason
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun connectSpp(device: BluetoothDevice): BluetoothSocket {
        val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            secureSocket.connect()
            return secureSocket
        } catch (secureError: IOException) {
            secureSocket.closeQuietly()

            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            try {
                insecureSocket.connect()
                return insecureSocket
            } catch (insecureError: IOException) {
                insecureSocket.closeQuietly()
                throw IOException(
                    "secure=${secureError.message ?: "failed"}, insecure=${insecureError.message ?: "failed"}",
                    insecureError
                )
            }
        }
    }

    private fun BluetoothSocket.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1024
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

data class ReceivedChunk(
    val text: String,
    val byteCount: Int
)

enum class ReadStopReason {
    NotConnected,
    StreamClosed
}
