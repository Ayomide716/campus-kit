package com.ayomide.campuskit.screens

import android.text.InputType
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Grading
import com.ayomide.campuskit.core.Result
import com.ayomide.campuskit.data.Course
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Prefs
import com.ayomide.campuskit.data.Semester
import com.ayomide.campuskit.ui.BaseScreen

class GpaActivity : BaseScreen() {

    override val screenTitle = "GPA / CGPA"

    private fun gradeOf(c: Course): String {
        val bands = Prefs(this).bands
        return if (c.score != null) Grading.gradeFor(c.score, bands) else c.grade
    }

    override fun render() {
        val db = Db.get(this)
        val semesters = db.semesters()
        val courses = db.courses()
        val all = courses.map { it.units to gradeOf(it) }
        val cgpa = Grading.gpa(all)
        val totalUnits = all.sumOf { it.first }
        val qp = Grading.qualityPoints(all)

        add(ui.card().apply {
            addView(ui.small("CGPA (5-point scale)"))
            addView(ui.big(Grading.format(cgpa), R.color.accent))
            addView(ui.primary(Grading.degreeClass(cgpa)))
            addView(ui.small("$totalUnits units · $qp quality points · ${semesters.size} semester(s)"))
        })

        if (totalUnits > 0) {
            add(ui.flatButton("🎯 What GPA do I need next semester?") { target(qp, totalUnits) })
        }

        add(ui.button("+ Add semester") { editSemester(null, semesters.size) })

        if (semesters.isEmpty()) {
            add(ui.empty("Add a semester (e.g. \"100L First Semester\"), then add each course with its units and score or grade."))
        }

        for (s in semesters) {
            val list = courses.filter { it.semesterId == s.id }
            val items = list.map { it.units to gradeOf(it) }
            add(ui.section(s.name))
            add(ui.card().apply {
                addView(ui.row().apply {
                    addView(ui.title("GPA ${Grading.format(Grading.gpa(items))}").apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(ui.small("${items.sumOf { it.first }} units").apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    })
                })
                for (c in list) {
                    val g = gradeOf(c)
                    val scoreText = if (c.score != null) "${c.score} → " else ""
                    addView(ui.flatButton("${c.code} · ${c.units} unit${if (c.units == 1) "" else "s"} · $scoreText$g (${Grading.points(g)})") {
                        editCourse(s.id, c)
                    }.apply { gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL; setTextColor(getColor(R.color.text_primary)) })
                }
                addView(ui.row().apply {
                    addView(ui.flatButton("+ Course") { editCourse(s.id, null) })
                    addView(ui.flatButton("Edit semester") { editSemester(s, s.position) })
                })
            })
        }
        add(ui.small("Grades use your school's bands (Settings → Grading). Default: A 70+, B 60–69, C 50–59, D 45–49, E 40–44, F below 40."))
    }

    private fun editSemester(existing: Semester?, position: Int) {
        val name = ui.input("e.g. 200L First Semester", existing?.name ?: "")
        ui.form(
            if (existing == null) "Add semester" else "Edit semester",
            ui.column().apply { addView(name) },
            onDelete = existing?.let { e -> { Db.get(this).deleteSemester(e.id); refresh() } },
        ) {
            val n = name.text.toString().trim()
            if (n.isEmpty()) "Enter a name." else {
                Db.get(this).saveSemester(Semester(existing?.id ?: 0, n, position)); refresh(); null
            }
        }
    }

    private fun editCourse(semesterId: Long, existing: Course?) {
        val code = ui.input("Course code, e.g. MTH 101", existing?.code ?: "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val units = ui.numberInput("Units, e.g. 3", existing?.units?.toString() ?: "")
        val result = ui.input("Score (0–100) or grade (A–F)", existing?.let { it.score?.toString() ?: it.grade } ?: "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        ui.form(
            if (existing == null) "Add course" else "Edit course",
            ui.column().apply {
                addView(code); addView(units); addView(result)
                addView(ui.small("Enter your score to have it graded with your bands, or just the letter grade."))
            },
            onDelete = existing?.let { e -> { Db.get(this).deleteCourse(e.id); refresh() } },
        ) {
            val c = code.text.toString().trim()
            val u = units.text.toString().trim().toIntOrNull()
            val r = Grading.parseResult(result.text.toString())
            when {
                c.isEmpty() -> "Enter the course code."
                u == null || u !in 0..30 -> "Enter units between 0 and 30."
                r == null -> "Enter a score from 0 to 100, or a grade A–F."
                else -> {
                    val (grade, score) = when (r) {
                        is Result.Score -> Grading.gradeFor(r.value, Prefs(this).bands) to r.value
                        is Result.Letter -> r.grade to null
                    }
                    Db.get(this).saveCourse(Course(existing?.id ?: 0, semesterId, c, u, grade, score))
                    refresh(); null
                }
            }
        }
    }

    private fun target(qp: Int, units: Int) {
        val target = ui.decimalInput("Target CGPA, e.g. 4.50")
        val next = ui.numberInput("Units next semester, e.g. 24")
        val out = ui.title("")
        val form = ui.column().apply { addView(target); addView(next); addView(out) }
        ui.form("Target CGPA", form, saveLabel = "Calculate") {
            val t = target.text.toString().toDoubleOrNull()
            val n = next.text.toString().toIntOrNull()
            if (t == null || t < 0 || t > 5) return@form "Enter a target between 0 and 5."
            if (n == null || n <= 0) return@form "Enter next semester's units."
            val need = Grading.requiredGpa(t, qp, units, n)!!
            out.text = when {
                need > 5.0 -> "You'd need ${Grading.format(need)}, which is above 5.00. Aim for it over more semesters."
                need <= 0 -> "You're already there. Any GPA keeps you at ${Grading.format(t)}+."
                else -> "You need a GPA of at least ${Grading.format(need)} next semester."
            }
            "" // keep the dialog open to show the answer
        }
    }
}
