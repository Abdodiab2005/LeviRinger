package net.leviro.ringer.util

import android.content.Context

/** Simple global settings store (defaults + language). Whitelist lives in SQLite. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

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

    companion object {
        private const val NAME = "leviro_prefs"
        private const val KEY_DEFAULT_RINGTONE = "default_ringtone"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_LANG = "language"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
