package net.leviro.ringer.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import net.leviro.ringer.rules.DeviceAudioState
import net.leviro.ringer.rules.RingerState

/** Reads device settings without changing the phone's ringer or interruption policy. */
object DeviceAudio {
    fun read(context: Context): DeviceAudioState {
        val audio = context.getSystemService(AudioManager::class.java)
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return DeviceAudioState(
            when (audio.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> RingerState.VIBRATE
                AudioManager.RINGER_MODE_SILENT -> RingerState.SILENT
                else -> RingerState.NORMAL
            },
            filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            audio.getStreamVolume(AudioManager.STREAM_RING) == 0
        )
    }
}
