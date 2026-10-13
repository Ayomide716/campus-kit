package com.ayomide.campuskit.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One weekly lecture. [day] is ISO day of week: 1 = Monday … 7 = Sunday. */
data class ClassSlot(
    val id: Long,
    val code: String,
    val title: String,
    val venue: String,
    val day: Int,
    val startMin: Int,
    val endMin: Int,
)

data class ExamItem(val id: Long, val course: String, val atMillis: Long, val venue: String)

enum class EventType { CLASS_REMINDER, CLASS_START, CLASS_END, EXAM_DAY_BEFORE, EXAM_HOUR_BEFORE }

data class Event(val atMillis: Long, val type: EventType, val title: String, val text: String)

data class Occurrence(val slot: ClassSlot, val startMillis: Long, val endMillis: Long)

object Schedule {

    val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun hhmm(minOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minOfDay / 60, minOfDay % 60)

    fun range(slot: ClassSlot) = "${hhmm(slot.startMin)}–${hhmm(slot.endMin)}"

    /** All class occurrences from the start of today for [days] days, sorted. */
    fun occurrences(classes: List<ClassSlot>, nowMillis: Long, zone: ZoneId, days: Int = 8): List<Occurrence> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val out = ArrayList<Occurrence>()
        for (i in 0 until days) {
            val date = today.plusDays(i.toLong())
            val dow = date.dayOfWeek.value
            for (c in classes) {
                if (c.day != dow) continue
                out += Occurrence(c, at(date, c.startMin, zone), at(date, c.endMin, zone))
            }
        }
        out.sortBy { it.startMillis }
        return out
    }

    private fun at(date: LocalDate, minOfDay: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusMinutes(minOfDay.toLong()).toInstant().toEpochMilli()

    /** The class happening now, or else the next one within a week. */
    fun currentOrNext(classes: List<ClassSlot>, nowMillis: Long, zone: ZoneId): Occurrence? =
        occurrences(classes, nowMillis, zone).firstOrNull { it.endMillis > nowMillis }

    fun isClassOngoing(classes: List<ClassSlot>, nowMillis: Long, zone: ZoneId): Boolean =
        occurrences(classes, nowMillis, zone, days = 1).any { nowMillis >= it.startMillis && nowMillis < it.endMillis }

    /**
     * Every reminder/boundary event with time in (afterMillis, untilMillis], sorted.
     * [reminderMin] ≤ 0 disables class reminders; [silenceBoundaries] adds
     * start/end events used to toggle auto-silent.
     */
    fun events(
        classes: List<ClassSlot>,
        exams: List<ExamItem>,
        afterMillis: Long,
        untilMillis: Long,
        zone: ZoneId,
        reminderMin: Int,
        silenceBoundaries: Boolean,
    ): List<Event> {
        val out = ArrayList<Event>()
        // Start a day early so reminders for classes just after afterMillis are included.
        for (o in occurrences(classes, afterMillis - 86_400_000L, zone, days = 10)) {
            val where = if (o.slot.venue.isNotBlank()) " at ${o.slot.venue}" else ""
            if (reminderMin > 0) {
                out += Event(
                    o.startMillis - reminderMin * 60_000L, EventType.CLASS_REMINDER,
                    "${o.slot.code} in $reminderMin min",
                    "${range(o.slot)}$where" + if (o.slot.title.isNotBlank()) " · ${o.slot.title}" else "",
                )
            }
            if (silenceBoundaries) {
                out += Event(o.startMillis, EventType.CLASS_START, o.slot.code, "")
                out += Event(o.endMillis, EventType.CLASS_END, o.slot.code, "")
            }
        }
        for (e in exams) {
            val where = if (e.venue.isNotBlank()) " at ${e.venue}" else ""
            out += Event(e.atMillis - 86_400_000L, EventType.EXAM_DAY_BEFORE, "${e.course} exam tomorrow", "Starts ${clock(e.atMillis, zone)}$where. You've got this!")
            out += Event(e.atMillis - 3_600_000L, EventType.EXAM_HOUR_BEFORE, "${e.course} exam in 1 hour", "Starts ${clock(e.atMillis, zone)}$where")
        }
        return out.filter { it.atMillis in (afterMillis + 1)..untilMillis }.sortedBy { it.atMillis }
    }

    fun clock(millis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return hhmm(t.hour * 60 + t.minute)
    }

    fun epochDay(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
}
