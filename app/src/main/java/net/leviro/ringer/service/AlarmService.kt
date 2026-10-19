package net.leviro.ringer.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ServiceCompat
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Const
import net.leviro.ringer.util.Notifications
import net.leviro.ringer.util.RingtoneStore
import java.io.File

/** Holds playback resources only while the controller owns a live generation. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedAlarmVolume: Int? = null
    private var generation: Long? = null
    private var modernListener: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != Const.ACTION_ARM ||
            !AlarmController.attach(this, intent.getLongExtra(Const.EXTRA_GENERATION, -1))) {
            if (generation == null) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    internal fun arm(call: AlarmController.CallAlert, remainingDelay: Long) {
        releaseAlert()
        generation = call.generation
        val notification = Notifications.build(this, call, waiting = true, fullScreen = false,
            channel = Const.CHANNEL_SERVICE)
        ServiceCompat.startForeground(this, Const.NOTIF_SERVICE, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LeviRinger:alarm").apply {
                setReferenceCounted(false)
                acquire(remainingDelay + if (call.test) 30_000L else 180_000L)
            }
        if (!call.test) registerCallListener(call.generation)
    }

    internal fun alert(call: AlarmController.CallAlert) {
        if (!AlarmController.playbackAllowed(call.generation)) return
        Log.i(TAG, "generation=${call.generation} alert start gain=${call.volumePercent}")
        ServiceCompat.startForeground(this, Const.NOTIF_ALERT,
            Notifications.build(this, call, waiting = false,
                fullScreen = call.showCard && Notifications.canUseFullScreenIntent(this),
                channel = Const.CHANNEL_ALERT),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        if (!call.vibrateOnly && call.volumePercent > 0) {
            if (call.boostAlarm) boostVolume()
            play(call, call.volumePercent / 100f, fallback = false)
        }
    }

    private fun play(call: AlarmController.CallAlert, gain: Float, fallback: Boolean) {
        if (!AlarmController.playbackAllowed(call.generation)) return
        val candidate = MediaPlayer()
        player = candidate
        fun failed() {
            if (player !== candidate) return
            player = null
            candidate.release()
            Log.w(TAG, "generation=${call.generation} playback error fallback=$fallback")
            if (!fallback && AlarmController.playbackAllowed(call.generation)) play(call, gain, fallback = true)
        }
        try {
            candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            candidate.setVolume(gain, gain)
            candidate.isLooping = true
            candidate.setOnPreparedListener {
                if (player === it && AlarmController.playbackAllowed(call.generation)) {
                    try { it.start() } catch (e: IllegalStateException) { failed() }
                }
            }
            candidate.setOnErrorListener { _, _, _ -> failed(); true }
            val path = call.ringtonePath
            when {
                fallback -> candidate.setDataSource(this, Settings.System.DEFAULT_ALARM_ALERT_URI)
                path != null && File(path).isFile -> candidate.setDataSource(path)
                else -> candidate.setDataSource(this, RingtoneStore.defaultAlarmUri())
            }
            candidate.prepareAsync()
        } catch (e: Exception) {
            failed()
        }
    }

    private fun registerCallListener(expected: Long) {
        val telephony = getSystemService(TelephonyManager::class.java)
        fun changed(state: Int) {
            if (generation != expected) return
            val value = when (state) {
                TelephonyManager.CALL_STATE_RINGING -> TelephonyManager.EXTRA_STATE_RINGING
                TelephonyManager.CALL_STATE_OFFHOOK -> TelephonyManager.EXTRA_STATE_OFFHOOK
                else -> TelephonyManager.EXTRA_STATE_IDLE
            }
            AlarmController.onCallStateChanged(this, value)
        }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val listener = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = changed(state)
                }
                modernListener = listener
                telephony.registerTelephonyCallback(mainExecutor, listener)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = changed(state)
                }
                legacyListener = listener
                @Suppress("DEPRECATION")
                telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "generation=$expected phone-state listener denied")
        }
    }

    private fun boostVolume() {
        val audio = getSystemService(AudioManager::class.java)
        try {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "generation=$generation volume boost denied")
        }
    }

    /** Synchronous cleanup prevents an old service exit from touching a newer player's resources. */
    internal fun releaseAlert() {
        generation = null
        val old = player
        player = null
        old?.setOnPreparedListener(null)
        old?.setOnErrorListener(null)
        old?.release()
        vibrator?.cancel()
        vibrator = null
        savedAlarmVolume?.let {
            try { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
            catch (e: SecurityException) { Log.w(TAG, "alarm volume restore denied") }
        }
        savedAlarmVolume = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        val telephony = getSystemService(TelephonyManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31) modernListener?.let { telephony.unregisterTelephonyCallback(it) }
            @Suppress("DEPRECATION")
            legacyListener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (e: SecurityException) { Log.w(TAG, "phone-state listener removal denied") }
        modernListener = null
        legacyListener = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        AlarmController.serviceDestroyed(this)
        releaseAlert()
        super.onDestroy()
    }

    private companion object { const val TAG = "LeviroRinger" }
}
