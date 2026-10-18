package net.leviro.ringer.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivitySettingsBinding
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Prefs
import net.leviro.ringer.util.RingtoneStore
import java.io.File

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var preview: SoundPreview
    private val dayChips by lazy {
        listOf(
            binding.chipMon, binding.chipTue, binding.chipWed,
            binding.chipThu, binding.chipFri, binding.chipSat, binding.chipSun
        )
    }

    private val pickRingtone = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::onRingtonePicked) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = Prefs(this)
        preview = SoundPreview(this)
        binding.txtDefaultRingtone.text =
            if (prefs.defaultRingtonePath == null) getString(R.string.ringtone_default)
            else getString(R.string.ringtone_custom)

        binding.btnDefaultRingtone.setOnClickListener { pickRingtone.launch(arrayOf("audio/*")) }
        binding.btnResetRingtone.setOnClickListener {
            RingtoneStore.deleteIfOwned(this, prefs.defaultRingtonePath)
            prefs.defaultRingtonePath = null
            binding.txtDefaultRingtone.text = getString(R.string.ringtone_default)
        }

        // Alert volume: 0..100 percent, saved immediately and applied to a running preview.
        binding.sliderVolume.value = prefs.alertVolumePercent.toFloat()
        refreshVolumeLabel()
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.alertVolumePercent = value.toInt()
            refreshVolumeLabel()
            if (preview.isPlaying) preview.setGain(prefs.alertVolumePercent)
            AlarmController.onConfigChanged(this)
        }
        binding.btnPreview.setOnClickListener { togglePreview() }

        // Delay before the alert sounds, 0..30 seconds.
        binding.sliderDelay.value = prefs.alertDelaySeconds.toFloat()
        refreshDelayLabel()
        binding.sliderDelay.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.alertDelaySeconds = value.toInt()
            refreshDelayLabel()
            AlarmController.onConfigChanged(this)
        }

        bindSwitch(binding.swGlobalVibrate, { prefs.defaultVibrateOnly }, { prefs.defaultVibrateOnly = it })
        bindSwitch(binding.swRingAlso, { prefs.alertWhenRinging }, { prefs.alertWhenRinging = it })
        bindSwitch(binding.swShowCard, { prefs.showAlertCard }, { prefs.showAlertCard = it })
        bindSwitch(binding.swBoostVolume, { prefs.boostAlarmVolume }, { prefs.boostAlarmVolume = it })

        setupSchedule()

        // Language: System / English / Arabic
        when (prefs.language) {
            "en" -> binding.radioEn.isChecked = true
            "ar" -> binding.radioAr.isChecked = true
            else -> binding.radioSystem.isChecked = true
        }
        binding.radioGroupLang.setOnCheckedChangeListener { _, id ->
            prefs.language = when (id) {
                R.id.radioEn -> "en"
                R.id.radioAr -> "ar"
                else -> ""
            }
            // Recreate so the new locale takes effect immediately.
            recreate()
        }

        binding.btnSetup.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun bindSwitch(
        view: com.google.android.material.materialswitch.MaterialSwitch,
        get: () -> Boolean,
        set: (Boolean) -> Unit
    ) {
        view.isChecked = get()
        view.setOnCheckedChangeListener { _, checked ->
            set(checked)
            AlarmController.onConfigChanged(this)
        }
    }

    private fun refreshVolumeLabel() {
        binding.txtVolumeLabel.text = getString(R.string.volume_label_fmt, prefs.alertVolumePercent)
        binding.txtVolumeZeroHint.visibility =
            if (prefs.alertVolumePercent == 0) View.VISIBLE else View.GONE
    }

    private fun refreshDelayLabel() {
        binding.txtDelayLabel.text = getString(R.string.delay_label_fmt, prefs.alertDelaySeconds)
    }

    /** Plays or stops the looped default alarm sound through the alarm stream. */
    private fun togglePreview() {
        if (preview.isPlaying) {
            preview.stop()
            binding.btnPreview.setText(R.string.preview)
            refreshSystemAlarmVolume()
            return
        }
        val uri = prefs.defaultRingtonePath?.let { Uri.fromFile(File(it)) }
            ?: RingtoneStore.defaultAlarmUri()
        preview.start(uri, prefs.alertVolumePercent)
        binding.btnPreview.setText(R.string.preview_stop)
        refreshSystemAlarmVolume()
    }

    private fun refreshSystemAlarmVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val percent = if (max <= 0) 0 else current * 100 / max
        binding.txtAlarmVolume.text = getString(R.string.alarm_volume_percent, percent)
        binding.txtAlarmVolumeWarning.visibility =
            if (percent == 0) View.VISIBLE else View.GONE
    }

    private fun setupSchedule() {
        binding.swScheduleEnabled.isChecked = prefs.scheduleEnabled
        binding.layoutSchedule.visibility =
            if (prefs.scheduleEnabled) View.VISIBLE else View.GONE
        binding.swScheduleEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.scheduleEnabled = checked
            binding.layoutSchedule.visibility =
                if (checked) View.VISIBLE else View.GONE
            AlarmController.onConfigChanged(this)
        }

        refreshScheduleButtons()
        binding.btnScheduleStart.setOnClickListener { pickScheduleTime(isStart = true) }
        binding.btnScheduleEnd.setOnClickListener { pickScheduleTime(isStart = false) }

        dayChips.forEachIndexed { i, chip ->
            chip.isChecked = prefs.scheduleDays and (1 shl i) != 0
            chip.setOnCheckedChangeListener { button, checked ->
                var days = prefs.scheduleDays
                days = if (checked) days or (1 shl i) else days and (1 shl i).inv()
                if (days and Prefs.ALL_DAYS == 0) {
                    // Refuse to clear the last selected day.
                    button.isChecked = true
                } else {
                    prefs.scheduleDays = days
                    AlarmController.onConfigChanged(this)
                }
            }
        }
    }

    private fun refreshScheduleButtons() {
        binding.btnScheduleStart.text = prefs.scheduleStart
        binding.btnScheduleEnd.text = prefs.scheduleEnd
    }

    /** 24-hour picker seeded from the stored "HH:mm" value (22:00 start / 07:00 end defaults). */
    private fun pickScheduleTime(isStart: Boolean) {
        val value = if (isStart) prefs.scheduleStart else prefs.scheduleEnd
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(this, { _, h, m ->
            val picked = String.format(java.util.Locale.US, "%02d:%02d", h, m)
            if (isStart) prefs.scheduleStart = picked else prefs.scheduleEnd = picked
            refreshScheduleButtons()
            AlarmController.onConfigChanged(this)
        }, hour.coerceIn(0, 23), minute.coerceIn(0, 59), true).show()
    }

    private fun onRingtonePicked(uri: Uri) {
        val path = RingtoneStore.copyIn(this, uri)
        if (path == null) {
            Toast.makeText(this, R.string.ringtone_copy_failed, Toast.LENGTH_SHORT).show()
            return
        }
        RingtoneStore.deleteIfOwned(this, prefs.defaultRingtonePath)
        prefs.defaultRingtonePath = path
        binding.txtDefaultRingtone.text = getString(R.string.ringtone_custom)
    }

    override fun onResume() {
        super.onResume()
        refreshSystemAlarmVolume()
    }

    override fun onStop() {
        super.onStop()
        preview.stop()
        binding.btnPreview.setText(R.string.preview)
    }

    override fun onDestroy() {
        preview.stop()
        super.onDestroy()
    }
}
