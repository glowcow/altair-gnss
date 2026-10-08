package dev.glowcow.altairgnss.recording

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** The marks along the axes of a chart: round values, as far apart as their labels need. */
object Scale {
    private const val S = 1_000L
    private const val M = 60 * S
    private const val H = 60 * M

    // Steps of time a clock is read in.
    private val TIME_STEPS = listOf(5 * S, 10 * S, 15 * S, 30 * S, M, 2 * M, 5 * M, 10 * M, 15 * M, 30 * M, H, 2 * H, 3 * H, 6 * H, 12 * H, 24 * H)

    /**
     * The step between marks of time for a chart of [spanMs] that is [width] wide, when a label
     * with its gap takes [label] of that width: the shortest round step whose labels do not touch.
     */
    fun timeStep(spanMs: Long, width: Float, label: Float): Long {
        if (spanMs <= 0 || width <= 0f) return TIME_STEPS.last()
        return TIME_STEPS.firstOrNull { it.toDouble() / spanMs * width >= label } ?: (TIME_STEPS.last() * ceil(label / width * spanMs / TIME_STEPS.last()).toLong().coerceAtLeast(1))
    }

    /** The times marked on a chart of [spanMs]: every [step] from the start, the start itself left out. */
    fun timeMarks(spanMs: Long, step: Long): List<Long> = (1..spanMs / step).map { it * step }

    /** A time on the axis: without seconds where the [step] has none. */
    fun timeLabel(ms: Long, step: Long): String {
        val s = ms / 1000
        return if (step % M == 0L) "%d:%02d".format(s / 3600, s / 60 % 60) else "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    /** Round values between [low] and [high], no more than [most] of them: steps of 1, 2 or 5 times a power of ten. */
    fun levels(low: Double, high: Double, most: Int): List<Double> {
        val span = high - low
        if (span <= 0 || most < 1) return emptyList()
        val rough = span / most
        val power = 10.0.pow(floor(log10(rough)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * power }.first { it >= rough }
        val first = ceil(low / step)
        val last = floor(high / step)
        return (first.toLong()..last.toLong()).map { it * step }
    }
}
