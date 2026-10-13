package com.ayomide.campuskit.core

object Countdown {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    /** Human text for time until an event: "in 3 days 4 hrs", "in 45 min", "now". */
    fun until(deltaMillis: Long): String {
        if (deltaMillis <= 0) return "now"
        val days = deltaMillis / DAY
        val hours = (deltaMillis % DAY) / HOUR
        val mins = (deltaMillis % HOUR + MIN - 1) / MIN // round minutes up
        return when {
            days > 0 -> "in $days ${plural(days, "day")}" + if (hours > 0) " $hours ${plural(hours, "hr")}" else ""
            hours > 0 -> "in $hours ${plural(hours, "hr")}" + if (mins > 0 && mins < 60) " $mins min" else ""
            else -> "in ${mins.coerceAtLeast(1)} min"
        }
    }

    private fun plural(n: Long, word: String) = if (n == 1L) word else word + "s"
}
