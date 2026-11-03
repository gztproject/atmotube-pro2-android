package com.atmotube.pro2.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WidgetLine(
    val label: String,
    val value: String,
    val detail: String,
    val level: AirLevel,
)

fun defaultWidgetPollutants(): Set<Pollutant> = setOf(Pollutant.PM25, Pollutant.CO2)

fun decodeWidgetSelection(stored: String?): Set<Pollutant> {
    if (stored == null) return defaultWidgetPollutants()
    if (stored.isBlank()) return emptySet()
    return stored.split(",").mapNotNull { runCatching { Pollutant.valueOf(it.trim()) }.getOrNull() }.toSet()
}

fun encodeWidgetSelection(selected: Set<Pollutant>): String =
    Pollutant.entries.filter { it in selected }.joinToString(",") { it.name }

fun widgetLines(
    selected: Set<Pollutant>,
    reading: AtmotubeReading?,
    pm: AtmotubePmReading?,
    cuts: Map<Pollutant, BandCuts>,
    hourly: HourlyPm?,
    locale: Locale = Locale.getDefault(),
): List<WidgetLine> {
    if (selected.isEmpty()) {
        return listOf(WidgetLine("Widget", "—", "Pick pollutants in Bands and alerts", AirLevel.UNKNOWN))
    }
    return Pollutant.entries.filter { it in selected }.map { pollutant ->
        val metric = widgetMetric(pollutant, reading, pm, cuts, locale)
        val hourValue = hourlyValue(pollutant, hourly)
        val detail = when {
            metric.level == AirLevel.UNKNOWN -> metric.caption
            hourValue != null -> "${metric.caption} · this hour ${formatPm(hourValue, locale)}"
            pollutant == Pollutant.PM1 || pollutant == Pollutant.PM25 || pollutant == Pollutant.PM10 ->
                "${metric.caption} · live"
            else -> metric.caption
        }
        val shown = if (metric.unit.isEmpty()) metric.value else "${metric.value} ${metric.unit}"
        WidgetLine(metric.label, shown, detail, metric.level)
    }
}

fun encodeWidgetLines(lines: List<WidgetLine>): String =
    lines.joinToString("\n") { line ->
        listOf(line.label, line.value, line.detail, line.level.name).joinToString("\t")
    }

fun decodeWidgetLines(text: String?): List<WidgetLine> {
    if (text.isNullOrBlank()) return emptyList()
    return text.lines().mapNotNull { line ->
        val parts = line.split("\t")
        if (parts.size != 4) return@mapNotNull null
        val level = runCatching { AirLevel.valueOf(parts[3]) }.getOrNull() ?: return@mapNotNull null
        WidgetLine(parts[0], parts[1], parts[2], level)
    }
}

fun widgetFreshness(
    updatedAtMillis: Long?,
    nowMillis: Long,
    locale: Locale = Locale.getDefault(),
    zone: TimeZone = TimeZone.getDefault(),
): String {
    if (updatedAtMillis == null) return "Connect to show live readings"
    val age = nowMillis - updatedAtMillis
    if (age in 0..90_000) return "Live"
    val format = SimpleDateFormat("HH:mm", locale)
    format.timeZone = zone
    return "Updated ${format.format(Date(updatedAtMillis))}"
}

fun AirLevel.widgetColor(): Int = when (this) {
    AirLevel.GOOD -> 0xFF0B6E66.toInt()
    AirLevel.FAIR -> 0xFF0E6B52.toInt()
    AirLevel.MODERATE -> 0xFF8A6A00.toInt()
    AirLevel.POOR -> 0xFFC4382D.toInt()
    AirLevel.VERY_POOR -> 0xFF960032.toInt()
    AirLevel.EXTREMELY_POOR -> 0xFF7D2181.toInt()
    AirLevel.UNKNOWN -> 0xFF5E594F.toInt()
}

private fun hourlyValue(pollutant: Pollutant, hourly: HourlyPm?): Double? = when (pollutant) {
    Pollutant.PM1 -> hourly?.pm1
    Pollutant.PM25 -> hourly?.pm25
    Pollutant.PM10 -> hourly?.pm10
    else -> null
}

private fun widgetMetric(
    pollutant: Pollutant,
    reading: AtmotubeReading?,
    pm: AtmotubePmReading?,
    cuts: Map<Pollutant, BandCuts>,
    locale: Locale,
): Metric {
    val band = cuts[pollutant] ?: defaultCuts(pollutant)
    return when (pollutant) {
        Pollutant.CO2 -> reading?.let { co2Metric(it.co2Ppm, locale, band) } ?: waiting(pollutant.label)
        Pollutant.PM1 -> pm?.let { pm1Metric(it.pm1, locale, band) } ?: waiting(pollutant.label)
        Pollutant.PM25 -> pm?.let { pm25Metric(it.pm25, locale, band) } ?: waiting(pollutant.label)
        Pollutant.PM10 -> pm?.let { pm10Metric(it.pm10, locale, band) } ?: waiting(pollutant.label)
        Pollutant.VOC -> reading?.let { vocMetric(it.vocIndex, it.vocPpb, locale, band) } ?: waiting(pollutant.label)
        Pollutant.NOX -> reading?.let { noxMetric(it.noxIndex, locale, band) } ?: waiting(pollutant.label)
    }
}

private fun waiting(label: String) = Metric(label, "—", "", AirLevel.UNKNOWN, "Waiting")
