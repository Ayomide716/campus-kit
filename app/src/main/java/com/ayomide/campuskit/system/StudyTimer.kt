package com.ayomide.campuskit.system

import android.content.Context
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Prefs

/**
 * Pomodoro timer without a background service: the end time is stored, an
 * alarm fires at the end, and a notification shows a live countdown.
 */
object StudyTimer {

    fun isRunning(ctx: Context) = Prefs(ctx).timerEnd > 0

    fun start(ctx: Context, isFocus: Boolean, minutes: Int) {
        val prefs = Prefs(ctx)
        val end = System.currentTimeMillis() + minutes * 60_000L
        prefs.timerEnd = end
        prefs.timerIsFocus = isFocus
        prefs.timerMinutes = minutes
        Notifier.cancel(ctx, Notifier.ID_TIMER_DONE)
        Alarms.scheduleTimer(ctx, end)
        Notifier.showTimerRunning(ctx, end, isFocus)
    }

    fun stop(ctx: Context) {
        Prefs(ctx).timerEnd = 0
        Alarms.cancelTimer(ctx)
        Notifier.cancel(ctx, Notifier.ID_TIMER_RUNNING)
    }

    /** Idempotent: records a finished focus session once and notifies. */
    @Synchronized
    fun completeIfDue(ctx: Context): Boolean {
        val prefs = Prefs(ctx)
        val end = prefs.timerEnd
        if (end == 0L || System.currentTimeMillis() < end - 2_000L) return false
        prefs.timerEnd = 0
        val focus = prefs.timerIsFocus
        val minutes = prefs.timerMinutes
        if (focus) Db.get(ctx).addSession(end, minutes)
        Notifier.showTimerDone(ctx, focus, minutes)
        return true
    }

    /** After reboot: finish an expired timer or re-arm a running one. */
    fun restore(ctx: Context) {
        if (completeIfDue(ctx)) return
        val prefs = Prefs(ctx)
        if (prefs.timerEnd > 0) {
            Alarms.scheduleTimer(ctx, prefs.timerEnd)
            Notifier.showTimerRunning(ctx, prefs.timerEnd, prefs.timerIsFocus)
        }
    }
}
