package net.leviro.ringer.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import net.leviro.ringer.util.LocaleHelper

/** All screens extend this so the AR/EN choice is applied consistently. */
abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
