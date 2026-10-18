package net.leviro.ringer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.leviro.ringer.util.Notifications

/**
 * The PHONE_STATE receiver and the call-screening service are registered in the manifest,
 * so detection survives a reboot on its own. This receiver simply wakes the app process
 * once after boot (helps some OEMs keep us off the kill list) and ensures channels exist.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> Notifications.createChannels(context)
        }
    }
}
