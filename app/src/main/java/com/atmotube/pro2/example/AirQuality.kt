package com.atmotube.pro2.example

import java.util.Locale

/**
 * Consumer reading of a live Atmotube sample.
 *
 * PM2.5 and PM10 default to the EU European Air Quality Index hourly bands. PM1 uses that
 * PM2.5 scale. CO2 uses indoor bands in ppm. VOC and NOx use the Sensirion index.
 * Every cutoff is editable. The headline is the worst reading that has a value.
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

data class PollutantSnapshot(
    val level: AirLevel,
    val valueText: String?,
)

const val BANDS_NOTE =
    "Bands are editable. Defaults: PM2.5 and PM10 use the EU EAQI hourly scale, PM1 uses the PM2.5 scale, CO2 uses indoor bands (800, 1,000, 1,500, 2,000, 5,000 ppm), and VOC and NOx use the Sensirion index. Temperature is °C and pressure is hPa."

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

fun co2Metric(
    ppm: Int,
    locale: Locale = Locale.getDefault(),
    cuts: BandCuts = defaultCuts(Pollutant.CO2),
): Metric = bandMetric("CO2", "ppm", interpretPollutant(Pollutant.CO2, ppm.toDouble(), cuts), locale, decimals = null)

fun pm25Metric(
    micrograms: Double,
    locale: Locale = Locale.getDefault(),
    cuts: BandCuts = defaultCuts(Pollutant.PM25),
): Metric = bandMetric("PM2.5", "µg/m³", interpretPollutant(Pollutant.PM25, micrograms, cuts), locale, decimals = 1)

fun pm10Metric(
    micrograms: Double,
    locale: Locale = Locale.getDefault(),
    cuts: BandCuts = defaultCuts(Pollutant.PM10),
): Metric = bandMetric("PM10", "µg/m³", interpretPollutant(Pollutant.PM10, micrograms, cuts), locale, decimals = 1)

fun pm1Metric(
    micrograms: Double,
    locale: Locale = Locale.getDefault(),
    cuts: BandCuts = defaultCuts(Pollutant.PM1),
): Metric = bandMetric("PM1", "µg/m³", interpretPollutant(Pollutant.PM1, micrograms, cuts), locale, decimals = 1)

fun vocMetric(
    index: Int,
    ppb: Int,
    locale: Locale = Locale.getDefault(),
    cuts: BandCuts = defaultCuts(Pollutant.VOC),
): Metric {
    val indexReading = interpretPollutant(Pollutant.VOC, index.toDouble(), cuts)
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
            caption = listOfNotNull(levelLabel(indexReading.level), ppbText).joinToString(" · "),
        )
    }
}

fun noxMetric(
    index: Int,
    locale: Locale = Locale.getDefault(),
    cuts: BandCuts = defaultCuts(Pollutant.NOX),
): Metric = bandMetric("NOx", "index", interpretPollutant(Pollutant.NOX, index.toDouble(), cuts), locale, decimals = null)

fun pollutantValues(reading: AtmotubeReading?, pm: AtmotubePmReading?): Map<Pollutant, Double?> = mapOf(
    Pollutant.CO2 to reading?.co2Ppm?.toDouble(),
    Pollutant.PM1 to pm?.pm1,
    Pollutant.PM25 to pm?.pm25,
    Pollutant.PM10 to pm?.pm10,
    Pollutant.VOC to reading?.vocIndex?.toDouble(),
    Pollutant.NOX to reading?.noxIndex?.toDouble(),
)

fun pollutantSnapshots(
    reading: AtmotubeReading?,
    pm: AtmotubePmReading?,
    cuts: Map<Pollutant, BandCuts>,
    locale: Locale = Locale.getDefault(),
): Map<Pollutant, PollutantSnapshot> {
    val values = pollutantValues(reading, pm)
    return Pollutant.entries.associateWith { pollutant ->
        val raw = values[pollutant]
        val interpreted = if (raw == null) {
            Interpreted(Kind.MISSING)
        } else {
            interpretPollutant(pollutant, raw, cuts[pollutant] ?: defaultCuts(pollutant))
        }
        PollutantSnapshot(
            level = interpreted.level,
            valueText = interpreted.number?.let { formatPollutantNumber(pollutant, it, locale) },
        )
    }
}

fun summarize(
    values: Map<Pollutant, Double?>,
    locale: Locale = Locale.getDefault(),
    cuts: Map<Pollutant, BandCuts> = Pollutant.entries.associateWith { defaultCuts(it) },
): AirSummary {
    val interpreted = Pollutant.entries.associateWith { pollutant ->
        val raw = values[pollutant]
        if (raw == null) {
            Interpreted(Kind.MISSING)
        } else {
            interpretPollutant(pollutant, raw, cuts[pollutant] ?: defaultCuts(pollutant))
        }
    }
    val valued = Pollutant.entries.mapNotNull { pollutant ->
        val reading = interpreted.getValue(pollutant)
        if (reading.kind == Kind.VALUE) pollutant to reading else null
    }
    if (valued.isEmpty()) {
        val warming = interpreted.values.any { it.kind == Kind.HEATING }
        val off = interpreted.values.any { it.kind == Kind.OFF }
        return AirSummary(
            title = if (warming) "Sensors are warming up" else "Waiting for sensors",
            detail = when {
                warming -> "Readings will appear once the sensors are warm."
                off -> "A sensor is off."
                else -> "Air quality needs a live sample."
            },
            level = AirLevel.UNKNOWN,
        )
    }

    val worstRank = valued.maxOf { levelRank(it.second.level) }
    val worst = valued.filter { levelRank(it.second.level) == worstRank }
    val level = worst.first().second.level
    val detail = when {
        level == AirLevel.GOOD && worst.size == valued.size -> "Every reading is in the Good band."
        worst.size == 1 -> {
            val (pollutant, reading) = worst.single()
            "${pollutant.label} is ${formatPollutantNumber(pollutant, reading.number!!, locale)} ${pollutant.unit}."
        }
        else -> worst.joinToString(" · ") { (pollutant, reading) ->
            "${pollutant.label} ${formatPollutantNumber(pollutant, reading.number!!, locale)} ${pollutant.unit}"
        } + "."
    }
    return AirSummary(levelLabel(level), detail, level)
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
    if (ppm == null) return "—"
    val reading = interpretPollutant(Pollutant.CO2, ppm.toDouble(), defaultCuts(Pollutant.CO2))
    return when (reading.kind) {
        Kind.VALUE -> formatGrouped(reading.number!!.toInt(), locale)
        Kind.OFF -> "Off"
        Kind.HEATING -> "Heating"
        Kind.MISSING -> "—"
    }
}

/** A real PM mass reading. Off, heating, and non-finite sentinels are skipped. */
fun usablePm(value: Double): Double? {
    if (!value.isFinite()) return null
    if (value in AtmotubeReading.offValues || value in AtmotubeReading.heatingValues) return null
    return value
}

/** Current PM stays on the card. The hour is a footnote once it has been collected. */
fun pmCardFootnote(
    current: Double,
    hourlyAverage: Double?,
    locale: Locale = Locale.getDefault(),
    held: Boolean = false,
    canMeasure: Boolean = false,
): String? {
    if (usablePm(current) == null) return if (canMeasure) "Tap to measure" else null
    val hour = hourlyAverage?.let { " · this hour ${formatPm(it, locale)}" }.orEmpty()
    return when {
        held && canMeasure -> "Last read · tap$hour"
        held -> "Last read$hour"
        hourlyAverage != null -> "This hour ${formatPm(hourlyAverage, locale)}"
        else -> "Live"
    }
}

fun formatPm(micrograms: Double?, locale: Locale = Locale.getDefault()): String {
    if (micrograms == null) return "—"
    val reading = interpretPollutant(Pollutant.PM25, micrograms, defaultCuts(Pollutant.PM25))
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

private fun bandMetric(
    label: String,
    unit: String,
    reading: Interpreted,
    locale: Locale,
    decimals: Int?,
): Metric = when (reading.kind) {
    Kind.OFF -> Metric(label, "Off", "", AirLevel.UNKNOWN, "Sensor off")
    Kind.HEATING -> Metric(label, "Heating", "", AirLevel.UNKNOWN, "Warming up")
    Kind.MISSING -> Metric(label, "—", "", AirLevel.UNKNOWN, "No reading")
    Kind.VALUE -> Metric(
        label = label,
        value = formatShown(reading.number!!, decimals, locale),
        unit = unit,
        level = reading.level,
        caption = levelLabel(reading.level),
    )
}

private fun interpretPollutant(pollutant: Pollutant, value: Double, cuts: BandCuts): Interpreted {
    sentinel(value)?.let { return it }
    if (pollutant == Pollutant.CO2 || pollutant == Pollutant.VOC || pollutant == Pollutant.NOX) {
        if (value <= 0.0) return Interpreted(Kind.MISSING)
    }
    return Interpreted(Kind.VALUE, cuts.levelFor(value), value)
}

private fun formatPollutantNumber(pollutant: Pollutant, number: Double, locale: Locale): String =
    when (pollutant) {
        Pollutant.PM1, Pollutant.PM25, Pollutant.PM10 -> formatFixed(number, 1, locale)
        Pollutant.CO2, Pollutant.VOC, Pollutant.NOX -> formatGrouped(number.toInt(), locale)
    }

private fun formatShown(number: Double, decimals: Int?, locale: Locale): String =
    if (decimals == null) formatGrouped(number.toInt(), locale) else formatFixed(number, decimals, locale)

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
