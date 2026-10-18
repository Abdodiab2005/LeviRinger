package net.leviro.ringer.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.leviro.ringer.util.LocaleHelper

/**
 * All screens extend this so the AR/EN choice is applied consistently and, since targetSdk
 * forces edge-to-edge, so toolbars and buttons never sit under the system bars: system-bar,
 * display-cutout and IME insets are applied as padding to the content root once, here.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Content with scrollable views resizes instead of panning under the keyboard.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val pad = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(pad.left, pad.top, pad.right, pad.bottom)
            insets
        }
    }
}
