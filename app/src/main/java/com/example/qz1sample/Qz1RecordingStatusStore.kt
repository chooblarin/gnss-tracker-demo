package com.example.qz1sample

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object Qz1RecordingStatusStore {
    private val lock = Any()
    private val rawPreview = CappedTextBuffer(MAX_LOG_CHARS)
    private val nmeaPreview = CappedTextBuffer(MAX_LOG_CHARS)

    private val _snapshot = MutableStateFlow(RecordingSnapshot())
    val snapshot: StateFlow<RecordingSnapshot> = _snapshot

    fun connecting(deviceLabel: String, fileName: String) {
        synchronized(lock) {
            rawPreview.clear()
            nmeaPreview.clear()
            _snapshot.value = RecordingSnapshot(
                sessionState = SessionState.Connecting(deviceLabel),
                fileName = fileName,
                phoneRecording = PhoneRecordingState.Preparing
            )
        }
    }

    fun connected(deviceLabel: String) {
        _snapshot.update {
            it.copy(sessionState = SessionState.Connected(deviceLabel))
        }
    }

    fun updateReceived(
        byteCount: Int,
        savedLineCount: Int,
        rawChunk: String,
        nmeaLines: List<String>,
        gnss: GnssSnapshot
    ) {
        synchronized(lock) {
            rawPreview.append(rawChunk)
            if (nmeaLines.isNotEmpty()) {
                nmeaPreview.append(nmeaLines.joinToString(separator = "\n", postfix = "\n"))
            }
            _snapshot.update {
                it.copy(
                    receivedBytes = byteCount,
                    savedLines = savedLineCount,
                    rawLog = rawPreview.toString(),
                    nmeaLog = nmeaPreview.toString(),
                    gnss = gnss
                )
            }
        }
    }

    fun failed(message: String) {
        _snapshot.update {
            it.copy(sessionState = SessionState.Failed(message))
        }
    }

    fun updatePhoneGnss(phoneGnss: PhoneGnssSnapshot) {
        _snapshot.update { it.copy(phoneRecording = PhoneRecordingState.Active(phoneGnss)) }
    }

    fun phoneUnavailable(reason: String) {
        _snapshot.update {
            it.copy(phoneRecording = PhoneRecordingState.Unavailable(reason))
        }
    }

    fun stopped(reason: String) {
        _snapshot.update {
            it.copy(sessionState = SessionState.Disconnected(reason))
        }
    }

    fun clearPreviewLogs() {
        synchronized(lock) {
            rawPreview.clear()
            nmeaPreview.clear()
            _snapshot.update {
                it.copy(rawLog = "", nmeaLog = "", gnss = GnssSnapshot())
            }
        }
    }

    private const val MAX_LOG_CHARS = 40_000
}

data class RecordingSnapshot(
    val sessionState: SessionState = SessionState.Idle,
    val fileName: String? = null,
    val receivedBytes: Int = 0,
    val savedLines: Int = 0,
    val rawLog: String = "",
    val nmeaLog: String = "",
    val gnss: GnssSnapshot = GnssSnapshot(),
    val phoneRecording: PhoneRecordingState = PhoneRecordingState.NotStarted
)

sealed interface PhoneRecordingState {
    data object NotStarted : PhoneRecordingState
    data object Preparing : PhoneRecordingState
    data class Active(val snapshot: PhoneGnssSnapshot) : PhoneRecordingState
    data class Unavailable(val reason: String) : PhoneRecordingState
}

private class CappedTextBuffer(private val maxChars: Int) {
    private val builder = StringBuilder()

    fun append(text: String) {
        builder.append(text)
        if (builder.length > maxChars) {
            builder.delete(0, builder.length - maxChars)
        }
    }

    fun clear() {
        builder.clear()
    }

    override fun toString(): String = builder.toString()
}
