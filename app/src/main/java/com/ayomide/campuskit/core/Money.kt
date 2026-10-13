package com.ayomide.campuskit.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Naira amounts are stored as whole kobo (Long) to avoid floating-point errors. */
object Money {

    /** "1500", "1,500", "₦1,500.50" → kobo. Null if invalid, negative or zero. */
    fun parseKobo(input: String?): Long? {
        val s = input?.trim()?.replace(",", "")?.replace("₦", "")?.replace(" ", "").orEmpty()
        if (s.isEmpty()) return null
        val bd = try {
            BigDecimal(s)
        } catch (e: NumberFormatException) {
            return null
        }
        if (bd.signum() <= 0 || bd.scale() > 2) return null
        return try {
            bd.movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** kobo → "₦1,500" or "₦1,500.50"; negative as "-₦200". */
    fun format(kobo: Long): String {
        val neg = kobo < 0
        val abs = if (neg) -kobo else kobo
        val naira = abs / 100
        val k = abs % 100
        val grouped = naira.toString().reversed().chunked(3).joinToString(",").reversed()
        val body = if (k == 0L) grouped else "$grouped.${k.toString().padStart(2, '0')}"
        return (if (neg) "-₦" else "₦") + body
    }

    /** Amount left per remaining day (including today), rounded down to whole kobo. */
    fun perDay(leftKobo: Long, daysLeft: Int): Long {
        if (daysLeft <= 0 || leftKobo <= 0) return 0
        return BigDecimal(leftKobo).divide(BigDecimal(daysLeft), 0, RoundingMode.DOWN).toLong()
    }
}
