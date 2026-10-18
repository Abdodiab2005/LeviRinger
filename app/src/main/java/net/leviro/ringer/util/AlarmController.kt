package net.leviro.ringer.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import net.leviro.ringer.R
import net.leviro.ringer.alert.AlertActivity
import net.leviro.ringer.db.Vip
import net.leviro.ringer.service.AlarmService

/**
 * Single entry point for triggering and stopping the VIP alarm.
 *
 * Why both a full-screen notification AND a foreground service:
 *  - The full-screen intent is the system-blessed way to launch an Activity over the
 *    lock screen from the background (this is exactly how incoming-call screens work).
 *  - Starting a foreground service directly from a background broadcast is restricted on
 *    Android 12+. So we try it, and if blocked, the launched AlertActivity starts the
 *    service itself (an Activity in the foreground may always start an FGS).
 */
object AlarmController {

    @Volatile
    var active: Boolean = false
        private set

    fun trigger(context: Context, vip: Vip) =
        trigger(context, vip.name, vip.number, vip.note, vip.ringtonePath)

    fun trigger(context: Context, name: String, number: String, note: String?, ringtonePath: String?) {
        active = true
        Notifications.createChannels(context)

        val alertIntent = Intent(context, AlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Const.EXTRA_NAME, name)
            putExtra(Const.EXTRA_NUMBER, number)
            putExtra(Const.EXTRA_NOTE, note)
            putExtra(Const.EXTRA_RINGTONE, ringtonePath)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreen = PendingIntent.getActivity(context, 0, alertIntent, flags)

        val notif = NotificationCompat.Builder(context, Const.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_ring)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(if (name.isNotBlank()) name else number)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Const.NOTIF_ALERT, notif)

        // Best-effort direct FGS start. If the OS refuses (background-start limit),
        // AlertActivity will start it once it is visible.
        try {
            val svc = Intent(context, AlarmService::class.java).apply {
                putExtra(Const.EXTRA_NAME, name)
                putExtra(Const.EXTRA_NUMBER, number)
                putExtra(Const.EXTRA_RINGTONE, ringtonePath)
            }
            ContextCompat.startForegroundService(context, svc)
        } catch (e: Exception) {
            // Expected on some OS versions when started from a background broadcast.
        }
    }

    /** Stops the alarm everywhere: cancels notification, stops service, tells the alert to finish. */
    fun stop(context: Context) {
        active = false
        context.stopService(Intent(context, AlarmService::class.java))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(Const.NOTIF_ALERT)
        context.sendBroadcast(Intent(Const.ACTION_STOP_ALERT).setPackage(context.packageName))
    }
}
