package com.example.qz1sample

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NmeaLogWriter(
    context: Context,
    deviceLabel: String,
    startedAt: Date = Date()
) {
    private val directory = NmeaLogStore.directory(context)
    val file: File = File(directory, "${timestamp(startedAt)}_${sanitize(deviceLabel)}.nmea")
    private val writer = BufferedWriter(
        OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.US_ASCII)
    )

    fun appendLines(lines: List<String>) {
        for (line in lines) {
            writer.appendLine(line)
        }
        writer.flush()
    }

    fun close() {
        writer.close()
    }

    companion object {
        private fun timestamp(date: Date): String {
            return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)
        }

        private fun sanitize(value: String): String {
            return value
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('_')
                .ifEmpty { "qz1" }
        }
    }
}
