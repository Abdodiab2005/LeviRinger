package net.leviro.ringer.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import net.leviro.ringer.R
import net.leviro.ringer.alert.AlertActivity
import net.leviro.ringer.util.Const
import net.leviro.ringer.util.Notifications
import net.leviro.ringer.util.RingtoneStore
import java.io.File

/**
 * Foreground service that actually makes the noise. Audio plays on the ALARM stream with
 * USAGE_ALARM attributes — this is what lets it sound through silent, vibrate, and (by
 * default) Do Not Disturb, without us changing any of the user's system settings.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedAlarmVolume: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.createChannels(this)
        startInForeground(intent)
        acquireWakeLock()
        maximizeAlarmVolume()
        startVibration()
        startPlayback(intent?.getStringExtra(Const.EXTRA_RINGTONE))
        return START_NOT_STICKY // do not auto-restart after the call is over
    }

    private fun startInForeground(intent: Intent?) {
        val name = intent?.getStringExtra(Const.EXTRA_NAME).orEmpty()
        val number = intent?.getStringExtra(Const.EXTRA_NUMBER).orEmpty()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Const.EXTRA_NAME, name)
                .putExtra(Const.EXTRA_NUMBER, number)
                .putExtra(Const.EXTRA_RINGTONE, intent?.getStringExtra(Const.EXTRA_RINGTONE)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, Const.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_ring)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(if (name.isNotBlank()) name else number)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, Const.NOTIF_SERVICE, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Const.NOTIF_SERVICE, notif)
        }
    }

    private fun startPlayback(ringtonePath: String?) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                if (ringtonePath != null && File(ringtonePath).exists()) {
                    setDataSource(ringtonePath)
                } else {
                    setDataSource(this@AlarmService, RingtoneStore.defaultAlarmUri())
                }
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (e: Exception) {
            // If the custom file is unreadable, fall back to the system alarm.
            try {
                player?.release()
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                    )
                    setDataSource(this@AlarmService, Uri.parse(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString()))
                    isLooping = true
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    private fun maximizeAlarmVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (e: Exception) {
        }
    }

    private fun restoreAlarmVolume() {
        if (savedAlarmVolume < 0) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
        } catch (e: Exception) {
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LeviRinger:alarm").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 1000L) // safety timeout: never longer than a ring should last
        }
    }

    override fun onDestroy() {
        try { player?.stop() } catch (e: Exception) {}
        player?.release()
        player = null
        vibrator?.cancel()
        restoreAlarmVolume()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }
}
