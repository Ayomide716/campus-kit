package com.ayomide.campuskit.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ayomide.campuskit.R
import com.ayomide.campuskit.screens.MainActivity
import com.ayomide.campuskit.screens.StudyActivity

object Notifier {

    const val CH_REMINDERS = "reminders"
    const val CH_TIMER = "timer"
    const val ID_TIMER_RUNNING = 1
    const val ID_TIMER_DONE = 2
    private const val ID_EVENT_BASE = 1000

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Lecture & exam reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders before lectures and exams, and study timer alerts"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, "Study timer (running)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the countdown while a study timer runs"
                setShowBadge(false)
            }
        )
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    private fun openApp(ctx: Context, target: Class<*>): PendingIntent =
        PendingIntent.getActivity(
            ctx, target.name.hashCode(),
            Intent(ctx, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun showEvent(ctx: Context, key: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, MainActivity::class.java))
            .build()
        post(ctx, ID_EVENT_BASE + (key and 0xFFFF), n)
    }

    fun showTimerRunning(ctx: Context, endMillis: Long, isFocus: Boolean) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (isFocus) "Focus session" else "Break")
            .setContentText(if (isFocus) "Stay off your phone. You've got this." else "Relax, stretch, drink water.")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endMillis)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx, StudyActivity::class.java))
            .build()
        post(ctx, ID_TIMER_RUNNING, n)
    }

    fun showTimerDone(ctx: Context, isFocus: Boolean, minutes: Int) {
        cancel(ctx, ID_TIMER_RUNNING)
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (isFocus) "Focus done: $minutes min 🎉" else "Break over")
            .setContentText(if (isFocus) "Nice work! Take a short break." else "Ready for another focus session?")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, StudyActivity::class.java))
            .build()
        post(ctx, ID_TIMER_DONE, n)
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    private fun post(ctx: Context, id: Int, n: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (e: SecurityException) {
            // Notification permission revoked; nothing to do.
        }
    }
}
