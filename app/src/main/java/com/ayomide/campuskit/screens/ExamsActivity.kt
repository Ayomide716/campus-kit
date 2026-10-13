package com.ayomide.campuskit.screens

import android.text.InputType
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Countdown
import com.ayomide.campuskit.core.ExamItem
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.system.Alarms
import com.ayomide.campuskit.ui.BaseScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExamsActivity : BaseScreen() {

    override val screenTitle = "Exams"

    override fun render() {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val exams = Db.get(this).exams()
        val upcoming = exams.filter { it.atMillis > now }
        val past = exams.filter { it.atMillis <= now }
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.getDefault())

        add(ui.button("+ Add exam") { edit(null) })
        add(ui.small("Reminders arrive the day before and 1 hour before each exam."))
        if (exams.isEmpty()) {
            add(ui.empty("No exams yet. Add them to see a countdown."))
            return
        }
        for (e in upcoming) {
            add(ui.card { edit(e) }.apply {
                addView(ui.title(e.course))
                addView(ui.text(Countdown.until(e.atMillis - now), 22f, R.color.accent, true))
                addView(ui.small(Instant.ofEpochMilli(e.atMillis).atZone(zone).format(fmt) + if (e.venue.isNotBlank()) " · ${e.venue}" else ""))
            })
        }
        if (past.isNotEmpty()) {
            add(ui.section("Done"))
            for (e in past.reversed()) {
                add(ui.card { edit(e) }.apply {
                    alpha = 0.6f
                    addView(ui.primary("✓ ${e.course}"))
                    addView(ui.small(Instant.ofEpochMilli(e.atMillis).atZone(zone).format(fmt)))
                })
            }
        }
    }

    private fun edit(existing: ExamItem?) {
        val zone = ZoneId.systemDefault()
        val initial = existing?.let { Instant.ofEpochMilli(it.atMillis).atZone(zone) }
        var date: LocalDate = initial?.toLocalDate() ?: LocalDate.now(zone).plusDays(7)
        var time = initial?.let { it.hour * 60 + it.minute } ?: (9 * 60)
        val course = ui.input("Course, e.g. GST 101", existing?.course ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val venue = ui.input("Venue (optional)", existing?.venue ?: "")
        val form = ui.column().apply {
            addView(course); addView(venue)
            addView(ui.label("Date")); addView(ui.dateField(date) { date = it })
            addView(ui.label("Time")); addView(ui.timeField(time) { time = it })
        }
        ui.form(
            if (existing == null) "Add exam" else "Edit exam", form,
            onDelete = existing?.let { e -> { Db.get(this).deleteExam(e.id); changed() } },
        ) {
            val c = course.text.toString().trim()
            if (c.isEmpty()) "Enter the course." else {
                val at = date.atStartOfDay(zone).plusMinutes(time.toLong()).toInstant().toEpochMilli()
                Db.get(this).saveExam(ExamItem(existing?.id ?: 0, c, at, venue.text.toString().trim()))
                changed(); null
            }
        }
    }

    private fun changed() {
        Alarms.reschedule(this)
        refresh()
    }
}
