package com.example.qz1sample

import android.content.Context

data class RecordingSessionFiles(
    val primary: NmeaLogFile,
    val phoneLocation: PhoneLocationSidecar
) {
    val fileName: String
        get() = primary.fileName
}

sealed interface PhoneLocationSidecar {
    data object None : PhoneLocationSidecar
    data class Available(val sessionFileName: String) : PhoneLocationSidecar
}

object RecordingSessionFileStore {
    fun list(context: Context): List<RecordingSessionFiles> {
        return NmeaLogStore.list(context).map { primary -> bundle(context, primary) }
    }

    fun find(context: Context, fileName: String): RecordingSessionFiles? {
        val primary = NmeaLogStore.describe(context, fileName) ?: return null
        return bundle(context, primary)
    }

    fun delete(context: Context, fileName: String): Boolean {
        if (!PhoneLocationLogStore.delete(context, fileName)) return false
        return NmeaLogStore.delete(context, fileName)
    }

    private fun bundle(context: Context, primary: NmeaLogFile): RecordingSessionFiles {
        val phoneLocation = if (PhoneLocationLogStore.hasSamples(context, primary.fileName)) {
            PhoneLocationSidecar.Available(primary.fileName)
        } else {
            PhoneLocationSidecar.None
        }
        return RecordingSessionFiles(primary, phoneLocation)
    }
}
