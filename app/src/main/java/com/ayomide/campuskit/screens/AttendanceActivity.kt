package com.ayomide.campuskit.screens

import android.text.InputType
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.AttendanceCalc
import com.ayomide.campuskit.data.AttCourse
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.ui.BaseScreen

class AttendanceActivity : BaseScreen() {

    override val screenTitle = "Attendance"

    override fun render() {
        val db = Db.get(this)
        val list = db.attendance()
        add(ui.button("+ Add course") { edit(null) })
        add(ui.flatButton("Import courses from timetable") { importFromTimetable() })
        if (list.isEmpty()) {
            add(ui.empty("Track each course: tap ✓ when you attend and ✗ when you miss. You'll see how many more classes you can miss and still meet the requirement (usually 75%)."))
            return
        }
        for (a in list) {
            val s = AttendanceCalc.status(a.planned, a.requiredPct, a.attended, a.missed)
            add(ui.card { edit(a) }.apply {
                addView(ui.title(a.code))
                val pct = s.percent?.let { "$it%" } ?: "–"
                addView(ui.small("Attended ${a.attended} of ${s.held} held ($pct) · need ${a.requiredPct}% of ${a.planned}"))
                addView(ui.progress(s.percent ?: 0, if (s.belowRequirement) R.color.danger else R.color.success))
                val msg = when {
                    s.belowRequirement -> "⚠ Below ${a.requiredPct}%: missed ${-s.missesLeft} more than allowed"
                    s.missesLeft == 0 -> "Don't miss any more classes"
                    else -> "You can miss ${s.missesLeft} more class${if (s.missesLeft == 1) "" else "es"}"
                }
                addView(ui.text(msg, 14f, if (s.belowRequirement || s.missesLeft == 0) R.color.danger else R.color.success, true))
                addView(ui.row().apply {
                    addView(ui.flatButton("✓ Attended") { db.saveAttendance(a.copy(attended = a.attended + 1)); refresh() })
                    addView(ui.flatButton("✗ Missed") { db.saveAttendance(a.copy(missed = a.missed + 1)); refresh() })
                })
            })
        }
        add(ui.small("Tap a course to edit its numbers or fix a mistake."))
    }

    private fun edit(existing: AttCourse?) {
        val code = ui.input("Course code", existing?.code ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val planned = ui.numberInput("Total classes this semester", existing?.planned?.toString() ?: "26")
        val req = ui.numberInput("Required %", existing?.requiredPct?.toString() ?: "75")
        val att = ui.numberInput("Attended so far", existing?.attended?.toString() ?: "0")
        val mis = ui.numberInput("Missed so far", existing?.missed?.toString() ?: "0")
        val form = ui.column().apply {
            addView(code)
            addView(ui.label("Total classes this semester (e.g. 2 a week × 13 weeks = 26)")); addView(planned)
            addView(ui.label("Required attendance %")); addView(req)
            addView(ui.label("Attended")); addView(att)
            addView(ui.label("Missed")); addView(mis)
        }
        ui.form(
            if (existing == null) "Add course" else "Edit ${existing.code}", form,
            onDelete = existing?.let { e -> { Db.get(this).deleteAttendance(e.id); refresh() } },
        ) {
            val c = code.text.toString().trim()
            val p = planned.text.toString().toIntOrNull()
            val r = req.text.toString().toIntOrNull()
            val a = att.text.toString().toIntOrNull()
            val m = mis.text.toString().toIntOrNull()
            when {
                c.isEmpty() -> "Enter the course code."
                p == null || p !in 1..500 -> "Enter the total number of classes."
                r == null || r !in 0..100 -> "Required % must be 0–100."
                a == null || m == null || a < 0 || m < 0 -> "Attended and missed must be 0 or more."
                else -> {
                    Db.get(this).saveAttendance(AttCourse(existing?.id ?: 0, c, p, r, a, m)); refresh(); null
                }
            }
        }
    }

    private fun importFromTimetable() {
        val db = Db.get(this)
        val existing = db.attendance().map { it.code.uppercase() }.toSet()
        val perWeek = db.classes().groupBy { it.code.trim().uppercase() }
        var added = 0
        for ((code, slots) in perWeek) {
            if (code in existing) continue
            db.saveAttendance(AttCourse(0, code, slots.size * 13, 75, 0, 0)); added++
        }
        android.widget.Toast.makeText(
            this,
            if (added == 0) "Nothing new to import. Add classes to your timetable first." else "Added $added course(s), assuming 13 weeks at 75%.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        refresh()
    }
}
