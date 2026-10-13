package com.ayomide.campuskit.core

import kotlin.math.ceil

object AttendanceCalc {

    data class Status(
        val held: Int,
        /** Attended / held as a whole percent, or null if nothing held yet. */
        val percent: Int?,
        val allowedMisses: Int,
        /** How many more classes may be missed; negative means already below. */
        val missesLeft: Int,
    ) {
        val belowRequirement: Boolean get() = missesLeft < 0
    }

    fun status(planned: Int, requiredPct: Int, attended: Int, missed: Int): Status {
        val p = planned.coerceAtLeast(0)
        val req = requiredPct.coerceIn(0, 100)
        val minToAttend = ceil(p * req / 100.0).toInt()
        val allowed = (p - minToAttend).coerceAtLeast(0)
        val held = attended + missed
        val pct = if (held > 0) (attended * 100 + held / 2) / held else null
        return Status(held, pct, allowed, allowed - missed)
    }
}
