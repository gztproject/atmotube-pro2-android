package com.atmotube.pro2.example

/**
 * PM and GPS settings from the PRO 2 shell, matching the official app.
 *
 * `pm status` is `state mode charging timer`.
 * `pm mode <mode> <charging>` stores the interval and whether PM runs while charging.
 * `gnss status` is `state mode timer`.
 * `gnss mode <mode>` and `gnss timer <minutes>` match Always on, Timer, and Always off.
 */
enum class PmSensorState(val code: Int) {
    OFF(0),
    ON(1),
}

enum class PmMode(val code: Int, val label: String) {
    ON_DEMAND(0, "On demand"),
    ALWAYS_ON(1, "Always on"),
    MIN_15(2, "Every 15 minutes"),
    MIN_10(3, "Every 10 minutes"),
    MIN_5(4, "Every 5 minutes"),
}

enum class GnssSensorState(val code: Int) {
    OFF(0),
    ON(1),
}

enum class GnssMode(val code: Int, val label: String) {
    ALWAYS_OFF(0, "Always off"),
    TIMER(1, "Timer"),
    ALWAYS_ON(2, "Always on"),
}

data class PmStatus(
    val state: PmSensorState,
    val mode: PmMode,
    val charging: Boolean,
    val timer: String,
)

data class GnssStatus(
    val state: GnssSensorState,
    val mode: GnssMode,
    val timerMinutes: Int,
)

val pmModeChoices: List<PmMode> = listOf(
    PmMode.ALWAYS_ON,
    PmMode.MIN_5,
    PmMode.MIN_10,
    PmMode.MIN_15,
    PmMode.ON_DEMAND,
)

val gnssModeChoices: List<GnssMode> = listOf(
    GnssMode.ALWAYS_ON,
    GnssMode.TIMER,
    GnssMode.ALWAYS_OFF,
)

fun parsePmStatus(raw: String): PmStatus? {
    val numbers = raw.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    if (numbers.size < 4) return null
    val state = numbers[numbers.size - 4]
    val mode = numbers[numbers.size - 3]
    val charging = numbers[numbers.size - 2]
    val timer = numbers[numbers.size - 1]
    val sensor = PmSensorState.entries.firstOrNull { it.code == state } ?: return null
    val interval = PmMode.entries.firstOrNull { it.code == mode } ?: return null
    if (charging != 0 && charging != 1) return null
    return PmStatus(sensor, interval, charging == 1, timer.toString())
}

fun parseGnssStatus(raw: String): GnssStatus? {
    val numbers = raw.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    if (numbers.size < 3) return null
    val state = numbers[numbers.size - 3]
    val mode = numbers[numbers.size - 2]
    val timer = numbers[numbers.size - 1]
    val sensor = GnssSensorState.entries.firstOrNull { it.code == state } ?: return null
    val gnssMode = GnssMode.entries.firstOrNull { it.code == mode } ?: return null
    return GnssStatus(sensor, gnssMode, timer)
}

fun pmModeArgs(mode: PmMode, charging: Boolean): Array<String> =
    arrayOf(mode.code.toString(), if (charging) "1" else "0")

fun gnssModeArg(mode: GnssMode): Array<String> = arrayOf(mode.code.toString())

fun gnssTimerArg(minutes: Int): Array<String> = arrayOf(minutes.coerceIn(1, 60).toString())

/** Editor value for the quiet-time control. A zero from the device means the usual 10 minutes. */
fun gnssTimerEditor(status: GnssStatus): Int =
    if (status.timerMinutes in 1..60) status.timerMinutes else 10
