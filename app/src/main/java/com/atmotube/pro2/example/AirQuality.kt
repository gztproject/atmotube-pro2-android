package com.atmotube.pro2.example

import java.util.Locale

/**
 * Consumer reading of a live Atmotube sample.
 *
 * CO2 cuts are common indoor guidance (800 / 1,000 / 1,500 ppm), not a medical scale.
 * PM2.5 follows US EPA breakpoints, with Unhealthy and above collapsed into [AirLevel.VERY_POOR].
 * VOC and NOx indexes are relative (Sensirion): 100 is a typical VOC index, 1 is the NOx clean-air baseline.
 */
enum class AirLevel {
    UNKNOWN,
    GOOD,
    FAIR,
    POOR,
    VERY_POOR,
}

data class Metric(
    val label: String,
    val value: String,
    val unit: String,
    val level: AirLevel,
    val caption: String,
)

data class AirSummary(
    val title: String,
    val detail: String,
    val level: AirLevel,
)

data class ClimateReadout(
    val temperature: String,
    val humidity: String,
    val pressure: String,
    val note: String?,
)

const val BANDS_NOTE =
    "CO2 uses common indoor bands (800 / 1,000 / 1,500 ppm). PM2.5 uses US EPA breakpoints, with the upper categories grouped as Very poor. VOC and NOx indexes are relative to recent air."

private const val FLAG_CO2_ERROR = 1 shl 3
private const val FLAG_MOTION = 1 shl 12
private const val FLAG_PM_ENABLED = 1 shl 13
private const val FLAG_CHARGING = 1 shl 14
private const val FLAG_RECENTLY_CHARGED = 1 shl 15

private enum class Kind { MISSING, OFF, HEATING, VALUE }

private data class Interpreted(
    val kind: Kind,
    val level: AirLevel = AirLevel.UNKNOWN,
    val number: Double? = null,
)

fun levelLabel(level: AirLevel): String = when (level) {
    AirLevel.UNKNOWN -> "Unavailable"
    AirLevel.GOOD -> "Good"
    AirLevel.FAIR -> "Moderate"
    AirLevel.POOR -> "Poor"
    AirLevel.VERY_POOR -> "Very poor"
}

fun co2Metric(ppm: Int, locale: Locale = Locale.getDefault()): Metric =
    countMetric("CO2", "ppm", interpretCo2(ppm), locale) { levelLabel(it) }

fun pm25Metric(micrograms: Double, locale: Locale = Locale.getDefault()): Metric =
    countMetric("PM2.5", "µg/m³", interpretPm25(micrograms), locale) { levelLabel(it) }

fun vocMetric(index: Int, ppb: Int, locale: Locale = Locale.getDefault()): Metric {
    val indexReading = interpretIndex(index, ::vocLevel)
    val ppbText = optionalGrouped(ppb, locale)?.let { "$it ppb" }
    return when (indexReading.kind) {
        Kind.OFF -> Metric("VOC", "Off", "", AirLevel.UNKNOWN, "Sensor off")
        Kind.HEATING -> Metric("VOC", "Heating", "", AirLevel.UNKNOWN, "Warming up")
        Kind.MISSING -> Metric("VOC", "—", "", AirLevel.UNKNOWN, ppbText ?: "No reading")
        Kind.VALUE -> Metric(
            label = "VOC",
            value = formatGrouped(indexReading.number!!.toInt(), locale),
            unit = "index",
            level = indexReading.level,
            caption = listOfNotNull(vocCaption(index), ppbText).joinToString(" · "),
        )
    }
}

fun noxMetric(index: Int, locale: Locale = Locale.getDefault()): Metric {
    val reading = interpretIndex(index, ::noxLevel)
    return when (reading.kind) {
        Kind.OFF -> Metric("NOx", "Off", "", AirLevel.UNKNOWN, "Sensor off")
        Kind.HEATING -> Metric("NOx", "Heating", "", AirLevel.UNKNOWN, "Warming up")
        Kind.MISSING -> Metric("NOx", "—", "", AirLevel.UNKNOWN, "No reading")
        Kind.VALUE -> Metric(
            label = "NOx",
            value = formatGrouped(reading.number!!.toInt(), locale),
            unit = "index",
            level = reading.level,
            caption = noxCaption(index),
        )
    }
}

fun summarize(
    co2Ppm: Int?,
    pm25: Double?,
    locale: Locale = Locale.getDefault(),
): AirSummary {
    val co2 = interpretCo2(co2Ppm)
    val pm = if (pm25 == null) Interpreted(Kind.MISSING) else interpretPm25(pm25)
    val co2Rank = rank(co2.level)
    val pmRank = rank(pm.level)

    if (co2.kind != Kind.VALUE && pm.kind != Kind.VALUE) {
        val warming = co2.kind == Kind.HEATING || pm.kind == Kind.HEATING
        return AirSummary(
            title = if (warming) "Sensors are warming up" else "Waiting for sensors",
            detail = when {
                co2.kind == Kind.HEATING -> "CO2 will appear once the sensor is warm."
                pm.kind == Kind.HEATING -> "Particle data will appear once the sensor is warm."
                co2.kind == Kind.OFF && pm.kind == Kind.OFF -> "CO2 and the particle sensor are off."
                else -> "Readings show up a moment after the device is ready."
            },
            level = AirLevel.UNKNOWN,
        )
    }

    val co2Up = co2.kind == Kind.VALUE && co2Rank >= rank(AirLevel.FAIR)
    val pmUp = pm.kind == Kind.VALUE && pmRank >= rank(AirLevel.FAIR)
    if (co2Up && pmUp && co2.level == pm.level) {
        return AirSummary(
            title = "CO2 and particles are both up",
            detail = "${formatGrouped(co2.number!!.toInt(), locale)} ppm CO2 · ${formatFixed(pm.number!!, 1, locale)} µg/m³ PM2.5.",
            level = co2.level,
        )
    }

    if (co2.kind == Kind.VALUE && co2Rank >= pmRank) {
        return co2Summary(co2, pm, locale)
    }
    if (pm.kind == Kind.VALUE) {
        return pmSummary(pm, co2, locale)
    }
    return co2Summary(co2, pm, locale)
}

fun climateReadout(
    temperature: Double?,
    humidity: Int,
    pressure: Double,
    flags: Int,
    locale: Locale = Locale.getDefault(),
): ClimateReadout {
    val charging = flags and FLAG_CHARGING != 0
    val recentlyCharged = flags and FLAG_RECENTLY_CHARGED != 0
    return ClimateReadout(
        temperature = formatMeasurement(temperature, "temp", locale) { formatFixed(it, 2, locale) + "°C" },
        humidity = formatMeasurement(humidity.toDouble(), "hum", locale) { formatFixed(it, 0, locale) + "%" },
        pressure = formatMeasurement(pressure, "press", locale) { formatFixed(it, 1, locale) },
        note = when {
            charging -> "Temperature and humidity are unreliable while charging."
            recentlyCharged -> "Temperature and humidity can run high just after charging."
            else -> null
        },
    )
}

fun statusLabels(flags: Int): List<String> = buildList {
    if (flags and FLAG_CHARGING != 0) add("Charging")
    if (flags and FLAG_RECENTLY_CHARGED != 0) add("Recently charged")
    if (flags and FLAG_MOTION != 0) add("Motion")
    if (flags and FLAG_PM_ENABLED != 0) add("PM enabled")
}

fun errorLabels(flags: Int): List<String> {
    val errors = listOf(
        0 to "PM sensor error",
        1 to "PM laser error",
        2 to "PM fan error",
        3 to "CO2 error",
        4 to "VOC/NOx error",
        5 to "Pressure error",
        6 to "Accelerometer error",
        7 to "Charger error",
        8 to "Flash error",
        9 to "GPS error",
        10 to "External module error",
    )
    return errors.mapNotNull { (bit, label) ->
        if (flags and (1 shl bit) != 0) label else null
    }
}

fun batteryLabel(level: Int, flags: Int): String? {
    if (level !in 0..100) return null
    return if (flags and FLAG_CHARGING != 0) "$level% · Charging" else "$level%"
}

fun formatCo2(ppm: Int?, locale: Locale = Locale.getDefault()): String {
    val reading = interpretCo2(ppm)
    return when (reading.kind) {
        Kind.VALUE -> formatGrouped(reading.number!!.toInt(), locale)
        Kind.OFF -> "Off"
        Kind.HEATING -> "Heating"
        Kind.MISSING -> "—"
    }
}

fun formatPm(micrograms: Double?, locale: Locale = Locale.getDefault()): String {
    if (micrograms == null) return "—"
    val reading = interpretPm25(micrograms)
    return when (reading.kind) {
        Kind.VALUE -> formatFixed(reading.number!!, 1, locale)
        Kind.OFF -> "Off"
        Kind.HEATING -> "Heating"
        Kind.MISSING -> "—"
    }
}

fun formatTemperature(celsius: Double?, locale: Locale = Locale.getDefault()): String =
    formatMeasurement(celsius, "temp", locale) { formatFixed(it, 1, locale) + "°C" }

fun gpsHasFix(reading: AtmotubeGpsReading): Boolean =
    reading.satellitesFixed > 0 && (reading.latitude != 0.0 || reading.longitude != 0.0)

fun downsample(values: List<Float>, maxPoints: Int = 160): List<Float> {
    if (values.size <= maxPoints || maxPoints <= 0) return values
    val bucket = values.size.toFloat() / maxPoints
    return List(maxPoints) { index ->
        val start = (index * bucket).toInt()
        val end = (((index + 1) * bucket).toInt()).coerceAtMost(values.size).coerceAtLeast(start + 1)
        values.subList(start, end).average().toFloat()
    }
}

fun isCo2Error(flags: Int): Boolean = flags and FLAG_CO2_ERROR != 0

private fun co2Summary(co2: Interpreted, pm: Interpreted, locale: Locale): AirSummary {
    val ppm = formatGrouped(co2.number!!.toInt(), locale)
    val (title, detail) = when (co2.level) {
        AirLevel.GOOD -> if (pm.kind == Kind.VALUE) {
            "Air looks good" to "CO2 and fine particles are in a comfortable range."
        } else {
            "CO2 looks good" to missingPmDetail(pm)
        }
        AirLevel.FAIR -> "CO2 is a little high" to "$ppm ppm. A bit of fresh air would bring it down."
        AirLevel.POOR -> "CO2 is building up" to "$ppm ppm. Opening a window helps."
        AirLevel.VERY_POOR -> "CO2 is high" to "$ppm ppm. Ventilate this room."
        AirLevel.UNKNOWN -> "Waiting for sensors" to "Readings show up a moment after the device is ready."
    }
    return AirSummary(title, detail, co2.level)
}

private fun pmSummary(pm: Interpreted, co2: Interpreted, locale: Locale): AirSummary {
    val value = formatFixed(pm.number!!, 1, locale)
    val (title, detail) = when (pm.level) {
        AirLevel.GOOD -> "Fine particles look good" to missingCo2Detail(co2)
        AirLevel.FAIR -> "Fine particles are a bit elevated" to "PM2.5 is $value µg/m³."
        AirLevel.POOR -> "Fine particles are elevated" to "PM2.5 is $value µg/m³."
        AirLevel.VERY_POOR -> "Fine particles are high" to "PM2.5 is $value µg/m³."
        AirLevel.UNKNOWN -> "Waiting for sensors" to "Readings show up a moment after the device is ready."
    }
    return AirSummary(title, detail, pm.level)
}

private fun missingPmDetail(pm: Interpreted): String = when (pm.kind) {
    Kind.HEATING -> "The particle sensor is still warming up."
    Kind.OFF -> "The particle sensor is off."
    else -> "Particle data is not in yet."
}

private fun missingCo2Detail(co2: Interpreted): String = when (co2.kind) {
    Kind.HEATING -> "The CO2 sensor is still warming up."
    Kind.OFF -> "The CO2 sensor is off."
    else -> "CO2 is not in yet."
}

private fun countMetric(
    label: String,
    unit: String,
    reading: Interpreted,
    locale: Locale,
    caption: (AirLevel) -> String,
): Metric = when (reading.kind) {
    Kind.OFF -> Metric(label, "Off", "", AirLevel.UNKNOWN, "Sensor off")
    Kind.HEATING -> Metric(label, "Heating", "", AirLevel.UNKNOWN, "Warming up")
    Kind.MISSING -> Metric(label, "—", "", AirLevel.UNKNOWN, "No reading")
    Kind.VALUE -> {
        val shown = if (unit == "µg/m³") {
            formatFixed(reading.number!!, 1, locale)
        } else {
            formatGrouped(reading.number!!.toInt(), locale)
        }
        Metric(label, shown, unit, reading.level, caption(reading.level))
    }
}

private fun interpretCo2(ppm: Int?): Interpreted {
    if (ppm == null) return Interpreted(Kind.MISSING)
    sentinel(ppm.toDouble())?.let { return it }
    if (ppm <= 0) return Interpreted(Kind.MISSING)
    val level = when {
        ppm < 800 -> AirLevel.GOOD
        ppm < 1_000 -> AirLevel.FAIR
        ppm < 1_500 -> AirLevel.POOR
        else -> AirLevel.VERY_POOR
    }
    return Interpreted(Kind.VALUE, level, ppm.toDouble())
}

private fun interpretPm25(micrograms: Double): Interpreted {
    sentinel(micrograms)?.let { return it }
    val level = when {
        micrograms <= 12.0 -> AirLevel.GOOD
        micrograms <= 35.4 -> AirLevel.FAIR
        micrograms <= 55.4 -> AirLevel.POOR
        else -> AirLevel.VERY_POOR
    }
    return Interpreted(Kind.VALUE, level, micrograms)
}

private fun interpretIndex(index: Int, levelOf: (Int) -> AirLevel): Interpreted {
    sentinel(index.toDouble())?.let { return it }
    if (index <= 0) return Interpreted(Kind.MISSING)
    return Interpreted(Kind.VALUE, levelOf(index), index.toDouble())
}

private fun vocLevel(index: Int): AirLevel = when {
    index <= 120 -> AirLevel.GOOD
    index <= 200 -> AirLevel.FAIR
    index <= 300 -> AirLevel.POOR
    else -> AirLevel.VERY_POOR
}

private fun vocCaption(index: Int): String = when {
    index < 80 -> "Lower than typical"
    index <= 120 -> "Typical"
    index <= 200 -> "Elevated"
    index <= 300 -> "High"
    else -> "Very high"
}

private fun noxLevel(index: Int): AirLevel = when {
    index <= 1 -> AirLevel.GOOD
    index <= 20 -> AirLevel.FAIR
    index <= 100 -> AirLevel.POOR
    else -> AirLevel.VERY_POOR
}

private fun noxCaption(index: Int): String = when {
    index <= 1 -> "Clean baseline"
    index <= 20 -> "Above baseline"
    index <= 100 -> "Elevated"
    else -> "High"
}

private fun sentinel(value: Double): Interpreted? = when {
    value in AtmotubeReading.offValues -> Interpreted(Kind.OFF)
    value in AtmotubeReading.heatingValues -> Interpreted(Kind.HEATING)
    else -> null
}

private fun optionalGrouped(value: Int, locale: Locale): String? {
    val reading = sentinel(value.toDouble())
    if (reading != null || value < 0) return null
    return formatGrouped(value, locale)
}

private fun formatMeasurement(
    value: Double?,
    type: String,
    locale: Locale,
    render: (Double) -> String,
): String {
    if (value == null) return "—"
    if (AtmotubeReading.formatSensorValue(value, type) == "Off") return "—"
    if (value in AtmotubeReading.heatingValues) return "Heating"
    return render(value)
}

private fun formatGrouped(value: Int, locale: Locale): String =
    java.text.NumberFormat.getIntegerInstance(locale).format(value)

private fun formatFixed(value: Double, decimals: Int, locale: Locale): String =
    String.format(locale, "%.${decimals}f", value)

private fun rank(level: AirLevel): Int = when (level) {
    AirLevel.UNKNOWN -> 0
    AirLevel.GOOD -> 1
    AirLevel.FAIR -> 2
    AirLevel.POOR -> 3
    AirLevel.VERY_POOR -> 4
}
