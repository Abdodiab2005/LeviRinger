package net.leviro.ringer.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Wraps a Context so all Activities can be forced into the user-selected language. */
object LocaleHelper {

    fun wrap(context: Context): Context {
        val lang = Prefs(context).language
        if (lang.isBlank()) return context // follow system
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
