package com.ayomide.campuskit.screens

import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.ClassSlot
import com.ayomide.campuskit.core.Schedule
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Prefs
import com.ayomide.campuskit.system.Alarms
import com.ayomide.campuskit.ui.BaseScreen
import java.time.LocalDate

class TimetableActivity : BaseScreen() {

    override val screenTitle = "Timetable"

    override fun render() {
        val classes = Db.get(this).classes()
        val rem = Prefs(this).reminderMin
        add(ui.button("+ Add class") { edit(null) })
        add(ui.small(if (rem > 0) "You'll get a reminder $rem min before each class. Change it in Settings." else "Class reminders are off. Turn them on in Settings."))

        if (classes.isEmpty()) {
            add(ui.empty("No classes yet. Add each weekly lecture once and it repeats every week."))
            return
        }
        val todayDow = LocalDate.now().dayOfWeek.value
        for (day in 1..7) {
            val list = classes.filter { it.day == day }
            if (list.isEmpty()) continue
            add(ui.section(Schedule.DAY_NAMES[day - 1] + if (day == todayDow) " · today" else ""))
            for (c in list) {
                add(ui.card { edit(c) }.apply {
                    addView(ui.text(Schedule.range(c), 13f, R.color.accent, true))
                    addView(ui.title(c.code + if (c.title.isNotBlank()) " · ${c.title}" else ""))
                    if (c.venue.isNotBlank()) addView(ui.small("📍 ${c.venue}"))
                })
            }
        }
    }

    private fun edit(existing: ClassSlot?) {
        var start = existing?.startMin ?: (8 * 60)
        var end = existing?.endMin ?: (10 * 60)
        val code = ui.input("Course code, e.g. CSC 201", existing?.code ?: "",
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val title = ui.input("Course title (optional)", existing?.title ?: "")
        val venue = ui.input("Venue (optional), e.g. LT2", existing?.venue ?: "")
        val day = ui.spinner(Schedule.DAY_NAMES, (existing?.day ?: LocalDate.now().dayOfWeek.value) - 1)
        val times = ui.row().apply {
            addView(ui.small("From"))
            addView(ui.timeField(start) { start = it })
            addView(ui.small("to"))
            addView(ui.timeField(end) { end = it })
        }
        for (i in 0 until times.childCount) {
            times.getChildAt(i).layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val form = ui.column().apply {
            addView(code); addView(title); addView(venue)
            addView(ui.label("Day")); addView(day)
            addView(ui.label("Time")); addView(times)
        }
        ui.form(
            if (existing == null) "Add class" else "Edit class", form,
            onDelete = existing?.let { e -> { Db.get(this).deleteClass(e.id); changed() } },
        ) {
            val c = code.text.toString().trim()
            when {
                c.isEmpty() -> "Enter the course code."
                end <= start -> "End time must be after start time."
                else -> {
                    Db.get(this).saveClass(
                        ClassSlot(existing?.id ?: 0, c, title.text.toString().trim(), venue.text.toString().trim(),
                            day.selectedItemPosition + 1, start, end)
                    )
                    changed(); null
                }
            }
        }
    }

    private fun changed() {
        Alarms.reschedule(this)
        refresh()
    }
}
