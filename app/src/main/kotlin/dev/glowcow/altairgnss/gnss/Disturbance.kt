package dev.glowcow.altairgnss.gnss

/** What seems to be wrong with the air: nothing, something drowning the satellites out, or signals that are not theirs. */
enum class Disturbance { NONE, INTERFERENCE, SPOOFING }

/**
 * Tells a disturbed sky from a quiet one by what the receiver hears while it has no fix. Many
 * signals of one system at one strength are a transmitter's, not satellites'; strong signals that
 * give no position for a minute are being spoiled by something. Either has to last, and a receiver
 * that drops its signals and starts over in between does not clear it.
 */
class DisturbanceWatch {
    private var since: Long? = null
    private var seen = 0L
    private var kind = Disturbance.NONE

    /** The verdict at [nowMs] of a steady clock; [jammed] is the receiver's own gain saying so. */
    fun update(signals: List<Signal>, nowMs: Long, jammed: Boolean = false): Disturbance {
        if (signals.any { it.usedInFix }) {
            since = null
            kind = Disturbance.NONE
            return if (jammed) Disturbance.INTERFERENCE else Disturbance.NONE
        }
        val verdict = suspect(signals)
        if (verdict != Disturbance.NONE) {
            if (since == null) since = nowMs
            seen = nowMs
            if (verdict == Disturbance.SPOOFING || kind == Disturbance.NONE) kind = verdict
        } else if (since != null && nowMs - seen > HOLD_MS) {
            since = null
            kind = Disturbance.NONE
        }
        val lasted = since?.let { nowMs - it } ?: 0L
        return when {
            kind == Disturbance.SPOOFING && lasted >= SPOOFING_MS -> Disturbance.SPOOFING
            jammed || (kind != Disturbance.NONE && lasted >= INTERFERENCE_MS) -> Disturbance.INTERFERENCE
            else -> Disturbance.NONE
        }
    }

    companion object {
        /** What signals heard without a fix look like at one moment. */
        fun suspect(signals: List<Signal>): Disturbance {
            val heard = signals.filter { it.isHeard }
            val alike = heard.groupBy { it.constellation to it.band }.values.any { group ->
                val levels = group.map { it.cn0DbHz }
                levels.any { from -> levels.count { it >= from && it <= from + ALIKE_DB } >= ALIKE_COUNT }
            }
            if (alike) return Disturbance.SPOOFING
            return if (heard.count { it.cn0DbHz >= STRONG_DB } >= STRONG_COUNT) Disturbance.INTERFERENCE else Disturbance.NONE
        }

        // Satellites of one system come in at strengths far apart; this many within this span do not.
        private const val ALIKE_COUNT = 8
        private const val ALIKE_DB = 6f

        // This many signals this strong give a fix within seconds under a quiet sky.
        private const val STRONG_COUNT = 5
        private const val STRONG_DB = 35f

        private const val SPOOFING_MS = 30_000L
        private const val INTERFERENCE_MS = 60_000L

        // A suspicion outlives this long a gap in what feeds it.
        private const val HOLD_MS = 20_000L
    }
}

/**
 * Watches the gain the receiver sets on each band: it turns the gain down when something loud
 * comes in. [baseline] is the gain of a quiet sky, learnt while there is a fix and kept between
 * launches; keys name a system and a carrier.
 */
class GainWatch(base: Map<String, Double> = emptyMap()) {
    val baseline: MutableMap<String, Double> = base.toMutableMap()

    /** True when [levels], dB, have dropped well under the quiet ones on some band; [fixed] lets them teach the baseline. */
    fun update(levels: Map<String, Double>, fixed: Boolean): Boolean {
        if (fixed) {
            for ((band, level) in levels) {
                val known = baseline[band]
                baseline[band] = when {
                    known == null || level > known -> level
                    // A gain a little lower is the quiet level drifting; far lower is the disturbance itself.
                    known - level < DRIFT_DB -> known + (level - known) * FOLLOW
                    else -> known
                }
            }
        }
        return levels.any { (band, level) -> baseline[band]?.let { level <= it - DROP_DB } == true }
    }

    private companion object {
        const val DROP_DB = 6.0
        const val DRIFT_DB = 3.0
        const val FOLLOW = 0.02
    }
}
