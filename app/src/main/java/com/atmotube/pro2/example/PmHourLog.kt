package com.atmotube.pro2.example

import java.util.Locale

const val PM_HOUR_MS = 60L * 60L * 1000L

data class HourlyPm(
    val pm1: Double?,
    val pm25: Double?,
    val pm10: Double?,
)

data class PmMinute(
    val pm1Sum: Double = 0.0,
    val pm1Count: Int = 0,
    val pm25Sum: Double = 0.0,
    val pm25Count: Int = 0,
    val pm10Sum: Double = 0.0,
    val pm10Count: Int = 0,
)

/**
 * Minute buckets for the trailing hour of PM. The average stays empty until the first
 * accepted sample is an hour old, so the live reading can be shown on its own until then.
 */
data class PmHourLog(
    val startedAtMillis: Long? = null,
    val minutes: Map<Long, PmMinute> = emptyMap(),
) {
    fun add(atMillis: Long, pm1: Double, pm25: Double, pm10: Double): PmHourLog {
        val sample1 = usablePm(pm1)
        val sample25 = usablePm(pm25)
        val sample10 = usablePm(pm10)
        if (sample1 == null && sample25 == null && sample10 == null) return this
        val lastMinute = minutes.keys.maxOrNull()
        val gap = lastMinute != null && atMillis - lastMinute * 60_000L > PM_HOUR_MS
        val base = if (startedAtMillis == null || gap) PmHourLog(startedAtMillis = atMillis) else this
        val minute = atMillis / 60_000L
        val current = base.minutes[minute] ?: PmMinute()
        val next = current.copy(
            pm1Sum = current.pm1Sum + (sample1 ?: 0.0),
            pm1Count = current.pm1Count + if (sample1 != null) 1 else 0,
            pm25Sum = current.pm25Sum + (sample25 ?: 0.0),
            pm25Count = current.pm25Count + if (sample25 != null) 1 else 0,
            pm10Sum = current.pm10Sum + (sample10 ?: 0.0),
            pm10Count = current.pm10Count + if (sample10 != null) 1 else 0,
        )
        val kept = base.minutes + (minute to next)
        val fromMinute = (atMillis - PM_HOUR_MS) / 60_000L
        return base.copy(minutes = kept.filterKeys { it >= fromMinute })
    }

    fun hourly(nowMillis: Long): HourlyPm? {
        val started = startedAtMillis ?: return null
        if (nowMillis - started < PM_HOUR_MS) return null
        val fromMinute = (nowMillis - PM_HOUR_MS) / 60_000L
        val recent = minutes.filterKeys { it >= fromMinute }
        if (recent.isEmpty()) return null
        return HourlyPm(
            pm1 = mean(recent.values) { it.pm1Sum to it.pm1Count },
            pm25 = mean(recent.values) { it.pm25Sum to it.pm25Count },
            pm10 = mean(recent.values) { it.pm10Sum to it.pm10Count },
        )
    }

    fun encode(): String {
        val header = startedAtMillis?.toString().orEmpty()
        val rows = minutes.toSortedMap().map { (minute, bucket) ->
            listOf(
                minute.toString(),
                format(bucket.pm1Sum),
                bucket.pm1Count.toString(),
                format(bucket.pm25Sum),
                bucket.pm25Count.toString(),
                format(bucket.pm10Sum),
                bucket.pm10Count.toString(),
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    companion object {
        fun decode(text: String?): PmHourLog {
            if (text.isNullOrBlank()) return PmHourLog()
            val lines = text.lines()
            val started = lines.firstOrNull()?.toLongOrNull()
            val minutes = mutableMapOf<Long, PmMinute>()
            for (line in lines.drop(1)) {
                val parts = line.split(",")
                if (parts.size != 7) continue
                val minute = parts[0].toLongOrNull() ?: continue
                val numbers = parts.drop(1).map { it.toDoubleOrNull() }
                if (numbers.any { it == null }) continue
                minutes[minute] = PmMinute(
                    pm1Sum = numbers[0]!!,
                    pm1Count = numbers[1]!!.toInt(),
                    pm25Sum = numbers[2]!!,
                    pm25Count = numbers[3]!!.toInt(),
                    pm10Sum = numbers[4]!!,
                    pm10Count = numbers[5]!!.toInt(),
                )
            }
            return PmHourLog(started, minutes)
        }

        private fun format(value: Double): String = String.format(Locale.US, "%.4f", value)
    }
}

private fun mean(buckets: Collection<PmMinute>, pick: (PmMinute) -> Pair<Double, Int>): Double? {
    var sum = 0.0
    var count = 0
    for (bucket in buckets) {
        val (bucketSum, bucketCount) = pick(bucket)
        sum += bucketSum
        count += bucketCount
    }
    return if (count == 0) null else sum / count
}
