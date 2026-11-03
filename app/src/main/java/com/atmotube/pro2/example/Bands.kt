package com.atmotube.pro2.example

import java.util.Locale

/**
 * Upper edge of each band. A reading below [goodBelow] is Good, below [fairBelow] is Fair,
 * and so on. A reading at or above [veryPoorBelow] is Extremely poor.
 */
data class BandCuts(
    val goodBelow: Double,
    val fairBelow: Double,
    val moderateBelow: Double,
    val poorBelow: Double,
    val veryPoorBelow: Double,
) {
    fun levelFor(value: Double): AirLevel = when {
        value < goodBelow -> AirLevel.GOOD
        value < fairBelow -> AirLevel.FAIR
        value < moderateBelow -> AirLevel.MODERATE
        value < poorBelow -> AirLevel.POOR
        value < veryPoorBelow -> AirLevel.VERY_POOR
        else -> AirLevel.EXTREMELY_POOR
    }

    fun validated(): BandCuts? =
        if (
            listOf(goodBelow, fairBelow, moderateBelow, poorBelow, veryPoorBelow).all { it.isFinite() && it > 0 } &&
            goodBelow < fairBelow &&
            fairBelow < moderateBelow &&
            moderateBelow < poorBelow &&
            poorBelow < veryPoorBelow
        ) {
            this
        } else {
            null
        }
}

data class PollutantAlert(
    val enabled: Boolean = false,
    val from: AirLevel = AirLevel.POOR,
)

data class BandProfile(
    val cuts: Map<Pollutant, BandCuts>,
    val alerts: Map<Pollutant, PollutantAlert>,
)

enum class Pollutant(val label: String, val unit: String) {
    CO2("CO2", "ppm"),
    PM1("PM1", "µg/m³"),
    PM25("PM2.5", "µg/m³"),
    PM10("PM10", "µg/m³"),
    VOC("VOC", "index"),
    NOX("NOx", "index"),
}

fun defaultCuts(pollutant: Pollutant): BandCuts = when (pollutant) {
    // EEA hourly EAQI: 0–5, 6–15, 16–50, 51–90, 91–140, above 140.
    Pollutant.PM25 -> BandCuts(6.0, 16.0, 51.0, 91.0, 141.0)
    // PM1 has no EU index. The PM2.5 scale is the closest mass-based default.
    Pollutant.PM1 -> BandCuts(6.0, 16.0, 51.0, 91.0, 141.0)
    // EEA hourly EAQI: 0–15, 16–45, 46–120, 121–195, 196–270, above 270.
    Pollutant.PM10 -> BandCuts(16.0, 46.0, 121.0, 196.0, 271.0)
    // Indoor guidance, then 2,000 ppm and the 5,000 ppm occupational line.
    Pollutant.CO2 -> BandCuts(800.0, 1_000.0, 1_500.0, 2_000.0, 5_000.0)
    // Sensirion VOC index. 100 is typical for recent air.
    Pollutant.VOC -> BandCuts(80.0, 121.0, 201.0, 301.0, 401.0)
    // Sensirion NOx index. 1 is the clean-air baseline.
    Pollutant.NOX -> BandCuts(2.0, 21.0, 51.0, 101.0, 201.0)
}

fun defaultProfile(): BandProfile = BandProfile(
    cuts = Pollutant.entries.associateWith { defaultCuts(it) },
    alerts = Pollutant.entries.associateWith { PollutantAlert() },
)

fun defaultNote(pollutant: Pollutant): String = when (pollutant) {
    Pollutant.PM25 -> "Default is the EU EAQI hourly scale, in µg/m³."
    Pollutant.PM10 -> "Default is the EU EAQI hourly scale, in µg/m³."
    Pollutant.PM1 -> "Default matches the PM2.5 EU scale. PM1 has no separate EU index."
    Pollutant.CO2 -> "Default is indoor guidance: 800, 1,000, 1,500, 2,000, and 5,000 ppm."
    Pollutant.VOC -> "Default follows the Sensirion index, where 100 is typical."
    Pollutant.NOX -> "Default follows the Sensirion index, where 1 is the clean-air baseline."
}

val alertLevelChoices: List<AirLevel> = listOf(
    AirLevel.FAIR,
    AirLevel.MODERATE,
    AirLevel.POOR,
    AirLevel.VERY_POOR,
    AirLevel.EXTREMELY_POOR,
)

fun parseCut(text: String): Double? {
    val trimmed = text.trim().replace(" ", "").replace(',', '.')
    return trimmed.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
}

fun formatCut(value: Double): String {
    val text = String.format(Locale.US, "%.4f", value)
    return text.trimEnd('0').trimEnd('.')
}

fun encodeProfile(profile: BandProfile): String =
    Pollutant.entries.joinToString("\n") { pollutant ->
        val cuts = profile.cuts[pollutant] ?: defaultCuts(pollutant)
        val alert = profile.alerts[pollutant] ?: PollutantAlert()
        listOf(
            pollutant.name,
            formatCut(cuts.goodBelow),
            formatCut(cuts.fairBelow),
            formatCut(cuts.moderateBelow),
            formatCut(cuts.poorBelow),
            formatCut(cuts.veryPoorBelow),
            alert.enabled.toString(),
            alert.from.name,
        ).joinToString(",")
    }

fun decodeProfile(text: String?): BandProfile {
    val cuts = defaultProfile().cuts.toMutableMap()
    val alerts = defaultProfile().alerts.toMutableMap()
    if (text.isNullOrBlank()) return BandProfile(cuts, alerts)
    for (line in text.lines()) {
        val parts = line.split(",")
        if (parts.size != 8) continue
        val pollutant = runCatching { Pollutant.valueOf(parts[0]) }.getOrNull() ?: continue
        val numbers = parts.subList(1, 6).map { it.toDoubleOrNull() }
        if (numbers.any { it == null }) continue
        val parsed = BandCuts(
            numbers[0]!!,
            numbers[1]!!,
            numbers[2]!!,
            numbers[3]!!,
            numbers[4]!!,
        ).validated() ?: continue
        val enabled = parts[6].toBooleanStrictOrNull() ?: continue
        val from = runCatching { AirLevel.valueOf(parts[7]) }.getOrNull() ?: continue
        if (from !in alertLevelChoices) continue
        cuts[pollutant] = parsed
        alerts[pollutant] = PollutantAlert(enabled, from)
    }
    return BandProfile(cuts, alerts)
}

fun shouldAlert(previous: AirLevel, current: AirLevel, from: AirLevel, enabled: Boolean): Boolean {
    if (!enabled) return false
    if (levelRank(current) < levelRank(from)) return false
    if (levelRank(previous) < levelRank(from)) return true
    return levelRank(current) > levelRank(previous)
}

fun shouldClear(previous: AirLevel, current: AirLevel, from: AirLevel, enabled: Boolean): Boolean {
    val wasAlerting = levelRank(previous) >= levelRank(from)
    val stillAlerting = enabled && levelRank(current) >= levelRank(from)
    return wasAlerting && !stillAlerting
}

fun crossingAlerts(
    previous: Map<Pollutant, AirLevel>,
    current: Map<Pollutant, AirLevel>,
    alerts: Map<Pollutant, PollutantAlert>,
): List<Pollutant> = Pollutant.entries.filter { pollutant ->
    val alert = alerts[pollutant] ?: PollutantAlert()
    shouldAlert(
        previous[pollutant] ?: AirLevel.UNKNOWN,
        current[pollutant] ?: AirLevel.UNKNOWN,
        alert.from,
        alert.enabled,
    )
}

fun recoveredAlerts(
    previous: Map<Pollutant, AirLevel>,
    current: Map<Pollutant, AirLevel>,
    alerts: Map<Pollutant, PollutantAlert>,
): List<Pollutant> = Pollutant.entries.filter { pollutant ->
    val alert = alerts[pollutant] ?: PollutantAlert()
    shouldClear(
        previous[pollutant] ?: AirLevel.UNKNOWN,
        current[pollutant] ?: AirLevel.UNKNOWN,
        alert.from,
        alert.enabled,
    )
}

fun levelRank(level: AirLevel): Int = when (level) {
    AirLevel.UNKNOWN -> 0
    AirLevel.GOOD -> 1
    AirLevel.FAIR -> 2
    AirLevel.MODERATE -> 3
    AirLevel.POOR -> 4
    AirLevel.VERY_POOR -> 5
    AirLevel.EXTREMELY_POOR -> 6
}
