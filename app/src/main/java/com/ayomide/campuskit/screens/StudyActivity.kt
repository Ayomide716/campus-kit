package com.ayomide.campuskit.screens

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Schedule
import com.ayomide.campuskit.core.Streak
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Prefs
import com.ayomide.campuskit.system.StudyTimer
import com.ayomide.campuskit.ui.BaseScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class StudyActivity : BaseScreen() {

    override val screenTitle = "Study timer"

    private val handler = Handler(Looper.getMainLooper())
    private var clock: TextView? = null
    private var wasRunning = false

    private val tick = object : Runnable {
        override fun run() {
            val prefs = Prefs(this@StudyActivity)
            if (prefs.timerEnd > 0) {
                if (StudyTimer.completeIfDue(this@StudyActivity)) {
                    refresh(); return
                }
                val left = ((prefs.timerEnd - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                clock?.text = String.format(Locale.US, "%02d:%02d", left / 60, left % 60)
                handler.postDelayed(this, 500)
            } else if (wasRunning) {
                refresh()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun render() {
        handler.removeCallbacks(tick)
        val prefs = Prefs(this)
        val db = Db.get(this)
        val running = prefs.timerEnd > 0
        wasRunning = running

        add(ui.card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ui.small(if (!running) "READY" else if (prefs.timerIsFocus) "FOCUS" else "BREAK").apply { gravity = Gravity.CENTER })
            val c = ui.text(
                String.format(Locale.US, "%02d:00", if (running) prefs.timerMinutes else prefs.focusMin),
                56f, if (running && !prefs.timerIsFocus) R.color.success else R.color.accent, true,
            ).apply { gravity = Gravity.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD) }
            clock = c
            addView(c)
            if (running) {
                addView(ui.button("Stop") { StudyTimer.stop(this@StudyActivity); refresh() })
                addView(ui.small("You can leave the app. A notification counts down and alerts you when time's up.").apply { gravity = Gravity.CENTER })
            } else {
                addView(ui.button("▶ Focus ${prefs.focusMin} min") { StudyTimer.start(this@StudyActivity, true, prefs.focusMin); refresh() })
                addView(ui.button("☕ Break ${prefs.breakMin} min") { StudyTimer.start(this@StudyActivity, false, prefs.breakMin); refresh() })
                addView(ui.flatButton("Change lengths") { lengths() })
            }
        })
        if (running) handler.post(tick)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = db.sessions()
        val streak = Streak.current(sessions.map { Schedule.epochDay(it.endMillis, zone) }, today.toEpochDay())

        add(ui.section("Your progress"))
        add(ui.card().apply {
            addView(ui.title("🔥 $streak day streak"))
            addView(ui.primary("Today: ${hm(db.studyMinutesSince(startOfToday))}"))
            addView(ui.primary("This week: ${hm(db.studyMinutesSince(startOfWeek))}"))
            addView(ui.small("${sessions.size} focus sessions in total"))
        })

        if (sessions.isNotEmpty()) {
            add(ui.section("Recent sessions"))
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())
            add(ui.card().apply {
                for (s in sessions.take(10)) {
                    val t = java.time.Instant.ofEpochMilli(s.endMillis).atZone(zone).format(fmt)
                    addView(ui.primary("${s.minutes} min · $t"))
                }
            })
            add(ui.flatButton("Clear history") {
                ui.confirm("Clear study history?", "This removes all sessions and resets your streak.", "Clear") {
                    db.clearSessions(); refresh()
                }
            })
        }
    }

    private fun hm(minutes: Long): String =
        if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

    private fun lengths() {
        val prefs = Prefs(this)
        val focus = ui.numberInput("Focus minutes", prefs.focusMin.toString())
        val brk = ui.numberInput("Break minutes", prefs.breakMin.toString())
        ui.form("Timer lengths", ui.column().apply {
            addView(ui.label("Focus (minutes)")); addView(focus)
            addView(ui.label("Break (minutes)")); addView(brk)
        }) {
            val f = focus.text.toString().toIntOrNull()
            val b = brk.text.toString().toIntOrNull()
            if (f == null || f !in 1..180 || b == null || b !in 1..60) "Focus 1–180 min, break 1–60 min."
            else {
                prefs.focusMin = f; prefs.breakMin = b; refresh(); null
            }
        }
    }
}
