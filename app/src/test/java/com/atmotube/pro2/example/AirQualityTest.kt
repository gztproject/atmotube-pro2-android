package com.atmotube.pro2.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Locale

class AirQualityTest {
    private val locale = Locale.US

    @Test
    fun livePacketExposesCo2VocAndNox() {
        val packet = le16(3043) +
            byteArrayOf(31) +
            byteArrayOf(0x57, 0x26, 0x00, 0x00) +
            le16(99) +
            le16(93) +
            le16(1) +
            le16(1100) +
            byteArrayOf(80) +
            le16(0xE000)

        val reading = AtmotubeReading.fromBytes(packet, "AA:BB:CC:DD:EE:FF")

        assertEquals(1100, reading.co2Ppm)
        assertEquals(99, reading.vocIndex)
        assertEquals(93, reading.vocPpb)
        assertEquals(1, reading.noxIndex)
        assertEquals(31, reading.humidity)
        assertEquals(80, reading.batteryLevel)
        assertEquals(30.43, reading.temperature!!, 0.001)
        assertEquals(981.5, reading.pressure, 0.001)

        val summary = summarize(reading.co2Ppm, 7.0, locale)
        assertEquals("CO2 is building up", summary.title)
        assertEquals(AirLevel.POOR, summary.level)
        assertEquals("1,100", co2Metric(reading.co2Ppm, locale).value)
        assertEquals("Poor", co2Metric(reading.co2Ppm, locale).caption)
        assertEquals("Typical · 93 ppb", vocMetric(reading.vocIndex, reading.vocPpb, locale).caption)
        assertEquals("Clean baseline", noxMetric(reading.noxIndex, locale).caption)
        assertEquals("80% · Charging", batteryLabel(reading.batteryLevel, reading.errorFlags))
        assertTrue(climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).note!!.contains("charging"))
        assertEquals(listOf("Charging", "Recently charged", "PM enabled"), statusLabels(reading.errorFlags))
        assertTrue(errorLabels(reading.errorFlags).isEmpty())
    }

    @Test
    fun co2BandsFollowIndoorCuts() {
        assertEquals(AirLevel.GOOD, co2Metric(799, locale).level)
        assertEquals(AirLevel.FAIR, co2Metric(800, locale).level)
        assertEquals(AirLevel.FAIR, co2Metric(999, locale).level)
        assertEquals(AirLevel.POOR, co2Metric(1000, locale).level)
        assertEquals(AirLevel.POOR, co2Metric(1499, locale).level)
        assertEquals(AirLevel.VERY_POOR, co2Metric(1500, locale).level)
        assertEquals("Heating", co2Metric(0xFFFE, locale).value)
        assertEquals("Off", co2Metric(0xFFFF, locale).value)
    }

    @Test
    fun pm25UsesEpaBreakpoints() {
        assertEquals(AirLevel.GOOD, pm25Metric(12.0, locale).level)
        assertEquals(AirLevel.FAIR, pm25Metric(12.1, locale).level)
        assertEquals(AirLevel.FAIR, pm25Metric(35.4, locale).level)
        assertEquals(AirLevel.POOR, pm25Metric(35.5, locale).level)
        assertEquals(AirLevel.POOR, pm25Metric(55.4, locale).level)
        assertEquals(AirLevel.VERY_POOR, pm25Metric(55.5, locale).level)
    }

    @Test
    fun summaryNamesTheWorsePollutant() {
        assertEquals("Air looks good", summarize(600, 7.0, locale).title)
        assertEquals("CO2 is a little high", summarize(900, 7.0, locale).title)
        assertEquals("CO2 is high", summarize(1800, 7.0, locale).title)
        assertEquals("Fine particles are a bit elevated", summarize(600, 20.0, locale).title)
        assertEquals("Fine particles are elevated", summarize(600, 40.0, locale).title)
        assertEquals("Fine particles are high", summarize(600, 80.0, locale).title)
        assertEquals("CO2 and particles are both up", summarize(1200, 40.0, locale).title)
        assertEquals("Fine particles are elevated", summarize(900, 40.0, locale).title)
        assertEquals("Fine particles look good", summarize(0xFFFE, 7.0, locale).title)
        assertTrue(summarize(0xFFFE, 7.0, locale).detail.contains("warming up"))
        assertEquals("Waiting for sensors", summarize(null, null, locale).title)
        assertEquals("CO2 looks good", summarize(500, null, locale).title)
    }

    @Test
    fun historyPacketKeepsCo2() {
        val body = byteArrayOf(
            0x01,
            0x02,
            0x10, 0x20, 0x30, 0x00,
            0x00, 0x00,
            40,
            0x00, 0x00, 0x00, 0x00,
            50,
            0x00, 0x00,
            0x48, 0x03,
        )
        val packet = body + crc8(body).toByte()
        val parsed = HistoryParser.parseStream(ByteArrayInputStream(packet), false)
        assertEquals(1, parsed.size)
        assertEquals(840, parsed[0].co2Ppm)
        assertFalse(parsed[0].flags.contains("CRC mismatch"))

        val withoutCo2 = body.copyOf()
        withoutCo2[1] = 0x00
        val trimmed = withoutCo2.copyOf(withoutCo2.size - 2)
        val bare = trimmed + crc8(trimmed).toByte()
        val parsedBare = HistoryParser.parseStream(ByteArrayInputStream(bare), false)
        assertNull(parsedBare.single().co2Ppm)
    }

    @Test
    fun firmwareReplyParsesLeadingNoise() {
        assertEquals("3.1.0", parseFirmwareVersion("\nversion app 3.1.0\n"))
        assertEquals("3.0.17", parseFirmwareVersion("version app 3.0.17"))
        assertNull(parseFirmwareVersion("version app"))
        assertTrue(checkFwNew(3, 0, 17, parseFirmwareVersion("version app 3.1.0")))
        assertFalse(checkFwNew(3, 0, 17, parseFirmwareVersion("version app 3.0.16")))
    }

    @Test
    fun downsampleShrinksLongSeries() {
        val values = List(400) { it.toFloat() }
        assertEquals(4, downsample(values, maxPoints = 4).size)
        val short = values.take(4)
        assertEquals(short, downsample(short, maxPoints = 160))
    }

    @Test
    fun co2ErrorIsSeparateFromStatusFlags() {
        assertTrue(isCo2Error(1 shl 3))
        assertEquals(listOf("CO2 error"), errorLabels(1 shl 3))
        assertTrue(errorLabels((1 shl 14) or (1 shl 13)).isEmpty())
    }

    private fun le16(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte())

    private fun crc8(data: ByteArray): Int {
        var crc = 0
        for (byte in data) {
            var current = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                current = if ((current and 0x80) != 0) ((current shl 1) xor 0x31) and 0xFF else (current shl 1) and 0xFF
            }
            crc = current
        }
        return crc and 0xFF
    }
}
