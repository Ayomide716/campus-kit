package com.ayomide.campuskit.core

object Streak {
    /**
     * Consecutive days (as epoch days) with at least one study session, ending
     * today or yesterday. A streak isn't broken until a whole day is missed.
     */
    fun current(days: Collection<Long>, today: Long): Int {
        val set = days.toHashSet()
        var d = when {
            today in set -> today
            (today - 1) in set -> today - 1
            else -> return 0
        }
        var n = 0
        while (d in set) {
            n++; d--
        }
        return n
    }
}
