package com.ayomide.campuskit.system

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.ayomide.campuskit.data.Prefs

/**
 * Auto-silent during lectures. Only switches from Normal, and only restores if
 * the phone is still in the mode we set, so a manual change by the user wins.
 */
object Ringer {

    fun hasDndAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun reconcile(ctx: Context, classOngoing: Boolean) {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            if (classOngoing && prefs.autoSilentMode != 0) {
                if (prefs.silencedByUs || am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
                val target = if (prefs.autoSilentMode == 2 && hasDndAccess(ctx)) {
                    AudioManager.RINGER_MODE_SILENT
                } else {
                    AudioManager.RINGER_MODE_VIBRATE
                }
                am.ringerMode = target
                prefs.ourRingerMode = target
                prefs.silencedByUs = true
            } else if (prefs.silencedByUs) {
                if (am.ringerMode == prefs.ourRingerMode) am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                prefs.silencedByUs = false
            }
        } catch (e: SecurityException) {
            Log.w("Ringer", "Ringer change not allowed", e)
            prefs.silencedByUs = false
        }
    }
}
