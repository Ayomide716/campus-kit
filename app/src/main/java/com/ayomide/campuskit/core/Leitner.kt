package com.ayomide.campuskit.core

/**
 * Simple Leitner spaced repetition: 5 boxes. "Got it" moves a card up a box and
 * pushes its due date out; "Again" sends it back to box 1, due today.
 */
object Leitner {

    const val MAX_BOX = 5

    /** Days until next review after reaching box index (1..5). */
    private val INTERVAL_DAYS = intArrayOf(0, 1, 2, 4, 7, 14)

    data class Next(val box: Int, val dueDay: Long)

    fun answer(box: Int, correct: Boolean, today: Long): Next =
        if (correct) {
            val nb = (box.coerceIn(1, MAX_BOX) + 1).coerceAtMost(MAX_BOX)
            Next(nb, today + INTERVAL_DAYS[nb])
        } else {
            Next(1, today)
        }

    fun isDue(dueDay: Long, today: Long) = dueDay <= today

    /**
     * Parses pasted lines into (front, back) pairs. Each line is
     * "front | back", "front = back" or "front<TAB>back". Blank or
     * malformed lines are skipped.
     */
    fun parseBulk(text: String): List<Pair<String, String>> =
        text.lines().mapNotNull { line ->
            val idx = listOf('|', '\t', '=').map { line.indexOf(it) }.filter { it > 0 }.minOrNull()
                ?: return@mapNotNull null
            val front = line.substring(0, idx).trim()
            val back = line.substring(idx + 1).trim()
            if (front.isEmpty() || back.isEmpty()) null else front to back
        }
}
