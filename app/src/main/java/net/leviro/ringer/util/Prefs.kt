package net.leviro.ringer.util

import android.content.Context

/** Simple global settings store (defaults + language). Whitelist lives in SQLite. */
class Prefs(context: Context) {

    // applicationContext is still null while Application.attachBaseContext runs (LocaleHelper).
    private val sp = (context.applicationContext ?: context).getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Absolute path to the global default alarm sound, or null = system default alarm. */
    var defaultRingtonePath: String?
        get() = sp.getString(KEY_DEFAULT_RINGTONE, null)
        set(value) = sp.edit().putString(KEY_DEFAULT_RINGTONE, value).apply()

    /** Master switch for the whole feature. */
    var masterEnabled: Boolean
        get() = sp.getBoolean(KEY_MASTER, true)
        set(value) = sp.edit().putBoolean(KEY_MASTER, value).apply()

    /** "" = follow system, "en" or "ar" = forced app language. */
    var language: String
        get() = sp.getString(KEY_LANG, "") ?: ""
        set(value) = sp.edit().putString(KEY_LANG, value).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** Vibrate without sound for every caller (a contact can also opt in individually). */
    var defaultVibrateOnly: Boolean
        get() = sp.getBoolean(KEY_DEFAULT_VIBRATE, false)
        set(value) = sp.edit().putBoolean(KEY_DEFAULT_VIBRATE, value).apply()

    /** When on, alerts only fire inside [scheduleStart]..[scheduleEnd] on [scheduleDays]. */
    var scheduleEnabled: Boolean
        get() = sp.getBoolean(KEY_SCHEDULE_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    /** "HH:mm". A start later than the end crosses midnight. */
    var scheduleStart: String
        get() = sp.getString(KEY_SCHEDULE_START, DEFAULT_SCHEDULE_START) ?: DEFAULT_SCHEDULE_START
        set(value) = sp.edit().putString(KEY_SCHEDULE_START, value).apply()

    var scheduleEnd: String
        get() = sp.getString(KEY_SCHEDULE_END, DEFAULT_SCHEDULE_END) ?: DEFAULT_SCHEDULE_END
        set(value) = sp.edit().putString(KEY_SCHEDULE_END, value).apply()

    /** Bit 0 = Monday ... bit 6 = Sunday. An overnight window belongs to the day it starts on. */
    var scheduleDays: Int
        get() = sp.getInt(KEY_SCHEDULE_DAYS, ALL_DAYS) and ALL_DAYS
        set(value) = sp.edit().putInt(KEY_SCHEDULE_DAYS, value and ALL_DAYS).apply()

    /** Playback gain of the alert sound, 0..100 percent of the alarm stream. 0 = silent. */
    var alertVolumePercent: Int
        get() = sp.getInt(KEY_ALERT_VOLUME, 100).coerceIn(0, 100)
        set(value) = sp.edit().putInt(KEY_ALERT_VOLUME, value.coerceIn(0, 100)).apply()

    /** Seconds to wait after the call starts ringing before the alert sounds, 0..30. */
    var alertDelaySeconds: Int
        get() = sp.getInt(KEY_ALERT_DELAY, 0).coerceIn(0, MAX_DELAY_SECONDS)
        set(value) = sp.edit().putInt(KEY_ALERT_DELAY, value.coerceIn(0, MAX_DELAY_SECONDS)).apply()

    /** Opt-in: raise the shared system alarm stream to max during an alert, restore it afterward. */
    var boostAlarmVolume: Boolean
        get() = sp.getBoolean(KEY_BOOST_ALARM, false)
        set(value) = sp.edit().putBoolean(KEY_BOOST_ALARM, value).apply()

    /** Also alert when the phone is in normal ring mode (off by default to avoid a double ring). */
    var alertWhenRinging: Boolean
        get() = sp.getBoolean(KEY_ALERT_WHEN_RINGING, false)
        set(value) = sp.edit().putBoolean(KEY_ALERT_WHEN_RINGING, value).apply()

    /** Show the caller card activity (full-screen intent) in addition to the notification. */
    var showAlertCard: Boolean
        get() = sp.getBoolean(KEY_SHOW_ALERT_CARD, true)
        set(value) = sp.edit().putBoolean(KEY_SHOW_ALERT_CARD, value).apply()

    companion object {
        const val ALL_DAYS = 0x7F
        const val MAX_DELAY_SECONDS = 30
        const val DEFAULT_SCHEDULE_START = "22:00"
        const val DEFAULT_SCHEDULE_END = "07:00"

        private const val NAME = "leviro_prefs"
        private const val KEY_DEFAULT_RINGTONE = "default_ringtone"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_LANG = "language"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_DEFAULT_VIBRATE = "default_vibrate"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START = "schedule_start"
        private const val KEY_SCHEDULE_END = "schedule_end"
        private const val KEY_SCHEDULE_DAYS = "schedule_days"
        private const val KEY_ALERT_VOLUME = "alert_volume"
        private const val KEY_ALERT_DELAY = "alert_delay_seconds"
        private const val KEY_BOOST_ALARM = "boost_alarm_volume"
        private const val KEY_ALERT_WHEN_RINGING = "alert_when_ringing"
        private const val KEY_SHOW_ALERT_CARD = "show_alert_card"
    }
}
