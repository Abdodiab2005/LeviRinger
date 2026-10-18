package net.leviro.ringer

import android.app.Application
import android.content.Context
import net.leviro.ringer.util.LocaleHelper
import net.leviro.ringer.util.Notifications

class RingerApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
