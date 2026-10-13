package com.ayomide.campuskit.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ayomide.campuskit.core.EventType
import com.ayomide.campuskit.core.Schedule
import com.ayomide.campuskit.data.Db
import com.ayomide.campuskit.data.Prefs
import java.time.ZoneId

/**
 * Keeps exactly one pending alarm: the next reminder / class boundary. When it
 * fires, due events are shown and the following one is scheduled. Rebuilt on
 * app open, data changes, reboot, time/zone change and app update.
 */
object Alarms {

    private const val TAG = "Alarms"
    private const val REQ_EVENTS = 1
    private const val REQ_TIMER = 2
    private const val LOOKAHEAD_MS = 9L * 86_400_000L
    /** Don't show reminders older than this (e.g. phone was off). */
    private const val STALE_MS = 15L * 60_000L

    const val ACTION_EVENTS = "com.ayomide.campuskit.EVENTS"
    const val ACTION_TIMER = "com.ayomide.campuskit.TIMER"

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun pending(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun set(ctx: Context, atMillis: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (canExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                return
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using inexact", e)
        }
        // Without exact-alarm access Android may deliver up to ~10 minutes late.
        am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 10 * 60_000L, pi)
    }

    /** Process anything due, reconcile auto-silent, then schedule the next event. */
    fun run(ctx: Context, showDue: Boolean) {
        try {
            val db = Db.get(ctx)
            val prefs = Prefs(ctx)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val classes = db.classes()
            val exams = db.exams()
            val silence = prefs.autoSilentMode != 0

            val from = maxOf(prefs.lastProcessed, now - STALE_MS)
            val dueUntil = now + 30_000L
            if (showDue) {
                for (e in Schedule.events(classes, exams, from, dueUntil, zone, prefs.reminderMin, false)) {
                    if (e.type == EventType.CLASS_START || e.type == EventType.CLASS_END) continue
                    Notifier.showEvent(ctx, (e.atMillis / 1000).toInt() xor e.type.ordinal, e.title, e.text)
                }
            }
            prefs.lastProcessed = maxOf(prefs.lastProcessed, if (showDue) dueUntil else now)

            Ringer.reconcile(ctx, Schedule.isClassOngoing(classes, now, zone))

            val next = Schedule.events(
                classes, exams, prefs.lastProcessed, now + LOOKAHEAD_MS, zone, prefs.reminderMin, silence,
            ).firstOrNull()
            val pi = pending(ctx, ACTION_EVENTS, REQ_EVENTS)
            if (next == null) {
                ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
            } else {
                set(ctx, next.atMillis, pi)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Scheduling failed", t)
        }
    }

    /** Call after any timetable/exam/settings change. */
    fun reschedule(ctx: Context) = run(ctx, showDue = false)

    fun scheduleTimer(ctx: Context, atMillis: Long) = set(ctx, atMillis, pending(ctx, ACTION_TIMER, REQ_TIMER))

    fun cancelTimer(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, ACTION_TIMER, REQ_TIMER))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Alarms.ACTION_EVENTS -> Alarms.run(context, showDue = true)
            Alarms.ACTION_TIMER -> StudyTimer.completeIfDue(context)
        }
    }
}

/** Re-creates alarms after reboot, app update, clock/time-zone change or exact-alarm permission change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                // Clock moved: don't flood old reminders.
                Prefs(context).lastProcessed = System.currentTimeMillis()
            }
        }
        Alarms.reschedule(context)
        StudyTimer.restore(context)
    }
}
