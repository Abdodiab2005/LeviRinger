package net.leviro.ringer.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import net.leviro.ringer.R
import net.leviro.ringer.alert.AlertActivity
import net.leviro.ringer.alert.StopAlertReceiver

/** Silent notification surfaces; alarm audio belongs to the service. */
object Notifications {
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Const.CHANNEL_ALERT,
            context.getString(R.string.channel_alert_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alert_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        manager.createNotificationChannel(NotificationChannel(Const.CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_service_desc)
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun canUseFullScreenIntent(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    internal fun build(context: Context, call: AlarmController.CallAlert, waiting: Boolean,
                       fullScreen: Boolean, channel: String): Notification {
        createChannels(context)
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, AlertActivity::class.java)
                .setData(Uri.parse("leviro://alert/${call.generation}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Const.EXTRA_GENERATION, call.generation)
                .putExtra(Const.EXTRA_NAME, call.name)
                .putExtra(Const.EXTRA_NUMBER, call.number)
                .putExtra(Const.EXTRA_NOTE, call.note),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(context, 0,
            Intent(context, StopAlertReceiver::class.java).setAction(Const.ACTION_USER_STOP)
                .setData(Uri.parse("leviro://stop/${call.generation}"))
                .putExtra(Const.EXTRA_GENERATION, call.generation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_ring)
            .setContentTitle(call.name.ifBlank { call.number.ifBlank { context.getString(R.string.alert_title) } })
            .setContentText(context.getString(if (waiting) R.string.engine_waiting else R.string.engine_alerting,
                call.delaySeconds))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (channel == Const.CHANNEL_ALERT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_ring, context.getString(R.string.engine_stop_alert), stop)
            .apply { if (fullScreen) setFullScreenIntent(open, true) }
            .build()
    }

    internal fun postAlert(context: Context, call: AlarmController.CallAlert, waiting: Boolean, fullScreen: Boolean) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(Const.NOTIF_ALERT,
                build(context, call, waiting, fullScreen, Const.CHANNEL_ALERT))
        } catch (e: SecurityException) {
            Log.w("LeviroRinger", "generation=${call.generation} notification denied")
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(Const.NOTIF_ALERT)
            cancel(Const.NOTIF_SERVICE)
        }
    }
}
