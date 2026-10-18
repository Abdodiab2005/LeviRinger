package net.leviro.ringer.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies a user-picked audio file into the app's private storage so we never depend
 * on a content URI that can be revoked or whose backing file gets deleted/moved.
 */
object RingtoneStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "ringtones").apply { if (!exists()) mkdirs() }

    /** Copies [source] into internal storage; returns the new absolute path, or null on failure. */
    fun copyIn(context: Context, source: Uri): String? {
        return try {
            val ext = guessExtension(context, source)
            val dest = File(dir(context), "rt_${UUID.randomUUID()}$ext")
            context.contentResolver.openInputStream(source).use { input ->
                if (input == null) return null
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes a previously copied file if it lives in our ringtone dir. */
    fun deleteIfOwned(context: Context, path: String?) {
        if (path == null) return
        val f = File(path)
        if (f.parentFile == dir(context) && f.exists()) f.delete()
    }

    /** System default alarm sound, used when no custom ringtone is set. */
    fun defaultAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun titleFor(context: Context, uri: Uri): String? =
        try { RingtoneManager.getRingtone(context, uri)?.getTitle(context) } catch (e: Exception) { null }

    private fun guessExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri) ?: return ".audio"
        return when {
            type.contains("mpeg") || type.contains("mp3") -> ".mp3"
            type.contains("ogg") -> ".ogg"
            type.contains("wav") -> ".wav"
            type.contains("aac") || type.contains("mp4") || type.contains("m4a") -> ".m4a"
            else -> ".audio"
        }
    }
}
