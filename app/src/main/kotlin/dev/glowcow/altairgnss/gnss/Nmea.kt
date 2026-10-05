package dev.glowcow.altairgnss.gnss

/** Dilution of precision: position, horizontal, vertical. */
data class Dop(val pdop: Float, val hdop: Float, val vdop: Float)

object Nmea {
    /** DOP values of a GSA sentence; null for any other sentence, a bad checksum or empty fields. */
    fun dop(sentence: String): Dop? {
        val s = sentence.trim()
        if (s.length < 7 || s[0] != '$' || s.substring(3, 6) != "GSA") return null
        val star = s.indexOf('*')
        val body = if (star >= 0) s.substring(1, star) else s.substring(1)
        if (star >= 0 && !checksumMatches(body, s.substring(star + 1))) return null
        // Talker+GSA, mode, fix type, twelve satellite slots, then PDOP, HDOP, VDOP.
        val fields = body.split(',')
        if (fields.size < 18) return null
        val pdop = fields[15].toFloatOrNull() ?: return null
        val hdop = fields[16].toFloatOrNull() ?: return null
        val vdop = fields[17].toFloatOrNull() ?: return null
        return Dop(pdop, hdop, vdop)
    }

    fun checksum(body: String): Int = body.fold(0) { acc, ch -> acc xor ch.code }

    private fun checksumMatches(body: String, hex: String): Boolean =
        hex.take(2).toIntOrNull(16) == checksum(body)
}
