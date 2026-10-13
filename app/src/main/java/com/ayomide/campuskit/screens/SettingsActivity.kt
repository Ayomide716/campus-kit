package com.ayomide.campuskit.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ayomide.campuskit.R
import com.ayomide.campuskit.core.Bands
import com.ayomide.campuskit.data.Prefs
import com.ayomide.campuskit.system.Alarms
import com.ayomide.campuskit.system.Notifier
import com.ayomide.campuskit.system.Ringer
import com.ayomide.campuskit.ui.BaseScreen

class SettingsActivity : BaseScreen() {

    override val screenTitle = "Settings"

    override fun render() {
        val prefs = Prefs(this)

        add(ui.section("Reminders"))
        add(ui.card {
            val options = listOf(0, 5, 10, 15, 30, 60)
            val labels = options.map { if (it == 0) "Off" else "$it minutes before" }
            val sp = ui.spinner(labels, options.indexOf(prefs.reminderMin).coerceAtLeast(0))
            ui.form("Class reminders", ui.column().apply { addView(sp) }) {
                prefs.reminderMin = options[sp.selectedItemPosition]; Alarms.reschedule(this); refresh(); null
            }
        }.apply {
            addView(ui.title("Class reminders"))
            addView(ui.small(if (prefs.reminderMin == 0) "Off" else "${prefs.reminderMin} minutes before each class"))
        })

        add(ui.card {
            val labels = listOf("Off", "Vibrate during lectures", "Silent during lectures")
            val sp = ui.spinner(labels, prefs.autoSilentMode)
            ui.form("Auto-silent", ui.column().apply {
                addView(sp)
                addView(ui.small("Switches your ringer when a timetable class starts and back to normal when it ends. \"Silent\" needs Do Not Disturb access; without it the app uses vibrate."))
            }) {
                prefs.autoSilentMode = sp.selectedItemPosition; Alarms.reschedule(this); refresh(); null
            }
        }.apply {
            addView(ui.title("Auto-silent in lectures"))
            addView(ui.small(listOf("Off", "Vibrate", "Silent")[prefs.autoSilentMode.coerceIn(0, 2)]))
        })

        add(ui.section("Permissions"))
        status(
            "Notifications", Notifier.canPost(this),
            "Needed for lecture, exam and timer alerts.",
        ) { openAppNotificationSettings() }
        status(
            "Alarms & reminders", Alarms.canExact(this),
            "Lets reminders arrive on time. Without it Android may delay them up to ~10 minutes.",
        ) {
            if (Build.VERSION.SDK_INT >= 31) tryStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        if (prefs.autoSilentMode == 2) {
            status(
                "Do Not Disturb access", Ringer.hasDndAccess(this),
                "Needed only for full silent mode in lectures.",
            ) { tryStart(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }

        add(ui.section("Grading"))
        val b = prefs.bands
        add(ui.card { editBands() }.apply {
            addView(ui.title("Grade bands (5-point)"))
            addView(ui.small("A ${b.a}+ · B ${b.b}–${b.a - 1} · C ${b.c}–${b.b - 1} · D ${b.d}–${b.c - 1} · E ${b.e}–${b.d - 1} · F below ${b.e}"))
            addView(ui.text("Tap to change", 13f, R.color.accent, true))
        })

        add(ui.section("About"))
        add(ui.card().apply {
            addView(ui.title("Campus Survival Kit 1.0"))
            addView(ui.small("Private by design: no account and no internet permission. Your timetable, grades, money and cards are stored only on this phone and are not included in cloud backups."))
            addView(ui.small("Samsung tip: if reminders stop, make sure the app isn't in Settings → Battery → Background usage limits → Deep sleeping apps."))
        })
    }

    override fun onResume() {
        super.onResume()
        Alarms.reschedule(this)
    }

    private fun status(name: String, ok: Boolean, help: String, fix: () -> Unit) {
        add(ui.card { if (!ok) fix() }.apply {
            addView(ui.text((if (ok) "✓ " else "✗ ") + name, 15f, if (ok) R.color.success else R.color.danger, true))
            addView(ui.small(if (ok) help else "$help Tap to allow."))
        })
    }

    private fun tryStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            tryStart2(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun tryStart2(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Open Settings → Apps → Campus Kit manually.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppNotificationSettings() {
        tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun editBands() {
        val prefs = Prefs(this)
        val b = prefs.bands
        val fields = listOf("A" to b.a, "B" to b.b, "C" to b.c, "D" to b.d, "E" to b.e).map { (g, v) ->
            g to ui.numberInput("Minimum score for $g", v.toString())
        }
        val form = ui.column()
        for ((g, f) in fields) {
            form.addView(ui.label("Minimum score for $g")); form.addView(f)
        }
        form.addView(ui.flatButton("Reset to default (70/60/50/45/40)") {
            val d = Bands()
            listOf(d.a, d.b, d.c, d.d, d.e).forEachIndexed { i, v -> fields[i].second.setText(v.toString()) }
        })
        ui.form("Grade bands", form) {
            val v = fields.map { it.second.text.toString().toIntOrNull() }
            if (v.any { it == null }) return@form "Fill in every band."
            val nb = Bands(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!)
            if (!nb.isValid()) "Each band must be lower than the one above it (A > B > C > D > E), within 0–100." else {
                prefs.bands = nb; refresh(); null
            }
        }
    }
}
