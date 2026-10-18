package net.leviro.ringer.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * Loops a sound through a MediaPlayer on the alarm stream so the user can audition the
 * alert volume from Settings without waiting for a real call. The gain can be adjusted
 * live while it plays.
 */
class SoundPreview(private val context: Context) {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean
        get() = try {
            player?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    /** Starts looping [uri]; replaces any playback already running. */
    fun start(uri: Uri, gainPercent: Int) {
        stop()
        try {
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setDataSource(context, uri)
            p.isLooping = true
            p.setVolume(gain(gainPercent), gain(gainPercent))
            p.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            stop()
        }
    }

    /** Adjusts the loudness while playing, 0..100 percent. */
    fun setGain(gainPercent: Int) {
        val g = gain(gainPercent)
        player?.setVolume(g, g)
    }

    fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (e: Exception) {
            }
            try {
                it.release()
            } catch (e: Exception) {
            }
        }
        player = null
    }

    private fun gain(percent: Int) = percent.coerceIn(0, 100) / 100f
}
