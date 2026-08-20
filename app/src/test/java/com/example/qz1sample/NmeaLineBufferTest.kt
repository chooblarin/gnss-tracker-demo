package com.example.qz1sample

import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaLineBufferTest {
    @Test
    fun buffersPartialLinesUntilNewline() {
        val buffer = NmeaLineBuffer()

        assertEquals(emptyList<String>(), buffer.append("$" + "GNR"))
        assertEquals(listOf("$" + "GNRMC,1*00"), buffer.append("MC,1*00\r\n"))
    }

    @Test
    fun keepsOnlyNmeaStyleLines() {
        val buffer = NmeaLineBuffer()

        assertEquals(
            listOf("$" + "GNGGA,1*00", "!AIVDM,1*00"),
            buffer.append("noise\n$" + "GNGGA,1*00\n!AIVDM,1*00\n")
        )
    }
}
