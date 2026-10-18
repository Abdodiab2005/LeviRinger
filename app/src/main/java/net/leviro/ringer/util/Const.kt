package net.leviro.ringer.util

object Const {
    // Broadcast actions (internal)
    const val ACTION_STOP_ALERT = "net.leviro.ringer.STOP_ALERT"

    const val ACTION_ARM = "net.leviro.ringer.ARM"
    const val ACTION_USER_STOP = "net.leviro.ringer.USER_STOP"
    const val EXTRA_GENERATION = "extra_generation"
    const val EXTRA_VIP_ID = "extra_vip_id"
    const val EXTRA_VIBRATE_ONLY = "extra_vibrate_only"
    const val EXTRA_DELAY_SECONDS = "extra_delay_seconds"

    // Intent extras passed to the alert / service
    const val EXTRA_NAME = "extra_name"
    const val EXTRA_NUMBER = "extra_number"
    const val EXTRA_NOTE = "extra_note"
    const val EXTRA_RINGTONE = "extra_ringtone" // path or null

    // Notification channels
    const val CHANNEL_ALERT = "leviro_vip_alert"     // high importance, full-screen intent
    const val CHANNEL_SERVICE = "leviro_fgs"         // low importance, foreground-service status

    // Notification ids
    const val NOTIF_ALERT = 1001
    const val NOTIF_SERVICE = 1002
}
