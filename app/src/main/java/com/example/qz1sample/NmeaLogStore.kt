package com.example.qz1sample

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NmeaLogStore {
    const val LOG_DIR = "qz1_logs"

    fun directory(context: Context): File {
        return File(context.filesDir, LOG_DIR).also { it.mkdirs() }
    }

    fun list(context: Context): List<NmeaLogFile> {
        return directory(context)
            .listFiles { file -> file.isFile && file.extension.equals("nmea", ignoreCase = true) }
            ?.map { file ->
                NmeaLogFile(
                    fileName = file.name,
                    sizeBytes = file.length(),
                    modifiedAtMillis = file.lastModified()
                )
            }
            ?.sortedByDescending { it.modifiedAtMillis }
            ?: emptyList()
    }

    fun delete(context: Context, fileName: String): Boolean {
        val file = findFile(context, fileName) ?: return false
        return file.delete()
    }

    fun describe(context: Context, fileName: String): NmeaLogFile? {
        val file = findFile(context, fileName) ?: return null
        return NmeaLogFile(
            fileName = file.name,
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified()
        )
    }

    fun <T> readLines(
        context: Context,
        fileName: String,
        transform: (Sequence<String>) -> T
    ): T? {
        val file = findFile(context, fileName) ?: return null
        return file.bufferedReader().useLines(transform)
    }

    fun shareUri(context: Context, fileName: String) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        findFile(context, fileName) ?: error("Log file not found: $fileName")
    )

    private fun findFile(context: Context, fileName: String): File? {
        val directory = directory(context).canonicalFile
        val file = File(directory, fileName).canonicalFile
        return file.takeIf { it.isFile && it.parentFile == directory }
    }
}

data class NmeaLogFile(
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1_000_000 -> "%.1f MB".format(sizeBytes / 1_000_000.0)
            sizeBytes >= 1_000 -> "%.1f KB".format(sizeBytes / 1_000.0)
            else -> "$sizeBytes B"
        }

    val modifiedLabel: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(modifiedAtMillis))
}
