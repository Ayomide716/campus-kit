package com.ayomide.campuskit.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Countdown
import com.ayomide.campuskit.core.Money
import com.ayomide.campuskit.core.Schedule
import com.ayomide.campuskit.core.Streak
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Prefs
import com.ayomide.campuskit.system.Alarms
import com.ayomide.campuskit.system.Notifier
import com.ayomide.campuskit.ui.BaseScreen
import com.ayomide.campuskit.ui.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : BaseScreen() {

    override val screenTitle = "Campus Kit"
    override val showBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        val prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.askedNotifications = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        actions.addView(ui.flatButton("⚙") { open(SettingsActivity::class.java) }.apply { contentDescription = "Settings" })
    }

    override fun onResume() {
        super.onResume()
        Alarms.reschedule(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun open(c: Class<*>) = startActivity(Intent(this, c))

    override fun render() {
        val db = Db.get(this)
        val prefs = Prefs(this)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)

        val hour = LocalTime.now(zone).hour
        val hello = when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        add(ui.small("$hello 👋 · " + today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))))

        // Next class
        val classes = db.classes()
        val next = Schedule.currentOrNext(classes, now, zone)
        add(ui.card { open(TimetableActivity::class.java) }.apply {
            addView(ui.small("NEXT CLASS"))
            if (next == null) {
                addView(ui.title("No classes yet"))
                addView(ui.small("Tap to add your timetable and get reminders before each lecture."))
            } else {
                val s = next.slot
                addView(ui.big(s.code).apply { textSize = 26f })
                val where = if (s.venue.isNotBlank()) " · ${s.venue}" else ""
                val day = if (Schedule.epochDay(next.startMillis, zone) == today.toEpochDay()) "Today" else Schedule.DAY_NAMES[s.day - 1]
                addView(ui.primary("$day ${Schedule.range(s)}$where"))
                val status = if (now >= next.startMillis) "Ongoing, ends ${Schedule.clock(next.endMillis, zone)}"
                else "Starts " + Countdown.until(next.startMillis - now)
                addView(ui.text(status, 14f, R.color.accent, true))
            }
        })

        // Stats
        val exam = db.exams().firstOrNull { it.atMillis > now }
        val days = db.sessions().map { Schedule.epochDay(it.endMillis, zone) }
        val streak = Streak.current(days, today.toEpochDay())
        val month = YearMonth.now(zone)
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = db.expenses(from, to).sumOf { it.amountKobo }
        val allowance = prefs.allowanceKobo
        val due = db.dueCount(null, today.toEpochDay())

        statRow(
            Stat("NEXT EXAM", exam?.course ?: "None", exam?.let { Countdown.until(it.atMillis - now) } ?: "Add exams", ExamsActivity::class.java),
            Stat("STUDY STREAK", "🔥 $streak", if (streak == 1) "day" else "days", StudyActivity::class.java),
        )
        statRow(
            Stat(
                "BUDGET LEFT",
                if (allowance > 0) Money.format(allowance - spent) else Money.format(spent),
                if (allowance > 0) "this month" else "spent this month",
                BudgetActivity::class.java,
            ),
            Stat("CARDS DUE", "$due", "flashcards", DecksActivity::class.java),
        )

        // Warnings
        if (!Notifier.canPost(this)) {
            add(ui.card { open(SettingsActivity::class.java) }.apply {
                addView(ui.text("Notifications are off", 15f, R.color.danger, true))
                addView(ui.small("You won't get lecture, exam or timer reminders. Tap to fix in Settings."))
            })
        } else if (!Alarms.canExact(this) && classes.isNotEmpty()) {
            add(ui.card { open(SettingsActivity::class.java) }.apply {
                addView(ui.text("Reminders may be a few minutes late", 15f, R.color.warning, true))
                addView(ui.small("Allow \"Alarms & reminders\" for on-time reminders. Tap to open Settings."))
            })
        }

        // Tools
        add(ui.section("Tools"))
        toolRow(Tool("🗓", "Timetable", TimetableActivity::class.java), Tool("🎓", "GPA / CGPA", GpaActivity::class.java))
        toolRow(Tool("✅", "Attendance", AttendanceActivity::class.java), Tool("⏱", "Study timer", StudyActivity::class.java))
        toolRow(Tool("📝", "Exams", ExamsActivity::class.java), Tool("💸", "Budget", BudgetActivity::class.java))
        toolRow(Tool("🧠", "Flashcards", DecksActivity::class.java), Tool("⚙", "Settings", SettingsActivity::class.java))

        add(ui.small("Everything stays on your phone. No account, no internet.").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private class Stat(val label: String, val value: String, val sub: String, val target: Class<*>)
    private class Tool(val icon: String, val name: String, val target: Class<*>)

    private fun halfParams(first: Boolean) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
        topMargin = dp(12)
        if (first) marginEnd = dp(6) else marginStart = dp(6)
    }

    private fun statRow(a: Stat, b: Stat) {
        val row = ui.row().apply { gravity = Gravity.FILL_VERTICAL }
        for ((i, s) in listOf(a, b).withIndex()) {
            row.addView(ui.card { open(s.target) }.apply {
                layoutParams = halfParams(i == 0)
                addView(ui.text(s.label, 11f, R.color.text_secondary, true))
                addView(ui.text(s.value, 20f, R.color.text_primary, true).apply { maxLines = 1 })
                addView(ui.small(s.sub))
            })
        }
        add(row)
    }

    private fun toolRow(a: Tool, b: Tool) {
        val row = ui.row()
        for ((i, t) in listOf(a, b).withIndex()) {
            row.addView(ui.card { open(t.target) }.apply {
                layoutParams = halfParams(i == 0)
                gravity = Gravity.CENTER
                addView(ui.text(t.icon, 28f).apply { gravity = Gravity.CENTER })
                addView(ui.text(t.name, 15f, R.color.text_primary, true).apply { gravity = Gravity.CENTER })
            })
        }
        add(row)
    }
}
