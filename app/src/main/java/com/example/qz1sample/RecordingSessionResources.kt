package com.example.qz1sample

import android.content.Context
import java.io.Closeable
import java.io.IOException

class RecordingSessionResources private constructor(
    val nmeaWriter: NmeaLogWriter
) : Closeable {
    private var sppSession: SppSession? = null
    private var phoneRecorder: PhoneGnssRecorder? = null
    private var closed = false

    val fileName: String
        get() = nmeaWriter.file.name

    val recordsPhoneGnss: Boolean
        get() = phoneRecorder != null

    fun startPhoneGnss(
        context: Context,
        onSnapshot: (PhoneGnssSnapshot) -> Unit,
        onFailure: (String) -> Unit
    ): PhoneGnssStartResult {
        check(phoneRecorder == null)
        return PhoneGnssRecorder.tryStart(
            context = context,
            nmeaFileName = fileName,
            onSnapshot = onSnapshot,
            onFailure = onFailure
        ).also { result ->
            phoneRecorder = (result as? PhoneGnssStartResult.Started)?.recorder
        }
    }

    fun discardPhoneGnss() {
        phoneRecorder?.discard()
        phoneRecorder = null
    }

    fun stopPhoneGnss() {
        val recorder = phoneRecorder
        phoneRecorder = null
        recorder?.close()
    }

    @Synchronized
    fun attach(session: SppSession): Boolean {
        if (closed) {
            session.close()
            return false
        }
        check(sppSession == null)
        sppSession = session
        return true
    }

    @Synchronized
    fun closeSppSession() {
        sppSession?.close()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        sppSession?.close()
        sppSession = null
        val phoneCloseFailure = runCatching { phoneRecorder?.close() }.exceptionOrNull()
        phoneRecorder = null
        val writerCloseFailure = runCatching { nmeaWriter.close() }.exceptionOrNull()
        (writerCloseFailure ?: phoneCloseFailure)?.let { throw it }
    }

    companion object {
        @Throws(IOException::class)
        fun create(
            context: Context,
            deviceLabel: String
        ): RecordingSessionResources {
            return RecordingSessionResources(NmeaLogWriter(context, deviceLabel))
        }
    }
}
