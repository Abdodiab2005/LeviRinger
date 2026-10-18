package net.leviro.ringer.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivityAlertBinding
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Const

/** A small non-modal card leaves the system's Answer and Decline controls usable. */
class AlertActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlertBinding
    private var generation = -1L
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Const.ACTION_STOP_ALERT &&
                intent.getLongExtra(Const.EXTRA_GENERATION, -1) == generation) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setVolumeControlStream(AudioManager.STREAM_ALARM)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setGravity(Gravity.TOP)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        val left = binding.root.paddingLeft
        val top = binding.root.paddingTop
        val right = binding.root.paddingRight
        val bottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.btnStop.setOnClickListener { stopAlert() }
        showCaller()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showCaller()
    }

    private fun showCaller() {
        generation = intent.getLongExtra(Const.EXTRA_GENERATION, -1)
        val name = intent.getStringExtra(Const.EXTRA_NAME).orEmpty()
        val number = intent.getStringExtra(Const.EXTRA_NUMBER).orEmpty()
        binding.txtName.text = name.ifBlank { number.ifBlank { getString(R.string.unknown_caller) } }
        binding.txtNumber.text = number
        binding.txtNumber.visibility = if (number.isBlank() || name.isBlank()) View.GONE else View.VISIBLE
        val note = intent.getStringExtra(Const.EXTRA_NOTE)
        binding.txtNote.text = note
        binding.txtNote.visibility = if (note.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, stopReceiver, IntentFilter(Const.ACTION_STOP_ALERT),
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!AlarmController.resumeFromCard(this, generation)) finish()
    }

    override fun onStop() {
        unregisterReceiver(stopReceiver)
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE)) {
            if (event.action == KeyEvent.ACTION_DOWN) stopAlert()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun stopAlert() {
        AlarmController.stopGeneration(this, generation)
        finish()
    }
}
