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

        val summary = summarize(7.0, 7.0, locale)
        assertEquals("Fair", summary.title)
        assertEquals(AirLevel.FAIR, summary.level)
        assertEquals("1,100", co2Metric(reading.co2Ppm, locale).value)
        assertEquals("ppm", co2Metric(reading.co2Ppm, locale).unit)
        assertEquals("Indoor", co2Metric(reading.co2Ppm, locale).caption)
        assertEquals(AirLevel.UNKNOWN, co2Metric(reading.co2Ppm, locale).level)
        assertEquals("Typical · 93 ppb", vocMetric(reading.vocIndex, reading.vocPpb, locale).caption)
        assertEquals("Clean baseline", noxMetric(reading.noxIndex, locale).caption)
        assertEquals("30.43°C", climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).temperature)
        assertEquals("981.5", climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).pressure)
        assertEquals("80% · Charging", batteryLabel(reading.batteryLevel, reading.errorFlags))
        assertTrue(climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).note!!.contains("charging"))
        assertEquals(listOf("Charging", "Recently charged", "PM enabled"), statusLabels(reading.errorFlags))
        assertTrue(errorLabels(reading.errorFlags).isEmpty())
    }

    @Test
    fun co2StaysUnbandedInPpm() {
        assertEquals(AirLevel.UNKNOWN, co2Metric(1500, locale).level)
        assertEquals("ppm", co2Metric(1500, locale).unit)
        assertEquals("Heating", co2Metric(0xFFFE, locale).value)
        assertEquals("Off", co2Metric(0xFFFF, locale).value)
    }

    @Test
    fun pmUsesEaqiHourlyBands() {
        assertEquals(AirLevel.GOOD, pm25Metric(0.0, locale).level)
        assertEquals(AirLevel.GOOD, pm25Metric(5.0, locale).level)
        assertEquals(AirLevel.GOOD, pm25Metric(5.9, locale).level)
        assertEquals(AirLevel.FAIR, pm25Metric(6.0, locale).level)
        assertEquals(AirLevel.FAIR, pm25Metric(15.0, locale).level)
        assertEquals(AirLevel.MODERATE, pm25Metric(16.0, locale).level)
        assertEquals(AirLevel.MODERATE, pm25Metric(50.0, locale).level)
        assertEquals(AirLevel.POOR, pm25Metric(51.0, locale).level)
        assertEquals(AirLevel.POOR, pm25Metric(90.0, locale).level)
        assertEquals(AirLevel.VERY_POOR, pm25Metric(91.0, locale).level)
        assertEquals(AirLevel.VERY_POOR, pm25Metric(140.0, locale).level)
        assertEquals(AirLevel.EXTREMELY_POOR, pm25Metric(141.0, locale).level)
        assertEquals("Fair", pm25Metric(7.0, locale).caption)
        assertEquals("µg/m³", pm25Metric(7.0, locale).unit)

        assertEquals(AirLevel.GOOD, pm10Metric(15.0, locale).level)
        assertEquals(AirLevel.FAIR, pm10Metric(16.0, locale).level)
        assertEquals(AirLevel.FAIR, pm10Metric(45.0, locale).level)
        assertEquals(AirLevel.MODERATE, pm10Metric(46.0, locale).level)
        assertEquals(AirLevel.MODERATE, pm10Metric(120.0, locale).level)
        assertEquals(AirLevel.POOR, pm10Metric(121.0, locale).level)
        assertEquals(AirLevel.POOR, pm10Metric(195.0, locale).level)
        assertEquals(AirLevel.VERY_POOR, pm10Metric(196.0, locale).level)
        assertEquals(AirLevel.VERY_POOR, pm10Metric(270.0, locale).level)
        assertEquals(AirLevel.EXTREMELY_POOR, pm10Metric(271.0, locale).level)
        assertEquals(AirLevel.UNKNOWN, pm1Metric(6.6, locale).level)
        assertEquals("µg/m³", pm1Metric(6.6, locale).unit)
    }

    @Test
    fun summaryUsesTheWorsePmFraction() {
        assertEquals("Good", summarize(4.0, 4.0, locale).title)
        assertEquals(AirLevel.GOOD, summarize(4.0, 4.0, locale).level)
        assertEquals("Fair", summarize(7.0, 7.0, locale).title)
        assertTrue(summarize(7.0, 7.0, locale).detail.contains("PM2.5"))
        assertEquals("Fair", summarize(4.0, 20.0, locale).title)
        assertTrue(summarize(4.0, 20.0, locale).detail.startsWith("PM10"))
        assertEquals("Moderate", summarize(16.0, 10.0, locale).title)
        assertEquals("Poor", summarize(60.0, 10.0, locale).title)
        assertEquals("Extremely poor", summarize(141.0, 10.0, locale).title)
        assertEquals("Good", summarize(4.0, null, locale).title)
        assertTrue(summarize(4.0, null, locale).detail.contains("not in yet"))
        assertEquals("Good", summarize(65534.0, 7.0, locale).title)
        assertTrue(summarize(65534.0, 7.0, locale).detail.contains("warming"))
        assertEquals("Waiting for particles", summarize(null, null, locale).title)
        assertEquals("Sensors are warming up", summarize(65534.0, 65534.0, locale).title)
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
