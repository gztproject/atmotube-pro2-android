package com.atmotube.pro2.example

import java.util.Locale

/**
 * Consumer reading of a live Atmotube sample.
 *
 * PM2.5 and PM10 use the EU European Air Quality Index hourly bands (EEA), in µg/m³.
 * The index is the worse of those two. CO2, VOC, and NOx are not EAQI pollutants:
 * CO2 is ppm, VOC is an index plus ppb, and NOx is the Sensirion index.
 */
enum class AirLevel {
    UNKNOWN,
    GOOD,
    FAIR,
    MODERATE,
    POOR,
    VERY_POOR,
    EXTREMELY_POOR,
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
    "EU European Air Quality Index, hourly bands in µg/m³. The category is the worse of PM2.5 and PM10. CO2 (ppm), VOC, and NOx are not part of the index. Temperature is °C and pressure is hPa."

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
    AirLevel.FAIR -> "Fair"
    AirLevel.MODERATE -> "Moderate"
    AirLevel.POOR -> "Poor"
    AirLevel.VERY_POOR -> "Very poor"
    AirLevel.EXTREMELY_POOR -> "Extremely poor"
}

fun co2Metric(ppm: Int, locale: Locale = Locale.getDefault()): Metric {
    val reading = interpretCo2(ppm)
    return when (reading.kind) {
        Kind.OFF -> Metric("CO2", "Off", "", AirLevel.UNKNOWN, "Sensor off")
        Kind.HEATING -> Metric("CO2", "Heating", "", AirLevel.UNKNOWN, "Warming up")
        Kind.MISSING -> Metric("CO2", "—", "", AirLevel.UNKNOWN, "No reading")
        Kind.VALUE -> Metric("CO2", formatGrouped(reading.number!!.toInt(), locale), "ppm", AirLevel.UNKNOWN, "Indoor")
    }
}

fun pm25Metric(micrograms: Double, locale: Locale = Locale.getDefault()): Metric =
    countMetric("PM2.5", "µg/m³", interpretPm25(micrograms), locale) { levelLabel(it) }

fun pm10Metric(micrograms: Double, locale: Locale = Locale.getDefault()): Metric =
    countMetric("PM10", "µg/m³", interpretPm10(micrograms), locale) { levelLabel(it) }

fun pm1Metric(micrograms: Double, locale: Locale = Locale.getDefault()): Metric {
    val reading = interpretMass(micrograms)
    return when (reading.kind) {
        Kind.OFF -> Metric("PM1", "Off", "", AirLevel.UNKNOWN, "Sensor off")
        Kind.HEATING -> Metric("PM1", "Heating", "", AirLevel.UNKNOWN, "Warming up")
        Kind.MISSING -> Metric("PM1", "—", "", AirLevel.UNKNOWN, "No reading")
        Kind.VALUE -> Metric("PM1", formatFixed(reading.number!!, 1, locale), "µg/m³", AirLevel.UNKNOWN, "No EAQI band")
    }
}

fun vocMetric(index: Int, ppb: Int, locale: Locale = Locale.getDefault()): Metric {
    val indexReading = interpretIndex(index)
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
    val reading = interpretIndex(index)
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
    pm25: Double?,
    pm10: Double?,
    locale: Locale = Locale.getDefault(),
): AirSummary {
    val fine = if (pm25 == null) Interpreted(Kind.MISSING) else interpretPm25(pm25)
    val coarse = if (pm10 == null) Interpreted(Kind.MISSING) else interpretPm10(pm10)

    if (fine.kind != Kind.VALUE && coarse.kind != Kind.VALUE) {
        val warming = fine.kind == Kind.HEATING || coarse.kind == Kind.HEATING
        return AirSummary(
            title = if (warming) "Sensors are warming up" else "Waiting for particles",
            detail = when {
                warming -> "PM2.5 and PM10 will appear once the sensor is warm."
                fine.kind == Kind.OFF || coarse.kind == Kind.OFF -> "The particle sensor is off."
                else -> "The EU index needs a PM2.5 or PM10 reading."
            },
            level = AirLevel.UNKNOWN,
        )
    }

    if (fine.kind == Kind.VALUE && coarse.kind == Kind.VALUE && fine.level == coarse.level) {
        return AirSummary(
            title = levelLabel(fine.level),
            detail = "PM2.5 ${formatFixed(fine.number!!, 1, locale)} µg/m³ · PM10 ${formatFixed(coarse.number!!, 1, locale)} µg/m³.",
            level = fine.level,
        )
    }

    val fineRank = rank(fine.level)
    val coarseRank = rank(coarse.level)
    return if (fine.kind == Kind.VALUE && fineRank >= coarseRank) {
        drivenBy("PM2.5", fine, coarse, locale)
    } else {
        drivenBy("PM10", coarse, fine, locale)
    }
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

private fun drivenBy(
    name: String,
    driver: Interpreted,
    other: Interpreted,
    locale: Locale,
): AirSummary {
    val value = formatFixed(driver.number!!, 1, locale)
    val extra = when (other.kind) {
        Kind.HEATING -> " The other fraction is still warming up."
        Kind.OFF -> " The other fraction is off."
        Kind.MISSING -> " The other fraction is not in yet."
        Kind.VALUE -> ""
    }
    return AirSummary(levelLabel(driver.level), "$name is $value µg/m³.$extra", driver.level)
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
    return Interpreted(Kind.VALUE, AirLevel.UNKNOWN, ppm.toDouble())
}

private fun interpretPm25(micrograms: Double): Interpreted = interpretBand(micrograms, ::eaqiPm25)

private fun interpretPm10(micrograms: Double): Interpreted = interpretBand(micrograms, ::eaqiPm10)

private fun interpretMass(micrograms: Double): Interpreted {
    sentinel(micrograms)?.let { return it }
    return Interpreted(Kind.VALUE, AirLevel.UNKNOWN, micrograms)
}

private fun interpretBand(micrograms: Double, band: (Double) -> AirLevel): Interpreted {
    sentinel(micrograms)?.let { return it }
    return Interpreted(Kind.VALUE, band(micrograms), micrograms)
}

/** EEA hourly bands: 0–5, 6–15, 16–50, 51–90, 91–140, above 140 µg/m³. */
private fun eaqiPm25(micrograms: Double): AirLevel = when {
    micrograms < 6 -> AirLevel.GOOD
    micrograms < 16 -> AirLevel.FAIR
    micrograms < 51 -> AirLevel.MODERATE
    micrograms < 91 -> AirLevel.POOR
    micrograms < 141 -> AirLevel.VERY_POOR
    else -> AirLevel.EXTREMELY_POOR
}

/** EEA hourly bands: 0–15, 16–45, 46–120, 121–195, 196–270, above 270 µg/m³. */
private fun eaqiPm10(micrograms: Double): AirLevel = when {
    micrograms < 16 -> AirLevel.GOOD
    micrograms < 46 -> AirLevel.FAIR
    micrograms < 121 -> AirLevel.MODERATE
    micrograms < 196 -> AirLevel.POOR
    micrograms < 271 -> AirLevel.VERY_POOR
    else -> AirLevel.EXTREMELY_POOR
}

private fun interpretIndex(index: Int): Interpreted {
    sentinel(index.toDouble())?.let { return it }
    if (index <= 0) return Interpreted(Kind.MISSING)
    return Interpreted(Kind.VALUE, AirLevel.UNKNOWN, index.toDouble())
}

private fun vocCaption(index: Int): String = when {
    index < 80 -> "Lower than typical"
    index <= 120 -> "Typical"
    index <= 200 -> "Elevated"
    index <= 300 -> "High"
    else -> "Very high"
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
    AirLevel.MODERATE -> 3
    AirLevel.POOR -> 4
    AirLevel.VERY_POOR -> 5
    AirLevel.EXTREMELY_POOR -> 6
}
