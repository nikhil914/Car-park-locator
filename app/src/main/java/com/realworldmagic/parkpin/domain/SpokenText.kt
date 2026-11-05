package com.realworldmagic.parkpin.domain

/** Screen-reader friendly phrases (units spelled out so TalkBack does not say "m" or "ft"). */
object SpokenText {
    /** "45 meters" style text for a distance. */
    fun distance(meters: Double, metric: Boolean): String = spellUnits(GeoMath.formatDistance(meters, metric))

    /** Replaces the trailing unit abbreviation of a [GeoMath.formatDistance] string. */
    fun spellUnits(formatted: String): String {
        val parts = formatted.trim().split(' ')
        if (parts.size != 2) return formatted
        val value = parts[0]
        val one = value == "1"
        val word = when (parts[1]) {
            "m" -> if (one) "meter" else "meters"
            "km" -> if (one) "kilometer" else "kilometers"
            "ft" -> if (one) "foot" else "feet"
            "mi" -> if (one) "mile" else "miles"
            else -> return formatted
        }
        return "$value $word"
    }
}
