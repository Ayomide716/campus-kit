package com.ayomide.campuskit.data

import android.content.Context
import android.content.SharedPreferences
import com.ayomide.campuskit.core.Bands

class Prefs(context: Context) {

    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("campus_prefs", Context.MODE_PRIVATE)

    /** Minutes before a class to remind; 0 = off. */
    var reminderMin: Int
        get() = p.getInt("reminder_min", 10)
        set(v) = p.edit().putInt("reminder_min", v).apply()

    /** 0 = off, 1 = vibrate, 2 = silent (needs Do Not Disturb access). */
    var autoSilentMode: Int
        get() = p.getInt("auto_silent", 0)
        set(v) = p.edit().putInt("auto_silent", v).apply()

    var silencedByUs: Boolean
        get() = p.getBoolean("silenced_by_us", false)
        set(v) = p.edit().putBoolean("silenced_by_us", v).apply()

    var ourRingerMode: Int
        get() = p.getInt("our_ringer", -1)
        set(v) = p.edit().putInt("our_ringer", v).apply()

    var bands: Bands
        get() = Bands(
            p.getInt("band_a", 70), p.getInt("band_b", 60), p.getInt("band_c", 50),
            p.getInt("band_d", 45), p.getInt("band_e", 40),
        ).takeIf { it.isValid() } ?: Bands()
        set(v) = p.edit().putInt("band_a", v.a).putInt("band_b", v.b).putInt("band_c", v.c)
            .putInt("band_d", v.d).putInt("band_e", v.e).apply()

    /** Monthly allowance in kobo; 0 = not set. */
    var allowanceKobo: Long
        get() = p.getLong("allowance", 0)
        set(v) = p.edit().putLong("allowance", v).apply()

    var focusMin: Int
        get() = p.getInt("focus_min", 25)
        set(v) = p.edit().putInt("focus_min", v).apply()

    var breakMin: Int
        get() = p.getInt("break_min", 5)
        set(v) = p.edit().putInt("break_min", v).apply()

    /** Running timer end time; 0 = no timer. */
    var timerEnd: Long
        get() = p.getLong("timer_end", 0)
        set(v) = p.edit().putLong("timer_end", v).apply()

    var timerIsFocus: Boolean
        get() = p.getBoolean("timer_focus", true)
        set(v) = p.edit().putBoolean("timer_focus", v).apply()

    var timerMinutes: Int
        get() = p.getInt("timer_minutes", 25)
        set(v) = p.edit().putInt("timer_minutes", v).apply()

    /** Scheduler bookkeeping: events up to this time have already been shown. */
    var lastProcessed: Long
        get() = p.getLong("last_processed", 0)
        set(v) = p.edit().putLong("last_processed", v).apply()

    var askedNotifications: Boolean
        get() = p.getBoolean("asked_notif", false)
        set(v) = p.edit().putBoolean("asked_notif", v).apply()
}
