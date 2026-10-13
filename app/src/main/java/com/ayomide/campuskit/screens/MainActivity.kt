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
import com.ayomide.campuskit.ui.ripple
import com.ayomide.campuskit.ui.rounded
import com.ayomide.campuskit.ui.marginTop
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
        actions.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_tune)
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
            contentDescription = "Settings"
            val p = dp(12)
            setPadding(p, p, p, p)
            background = ripple(rounded(R.color.card, 100), 100)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            isClickable = true
            setOnClickListener { open(SettingsActivity::class.java) }
        })
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

        val hello = when (LocalTime.now(zone).hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        add(ui.text("$hello 👋", 24f, R.color.text_primary, true).apply { setPadding(dp(4), 0, 0, 0) })
        add(ui.small(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))).apply { setPadding(dp(4), dp(2), 0, 0) })

        // Hero: next class
        val classes = db.classes()
        val next = Schedule.currentOrNext(classes, now, zone)
        add(ui.tonalCard(R.color.accent_container, { open(TimetableActivity::class.java) }, radiusDp = 28).apply {
            setPadding(dp(22), dp(22), dp(22), dp(22))
            marginTop(dp(16))
            addView(ui.row().apply {
                addView(ui.iconBadge(R.drawable.ic_calendar, R.color.accent, R.color.on_accent, 40))
                addView(ui.text("Next class", 14f, R.color.on_accent_container, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
                })
            })
            if (next == null) {
                addView(ui.text("No classes yet", 26f, R.color.on_accent_container, true).marginTop(dp(14)))
                addView(ui.text("Add your timetable to get a reminder before every lecture.", 14.5f, R.color.on_accent_container))
                addView(ui.chip("Add timetable"))
            } else {
                val s = next.slot
                addView(ui.text(s.code, 34f, R.color.on_accent_container, true).marginTop(dp(12)))
                val where = if (s.venue.isNotBlank()) " · ${s.venue}" else ""
                val day = if (Schedule.epochDay(next.startMillis, zone) == today.toEpochDay()) "Today" else Schedule.DAY_NAMES[s.day - 1]
                addView(ui.text("$day ${Schedule.range(s)}$where", 15f, R.color.on_accent_container))
                addView(ui.chip(if (now >= next.startMillis) "Ongoing · ends ${Schedule.clock(next.endMillis, zone)}"
                    else "Starts " + Countdown.until(next.startMillis - now)))
            }
        })

        // Warnings
        if (!Notifier.canPost(this)) {
            add(warning("Notifications are off", "You won't get lecture, exam or timer reminders. Tap to fix."))
        } else if (!Alarms.canExact(this) && classes.isNotEmpty()) {
            add(warning("Reminders may be late", "Allow \"Alarms & reminders\" for on-time reminders. Tap to fix."))
        }

        // Bento stats
        val exam = db.exams().firstOrNull { it.atMillis > now }
        val streak = Streak.current(db.sessions().map { Schedule.epochDay(it.endMillis, zone) }, today.toEpochDay())
        val month = YearMonth.now(zone)
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = db.expenses(from, to).sumOf { it.amountKobo }
        val allowance = prefs.allowanceKobo
        val due = db.dueCount(null, today.toEpochDay())

        add(ui.section("At a glance"))
        pair(
            Stat(R.drawable.ic_edit, R.color.t_rose, R.color.on_t_rose, "Next exam", exam?.course ?: "None",
                exam?.let { Countdown.until(it.atMillis - now) } ?: "Add your exams", ExamsActivity::class.java),
            Stat(R.drawable.ic_timer, R.color.t_amber, R.color.on_t_amber, "Study streak", "🔥 $streak",
                if (streak == 1) "day" else "days", StudyActivity::class.java),
        )
        pair(
            Stat(R.drawable.ic_wallet, R.color.t_green, R.color.on_t_green,
                if (allowance > 0) "Budget left" else "Spent", Money.format(if (allowance > 0) allowance - spent else spent),
                "this month", BudgetActivity::class.java),
            Stat(R.drawable.ic_layers, R.color.t_violet, R.color.on_t_violet, "Cards due", "$due",
                "flashcards", DecksActivity::class.java),
        )

        // Tools
        add(ui.section("Tools"))
        val tools = listOf(
            Tool(R.drawable.ic_calendar, R.color.t_blue, R.color.on_t_blue, "Timetable", "Lectures & reminders", TimetableActivity::class.java),
            Tool(R.drawable.ic_school, R.color.t_violet, R.color.on_t_violet, "GPA / CGPA", "Results & targets", GpaActivity::class.java),
            Tool(R.drawable.ic_check, R.color.t_green, R.color.on_t_green, "Attendance", "Classes you can miss", AttendanceActivity::class.java),
            Tool(R.drawable.ic_timer, R.color.t_amber, R.color.on_t_amber, "Study timer", "Focus sessions", StudyActivity::class.java),
            Tool(R.drawable.ic_edit, R.color.t_rose, R.color.on_t_rose, "Exams", "Countdowns", ExamsActivity::class.java),
            Tool(R.drawable.ic_wallet, R.color.t_teal, R.color.on_t_teal, "Budget", "Allowance & spending", BudgetActivity::class.java),
            Tool(R.drawable.ic_layers, R.color.t_orange, R.color.on_t_orange, "Flashcards", "Spaced revision", DecksActivity::class.java),
            Tool(R.drawable.ic_tune, R.color.t_slate, R.color.on_t_slate, "Settings", "Reminders & grading", SettingsActivity::class.java),
        )
        for (i in tools.indices step 2) {
            val row = ui.row().apply {
                gravity = Gravity.TOP
                marginTop(dp(12))
            }
            for (j in 0..1) {
                val t = tools[i + j]
                row.addView(ui.card { open(t.target) }.apply {
                    layoutParams = half(j == 0)
                    minimumHeight = dp(132)
                    addView(ui.iconBadge(t.icon, t.bg, t.fg, 48))
                    addView(ui.text(t.name, 16f, R.color.text_primary, true).marginTop(dp(14)))
                    addView(ui.small(t.blurb))
                })
            }
            add(row)
        }

        add(ui.small("Everything stays on your phone. No account, no internet.").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        })
    }

    private class Stat(val icon: Int, val bg: Int, val fg: Int, val label: String, val value: String, val sub: String, val target: Class<*>)
    private class Tool(val icon: Int, val bg: Int, val fg: Int, val name: String, val blurb: String, val target: Class<*>)

    /** Half-width cell in a 2-column row. No top margin: the row carries the spacing. */
    private fun half(first: Boolean) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        if (first) marginEnd = dp(6) else marginStart = dp(6)
    }

    private fun pair(a: Stat, b: Stat) {
        val row = ui.row().apply { gravity = Gravity.TOP; marginTop(dp(12)) }
        for ((i, s) in listOf(a, b).withIndex()) {
            row.addView(ui.tonalCard(s.bg, { open(s.target) }).apply {
                layoutParams = half(i == 0)
                minimumHeight = dp(140)
                addView(ui.row().apply {
                    addView(android.widget.ImageView(this@MainActivity).apply {
                        setImageResource(s.icon)
                        imageTintList = android.content.res.ColorStateList.valueOf(getColor(s.fg))
                        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                    })
                    addView(ui.text(s.label, 13.5f, s.fg, true).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
                    })
                })
                addView(ui.text(s.value, 24f, s.fg, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.marginTop(dp(14)))
                addView(ui.text(s.sub, 13.5f, s.fg))
            })
        }
        add(row)
    }

    private fun warning(title: String, body: String) =
        ui.tonalCard(R.color.danger_container, { open(SettingsActivity::class.java) }).apply {
            addView(ui.row().apply {
                addView(android.widget.ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_warning)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.danger))
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                })
                addView(ui.text(title, 15f, R.color.danger, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                })
            })
            addView(ui.small(body).marginTop(dp(6)))
        }
}
