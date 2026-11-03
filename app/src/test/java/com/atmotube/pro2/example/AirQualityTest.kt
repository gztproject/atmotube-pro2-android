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

        val summary = summarize(mapOf(Pollutant.PM25 to 7.0, Pollutant.PM10 to 7.0), locale)
        assertEquals("Fair", summary.title)
        assertEquals(AirLevel.FAIR, summary.level)
        val co2 = co2Metric(reading.co2Ppm, locale)
        assertEquals("1,100", co2.value)
        assertEquals("ppm", co2.unit)
        assertEquals("Moderate", co2.caption)
        assertEquals(AirLevel.MODERATE, co2.level)
        assertEquals("Fair · 93 ppb", vocMetric(reading.vocIndex, reading.vocPpb, locale).caption)
        assertEquals(AirLevel.FAIR, vocMetric(reading.vocIndex, reading.vocPpb, locale).level)
        assertEquals("Good", noxMetric(reading.noxIndex, locale).caption)
        assertEquals(AirLevel.GOOD, noxMetric(reading.noxIndex, locale).level)
        assertEquals("30.43°C", climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).temperature)
        assertEquals("981.5", climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).pressure)
        assertEquals("80% · Charging", batteryLabel(reading.batteryLevel, reading.errorFlags))
        assertTrue(climateReadout(reading.temperature, reading.humidity, reading.pressure, reading.errorFlags, locale).note!!.contains("charging"))
        assertEquals(listOf("Charging", "Recently charged", "PM enabled"), statusLabels(reading.errorFlags))
        assertTrue(errorLabels(reading.errorFlags).isEmpty())
    }

    @Test
    fun co2UsesIndoorBands() {
        assertEquals(AirLevel.GOOD, co2Metric(799, locale).level)
        assertEquals(AirLevel.FAIR, co2Metric(800, locale).level)
        assertEquals(AirLevel.FAIR, co2Metric(999, locale).level)
        assertEquals(AirLevel.MODERATE, co2Metric(1_000, locale).level)
        assertEquals(AirLevel.MODERATE, co2Metric(1_100, locale).level)
        assertEquals(AirLevel.MODERATE, co2Metric(1_499, locale).level)
        assertEquals(AirLevel.POOR, co2Metric(1_500, locale).level)
        assertEquals(AirLevel.POOR, co2Metric(1_999, locale).level)
        assertEquals(AirLevel.VERY_POOR, co2Metric(2_000, locale).level)
        assertEquals(AirLevel.VERY_POOR, co2Metric(4_999, locale).level)
        assertEquals(AirLevel.EXTREMELY_POOR, co2Metric(5_000, locale).level)
        assertEquals("ppm", co2Metric(1_500, locale).unit)
        assertEquals("Poor", co2Metric(1_500, locale).caption)
        assertEquals("Heating", co2Metric(0xFFFE, locale).value)
        assertEquals("Off", co2Metric(0xFFFF, locale).value)
        assertEquals(AirLevel.UNKNOWN, co2Metric(0, locale).level)
        assertEquals("No reading", co2Metric(0, locale).caption)

        val tight = BandCuts(400.0, 600.0, 800.0, 1_000.0, 1_200.0)
        assertEquals(AirLevel.VERY_POOR, co2Metric(1_100, locale, tight).level)
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
        assertEquals(AirLevel.FAIR, pm1Metric(6.6, locale).level)
        assertEquals("Fair", pm1Metric(6.6, locale).caption)
        assertEquals("µg/m³", pm1Metric(6.6, locale).unit)
    }

    @Test
    fun summaryUsesTheWorstReading() {
        assertEquals("Good", pmSummary(4.0, 4.0).title)
        assertEquals(AirLevel.GOOD, pmSummary(4.0, 4.0).level)
        assertEquals("Every reading is in the Good band.", pmSummary(4.0, 4.0).detail)
        assertEquals("Fair", pmSummary(7.0, 7.0).title)
        assertTrue(pmSummary(7.0, 7.0).detail.contains("PM2.5"))
        assertEquals("Fair", pmSummary(4.0, 20.0).title)
        assertTrue(pmSummary(4.0, 20.0).detail.startsWith("PM10"))
        assertEquals("Moderate", pmSummary(16.0, 10.0).title)
        assertEquals("Poor", pmSummary(60.0, 10.0).title)
        assertEquals("Extremely poor", pmSummary(141.0, 10.0).title)
        assertEquals("Good", pmSummary(4.0, null).title)
        assertEquals("Every reading is in the Good band.", pmSummary(4.0, null).detail)
        assertEquals("Good", pmSummary(65534.0, 7.0).title)
        assertEquals("Every reading is in the Good band.", pmSummary(65534.0, 7.0).detail)
        assertEquals("Fair", pmSummary(65534.0, 20.0).title)
        assertTrue(pmSummary(65534.0, 20.0).detail.startsWith("PM10"))
        assertEquals("Waiting for sensors", pmSummary(null, null).title)
        assertEquals("Sensors are warming up", pmSummary(65534.0, 65534.0).title)

        val withCo2 = summarize(
            mapOf(Pollutant.PM25 to 7.0, Pollutant.PM10 to 7.0, Pollutant.CO2 to 1_100.0),
            locale,
        )
        assertEquals("Moderate", withCo2.title)
        assertEquals(AirLevel.MODERATE, withCo2.level)
        assertTrue(withCo2.detail.startsWith("CO2"))
    }

    @Test
    fun bandsRoundTripAndAlertsFireOnEntry() {
        assertEquals(1.5, parseCut("1,5")!!, 0.001)
        assertNull(parseCut("0"))
        assertNull(BandCuts(10.0, 9.0, 11.0, 12.0, 13.0).validated())

        val custom = defaultProfile().let { current ->
            current.copy(
                cuts = current.cuts + (Pollutant.CO2 to BandCuts(700.0, 900.0, 1_200.0, 1_800.0, 4_000.0)),
                alerts = current.alerts + (Pollutant.CO2 to PollutantAlert(enabled = true, from = AirLevel.FAIR)),
            )
        }
        val decoded = decodeProfile(encodeProfile(custom))
        assertEquals(700.0, decoded.cuts.getValue(Pollutant.CO2).goodBelow, 0.001)
        assertEquals(4_000.0, decoded.cuts.getValue(Pollutant.CO2).veryPoorBelow, 0.001)
        assertTrue(decoded.alerts.getValue(Pollutant.CO2).enabled)
        assertEquals(AirLevel.FAIR, decoded.alerts.getValue(Pollutant.CO2).from)
        assertEquals(defaultCuts(Pollutant.PM25), decoded.cuts.getValue(Pollutant.PM25))

        val rejected = decodeProfile("CO2,1,2,3,4,5,true,GOOD")
        assertFalse(rejected.alerts.getValue(Pollutant.CO2).enabled)
        assertEquals(800.0, rejected.cuts.getValue(Pollutant.CO2).goodBelow, 0.001)
        assertEquals(800.0, decodeProfile("not a profile").cuts.getValue(Pollutant.CO2).goodBelow, 0.001)

        assertTrue(shouldAlert(AirLevel.UNKNOWN, AirLevel.POOR, AirLevel.POOR, enabled = true))
        assertFalse(shouldAlert(AirLevel.POOR, AirLevel.POOR, AirLevel.POOR, enabled = true))
        assertTrue(shouldAlert(AirLevel.POOR, AirLevel.VERY_POOR, AirLevel.POOR, enabled = true))
        assertFalse(shouldAlert(AirLevel.GOOD, AirLevel.MODERATE, AirLevel.POOR, enabled = true))
        assertFalse(shouldAlert(AirLevel.UNKNOWN, AirLevel.POOR, AirLevel.POOR, enabled = false))
        assertFalse(shouldAlert(AirLevel.UNKNOWN, AirLevel.UNKNOWN, AirLevel.POOR, enabled = true))
        assertTrue(shouldClear(AirLevel.POOR, AirLevel.FAIR, AirLevel.POOR, enabled = true))
        assertTrue(shouldClear(AirLevel.POOR, AirLevel.POOR, AirLevel.POOR, enabled = false))
        assertFalse(shouldClear(AirLevel.GOOD, AirLevel.GOOD, AirLevel.POOR, enabled = true))

        val previous = mapOf(Pollutant.CO2 to AirLevel.FAIR)
        val current = mapOf(Pollutant.CO2 to AirLevel.MODERATE)
        val alerts = mapOf(Pollutant.CO2 to PollutantAlert(enabled = true, from = AirLevel.MODERATE))
        assertEquals(listOf(Pollutant.CO2), crossingAlerts(previous, current, alerts))
        assertTrue(recoveredAlerts(current, mapOf(Pollutant.CO2 to AirLevel.FAIR), alerts).contains(Pollutant.CO2))
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

    private fun pmSummary(pm25: Double?, pm10: Double?) = summarize(
        mapOf(Pollutant.PM25 to pm25, Pollutant.PM10 to pm10),
        locale,
    )

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
