package com.example.qz1sample

class NmeaLineBuffer {
    private val pending = StringBuilder()

    fun append(text: String): List<String> {
        pending.append(text)
        val lines = pending.toString().split('\n')
        pending.clear()
        pending.append(lines.lastOrNull() ?: "")

        return lines
            .dropLast(1)
            .map { it.trim() }
            .filter { it.startsWith("$") || it.startsWith("!") }
    }

    fun clear() {
        pending.clear()
    }
}
