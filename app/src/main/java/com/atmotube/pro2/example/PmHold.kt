package com.atmotube.pro2.example

import java.util.Locale

private const val MISSING_COUNT = 0xFFFF

/** A PM packet with a real mass reading, rather than off or heating. */
fun pmIsLive(pm: AtmotubePmReading): Boolean =
    usablePm(pm.pm1) != null || usablePm(pm.pm25) != null || usablePm(pm.pm10) != null

fun formatParticleCount(count: Int): String =
    if (count == MISSING_COUNT || count == 0x7FFF) "—" else count.toString()

data class PmView(
    val reading: AtmotubePmReading?,
    val held: Boolean,
)

/**
 * While the PM sensor is on, samples accumulate. When it turns off between interval reads,
 * the average of that burst stays on screen until the next live sample.
 */
data class PmTracker(
    val openSamples: Int = 0,
    val pm1Sum: Double = 0.0,
    val pm1Count: Int = 0,
    val pm25Sum: Double = 0.0,
    val pm25Count: Int = 0,
    val pm10Sum: Double = 0.0,
    val pm10Count: Int = 0,
    val pm05Sum: Long = 0,
    val pm05Count: Int = 0,
    val pm1pSum: Long = 0,
    val pm1pCount: Int = 0,
    val pm25pSum: Long = 0,
    val pm25pCount: Int = 0,
    val pm10pSum: Long = 0,
    val pm10pCount: Int = 0,
    val sizeSum: Double = 0.0,
    val sizeCount: Int = 0,
    val held: AtmotubePmReading? = null,
) {
    fun accept(pm: AtmotubePmReading): PmTracker {
        if (!pmIsLive(pm)) {
            if (openSamples == 0) return this
            return copy(
                openSamples = 0,
                pm1Sum = 0.0,
                pm1Count = 0,
                pm25Sum = 0.0,
                pm25Count = 0,
                pm10Sum = 0.0,
                pm10Count = 0,
                pm05Sum = 0,
                pm05Count = 0,
                pm1pSum = 0,
                pm1pCount = 0,
                pm25pSum = 0,
                pm25pCount = 0,
                pm10pSum = 0,
                pm10pCount = 0,
                sizeSum = 0.0,
                sizeCount = 0,
                held = freeze(),
            )
        }
        val size = usablePm(pm.typicalParticleSize)
        val pm05 = usableCount(pm.pm05Particles)
        val pm1p = usableCount(pm.pm1Particles)
        val pm25p = usableCount(pm.pm25Particles)
        val pm10p = usableCount(pm.pm10Particles)
        return copy(
            openSamples = openSamples + 1,
            pm1Sum = pm1Sum + (usablePm(pm.pm1) ?: 0.0),
            pm1Count = pm1Count + if (usablePm(pm.pm1) != null) 1 else 0,
            pm25Sum = pm25Sum + (usablePm(pm.pm25) ?: 0.0),
            pm25Count = pm25Count + if (usablePm(pm.pm25) != null) 1 else 0,
            pm10Sum = pm10Sum + (usablePm(pm.pm10) ?: 0.0),
            pm10Count = pm10Count + if (usablePm(pm.pm10) != null) 1 else 0,
            pm05Sum = pm05Sum + (pm05 ?: 0),
            pm05Count = pm05Count + if (pm05 != null) 1 else 0,
            pm1pSum = pm1pSum + (pm1p ?: 0),
            pm1pCount = pm1pCount + if (pm1p != null) 1 else 0,
            pm25pSum = pm25pSum + (pm25p ?: 0),
            pm25pCount = pm25pCount + if (pm25p != null) 1 else 0,
            pm10pSum = pm10pSum + (pm10p ?: 0),
            pm10pCount = pm10pCount + if (pm10p != null) 1 else 0,
            sizeSum = sizeSum + (size ?: 0.0),
            sizeCount = sizeCount + if (size != null) 1 else 0,
        )
    }

    fun display(current: AtmotubePmReading?): PmView {
        if (current != null && pmIsLive(current)) return PmView(current, held = false)
        val frozen = held ?: return PmView(current, held = false)
        return PmView(frozen, held = true)
    }

    fun encode(): String {
        val frozen = held ?: return ""
        return listOf(
            format(frozen.pm1),
            format(frozen.pm25),
            format(frozen.pm10),
            frozen.pm05Particles.toString(),
            frozen.pm1Particles.toString(),
            frozen.pm25Particles.toString(),
            frozen.pm10Particles.toString(),
            format(frozen.typicalParticleSize),
        ).joinToString(",")
    }

    private fun freeze(): AtmotubePmReading = AtmotubePmReading(
        pm1 = mean(pm1Sum, pm1Count),
        pm25 = mean(pm25Sum, pm25Count),
        pm10 = mean(pm10Sum, pm10Count),
        pm05Particles = meanCount(pm05Sum, pm05Count),
        pm1Particles = meanCount(pm1pSum, pm1pCount),
        pm25Particles = meanCount(pm25pSum, pm25pCount),
        pm10Particles = meanCount(pm10pSum, pm10pCount),
        typicalParticleSize = if (sizeCount == 0) 6553.5 else sizeSum / sizeCount,
    )

    companion object {
        fun decode(text: String?): PmTracker {
            if (text.isNullOrBlank()) return PmTracker()
            val parts = text.split(",")
            if (parts.size != 8) return PmTracker()
            val numbers = parts.map { it.toDoubleOrNull() }
            if (numbers.any { it == null }) return PmTracker()
            return PmTracker(
                held = AtmotubePmReading(
                    pm1 = numbers[0]!!,
                    pm25 = numbers[1]!!,
                    pm10 = numbers[2]!!,
                    pm05Particles = numbers[3]!!.toInt(),
                    pm1Particles = numbers[4]!!.toInt(),
                    pm25Particles = numbers[5]!!.toInt(),
                    pm10Particles = numbers[6]!!.toInt(),
                    typicalParticleSize = numbers[7]!!,
                ),
            )
        }

        private fun format(value: Double): String = String.format(Locale.US, "%.4f", value)
    }
}

private fun usableCount(count: Int): Int? =
    if (count == MISSING_COUNT || count == 0x7FFF) null else count

private fun mean(sum: Double, count: Int): Double = if (count == 0) 6553.5 else sum / count

private fun meanCount(sum: Long, count: Int): Int =
    if (count == 0) MISSING_COUNT else ((sum + count / 2) / count).toInt()
