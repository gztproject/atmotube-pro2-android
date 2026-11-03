package com.atmotube.pro2.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DeviceAndWidgetTest {
    private val locale = Locale.US

    @Test
    fun pmAndGpsStatusMatchTheOfficialShell() {
        val pm = parsePmStatus("1 2 1 0")
        assertEquals(PmSensorState.ON, pm!!.state)
        assertEquals(PmMode.MIN_15, pm.mode)
        assertTrue(pm.charging)
        assertEquals("0", pm.timer)
        assertEquals(PmMode.MIN_15, parsePmStatus("\npm status 1 2 1 0\n")!!.mode)

        val gps = parseGnssStatus("0 1 10")
        assertEquals(GnssSensorState.OFF, gps!!.state)
        assertEquals(GnssMode.TIMER, gps.mode)
        assertEquals(10, gps.timerMinutes)
        assertEquals(listOf("1", "1"), pmModeArgs(PmMode.ALWAYS_ON, charging = true).toList())
        assertEquals(listOf("1"), gnssModeArg(GnssMode.TIMER).toList())
        assertEquals(listOf("10"), gnssTimerArg(10).toList())
        assertEquals(10, gnssTimerEditor(GnssStatus(GnssSensorState.ON, GnssMode.TIMER, 0)))
        assertEquals(15, gnssTimerEditor(GnssStatus(GnssSensorState.ON, GnssMode.TIMER, 15)))
        assertNull(parsePmStatus("nope"))
        assertNull(parseGnssStatus("1 9"))
    }

    @Test
    fun hourlyAverageWaitsUntilTheHourIsCollected() {
        val start = 1_700_000_000_000L
        val first = PmHourLog().add(start, 1.0, 10.0, 2.0)
        assertNull(first.hourly(start))
        assertNull(first.hourly(start + PM_HOUR_MS - 1))
        assertEquals(10.0, first.hourly(start + PM_HOUR_MS)!!.pm25!!, 0.001)

        val both = first.add(start + 30 * 60_000L, 1.0, 20.0, 2.0)
        assertEquals(15.0, both.hourly(start + PM_HOUR_MS)!!.pm25!!, 0.001)
        assertEquals(15.0, PmHourLog.decode(both.encode()).hourly(start + PM_HOUR_MS)!!.pm25!!, 0.001)

        val afterGap = both.add(start + 3 * PM_HOUR_MS, 1.0, 5.0, 2.0)
        assertNull(afterGap.hourly(start + 3 * PM_HOUR_MS))
        assertEquals(5.0, afterGap.hourly(start + 4 * PM_HOUR_MS)!!.pm25!!, 0.001)

        assertNull(PmHourLog().add(start, 65534.0, 65534.0, 65534.0).startedAtMillis)
    }

    @Test
    fun currentPmShowsBeforeTheHourlyAverage() {
        assertEquals("Live", pmCardFootnote(7.0, null, locale))
        assertEquals("This hour 4.0", pmCardFootnote(7.0, 4.0, locale))
        assertNull(pmCardFootnote(65534.0, 4.0, locale))

        val pm = AtmotubePmReading(
            pm1 = 6.6,
            pm25 = 7.0,
            pm10 = 7.0,
            pm05Particles = 0,
            pm1Particles = 0,
            pm25Particles = 0,
            pm10Particles = 0,
            typicalParticleSize = 0.6,
        )
        val live = widgetLines(setOf(Pollutant.PM25), null, pm, defaultProfile().cuts, hourly = null, locale = locale)
        assertEquals("7.0 µg/m³", live.single().value)
        assertTrue(live.single().detail.contains("live"))
        assertTrue(!live.single().detail.contains("this hour"))

        val settled = widgetLines(
            setOf(Pollutant.PM25),
            null,
            pm,
            defaultProfile().cuts,
            HourlyPm(pm1 = null, pm25 = 4.0, pm10 = null),
            locale = locale,
        )
        assertEquals("7.0 µg/m³", settled.single().value)
        assertTrue(settled.single().detail.contains("this hour 4.0"))

        assertEquals(setOf(Pollutant.PM25, Pollutant.CO2), decodeWidgetSelection(null))
        assertTrue(decodeWidgetSelection("").isEmpty())
        assertEquals(setOf(Pollutant.CO2), decodeWidgetSelection(encodeWidgetSelection(setOf(Pollutant.CO2))))
        assertEquals("Connect to show live readings", widgetFreshness(null, 0L, locale, TimeZone.getTimeZone("UTC")))
        assertEquals("Live", widgetFreshness(1_000L, 2_000L, locale, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun pmHoldsTheBurstAverageWhileTheSensorIsOff() {
        val first = pm(6.0, 10.0, 12.0, particles = 40)
        val second = pm(8.0, 20.0, 16.0, particles = 60)
        val off = pm(6553.5, 6553.5, 6553.5, particles = 65535)
        val burst = PmTracker().accept(first).accept(second)
        val held = burst.accept(off)
        val frozen = checkNotNull(held.held)
        assertEquals(15.0, frozen.pm25, 0.001)
        assertEquals(50, frozen.pm05Particles)
        val view = held.display(off)
        assertTrue(view.held)
        assertEquals(15.0, view.reading!!.pm25, 0.001)
        val liveAgain = held.accept(pm(1.0, 4.0, 5.0, particles = 10)).display(pm(1.0, 4.0, 5.0, particles = 10))
        assertEquals(false, liveAgain.held)
        assertEquals(4.0, liveAgain.reading!!.pm25, 0.001)
        assertEquals("Last read · tap", pmCardFootnote(15.0, null, locale, held = true, canMeasure = true))
        assertEquals("Tap to measure", pmCardFootnote(6553.5, null, locale, canMeasure = true))
        val restored = PmTracker.decode(held.encode())
        assertEquals(15.0, restored.display(off).reading!!.pm25, 0.001)
    }

    private fun pm(pm1: Double, pm25: Double, pm10: Double, particles: Int) = AtmotubePmReading(
        pm1 = pm1,
        pm25 = pm25,
        pm10 = pm10,
        pm05Particles = particles,
        pm1Particles = particles,
        pm25Particles = particles,
        pm10Particles = particles,
        typicalParticleSize = 0.6,
    )
}
