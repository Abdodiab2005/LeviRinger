package net.leviro.ringer.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivityAlertBinding
import net.leviro.ringer.service.AlarmService
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Const

/**
 * The screen the user sees when a VIP rings. Shows over the lock screen, displays the
 * caller card, and offers three ways to silence the alarm: the Stop button, either volume
 * key, or simply the call ending (handled via the STOP_ALERT broadcast).
 *
 * Note: this is NOT a BaseActivity — the alert keeps its own theme and we never want the
 * locale wrap to interfere with showing over the keyguard.
 */
class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Const.ACTION_STOP_ALERT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra(Const.EXTRA_NAME).orEmpty()
        val number = intent.getStringExtra(Const.EXTRA_NUMBER).orEmpty()
        val note = intent.getStringExtra(Const.EXTRA_NOTE)

        binding.txtName.text = if (name.isNotBlank()) name else getString(R.string.unknown_caller)
        binding.txtNumber.text = number
        if (!note.isNullOrBlank()) {
            binding.txtNote.text = note
            binding.txtNote.visibility = android.view.View.VISIBLE
        }
        binding.txtHint.text = getString(R.string.alert_hint)

        binding.btnStop.setOnClickListener { stopEverything() }

        // Ensure the sound is running even if the OS blocked the background FGS start:
        // an Activity in the foreground may always start a foreground service.
        ContextCompat.startForegroundService(
            this,
            Intent(this, AlarmService::class.java)
                .putExtra(Const.EXTRA_NAME, name)
                .putExtra(Const.EXTRA_NUMBER, number)
                .putExtra(Const.EXTRA_RINGTONE, intent.getStringExtra(Const.EXTRA_RINGTONE))
        )
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, stopReceiver, IntentFilter(Const.ACTION_STOP_ALERT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(stopReceiver) } catch (e: Exception) {}
    }

    /** Either volume key silences the alarm — a familiar, glanceable gesture. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            stopEverything()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stopEverything() {
        AlarmController.stop(this)
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
