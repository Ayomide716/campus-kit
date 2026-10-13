package com.ayomide.campuskit.core

import java.util.Locale
import kotlin.math.roundToLong

/** Minimum score for each grade on the 5-point scale. Editable in Settings. */
data class Bands(
    val a: Int = 70,
    val b: Int = 60,
    val c: Int = 50,
    val d: Int = 45,
    val e: Int = 40,
) {
    fun isValid(): Boolean = a in 1..100 && a > b && b > c && c > d && d > e && e >= 0
}

/** A course result: either a raw score (graded with the current bands) or a letter. */
sealed class Result {
    data class Score(val value: Int) : Result()
    data class Letter(val grade: String) : Result()
}

object Grading {

    val GRADES = listOf("A", "B", "C", "D", "E", "F")

    fun points(grade: String): Int? = when (grade.uppercase(Locale.US)) {
        "A" -> 5
        "B" -> 4
        "C" -> 3
        "D" -> 2
        "E" -> 1
        "F" -> 0
        else -> null
    }

    fun gradeFor(score: Int, bands: Bands): String = when {
        score >= bands.a -> "A"
        score >= bands.b -> "B"
        score >= bands.c -> "C"
        score >= bands.d -> "D"
        score >= bands.e -> "E"
        else -> "F"
    }

    /** Accepts "68" (score 0–100) or "b"/"B" (letter). Returns null if invalid. */
    fun parseResult(input: String?): Result? {
        val s = input?.trim().orEmpty()
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return if (it in 0..100) Result.Score(it) else null }
        val g = s.uppercase(Locale.US)
        return if (g in GRADES) Result.Letter(g) else null
    }

    fun gradeOf(result: Result, bands: Bands): String = when (result) {
        is Result.Score -> gradeFor(result.value, bands)
        is Result.Letter -> result.grade
    }

    /** Quality points = units × grade points. */
    fun qualityPoints(items: List<Pair<Int, String>>): Int =
        items.sumOf { (units, grade) -> units * (points(grade) ?: 0) }

    /** GPA over (units, grade) pairs, or null when there are no units. */
    fun gpa(items: List<Pair<Int, String>>): Double? {
        val units = items.sumOf { it.first }
        if (units <= 0) return null
        return qualityPoints(items).toDouble() / units
    }

    fun format(gpa: Double?): String =
        if (gpa == null) "–" else String.format(Locale.US, "%.2f", round2(gpa))

    fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

    /** NUC 5-point classes of degree. */
    fun degreeClass(cgpa: Double?): String {
        if (cgpa == null) return "No results yet"
        val c = round2(cgpa)
        return when {
            c >= 4.50 -> "First Class"
            c >= 3.50 -> "Second Class Upper (2:1)"
            c >= 2.40 -> "Second Class Lower (2:2)"
            c >= 1.50 -> "Third Class"
            c >= 1.00 -> "Pass"
            else -> "Below pass"
        }
    }

    /**
     * GPA needed next semester to reach [target] CGPA, given the quality points
     * and units so far. Null when [nextUnits] is not positive.
     */
    fun requiredGpa(target: Double, qualityPoints: Int, unitsSoFar: Int, nextUnits: Int): Double? {
        if (nextUnits <= 0) return null
        return (target * (unitsSoFar + nextUnits) - qualityPoints) / nextUnits
    }
}
