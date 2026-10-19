package net.leviro.ringer.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButtonToggleGroup
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivityAddContactBinding
import net.leviro.ringer.db.Vip
import net.leviro.ringer.db.VipDao
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.PhoneMatch
import net.leviro.ringer.util.Prefs
import net.leviro.ringer.util.RingtoneStore
import java.io.File
import java.util.Locale

class AddContactActivity : BaseActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private lateinit var dao: VipDao
    private lateinit var prefs: Prefs
    private lateinit var preview: SoundPreview
    private var editingId: Long = 0
    private var existing: Vip? = null
    private var pickedRingtonePath: String? = null
    private var startTime: String? = null
    private var endTime: String? = null
    private var volumePercent: Int? = null
    private var delaySeconds: Int? = null

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let(::readContact)
    }

    private val pickRingtone = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::onRingtonePicked) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        dao = VipDao(this)
        prefs = Prefs(this)
        preview = SoundPreview(this)
        editingId = intent.getLongExtra("id", 0)

        if (editingId != 0L) {
            existing = dao.getAll().firstOrNull { it.id == editingId }
            existing?.let { fill(it) }
            binding.toolbar.title = getString(R.string.edit_contact)
        } else {
            binding.toolbar.title = getString(R.string.add_contact)
            binding.swEnabled.isChecked = true
        }
        refreshTimeButtons()
        setupOverrides(existing)

        binding.btnPickContact.setOnClickListener {
            pickContact.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            )
        }
        binding.btnPickRingtone.setOnClickListener {
            pickRingtone.launch(arrayOf("audio/*"))
        }
        binding.btnDefaultRingtone.setOnClickListener {
            RingtoneStore.deleteIfOwned(this, pickedRingtonePath?.takeIf { it != existing?.ringtonePath })
            pickedRingtonePath = null
            binding.txtRingtone.text = getString(R.string.ringtone_default)
        }
        binding.btnStartTime.setOnClickListener { pickTime(isStart = true) }
        binding.btnEndTime.setOnClickListener { pickTime(isStart = false) }
        binding.btnClearSchedule.setOnClickListener {
            startTime = null
            endTime = null
            refreshTimeButtons()
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun fill(vip: Vip) {
        binding.editName.setText(vip.name)
        binding.editNumber.setText(vip.number)
        binding.editNote.setText(vip.note ?: "")
        binding.swEnabled.isChecked = vip.enabled
        binding.swVibrateOnly.isChecked = vip.vibrateOnly
        startTime = vip.startTime
        endTime = vip.endTime
        pickedRingtonePath = vip.ringtonePath
        binding.txtRingtone.text = ringtoneLabel(vip.ringtonePath)
    }

    /** Each override starts from the Settings value, so a contact only differs where the user chose it. */
    private fun setupOverrides(vip: Vip?) {
        volumePercent = vip?.volumePercent
        delaySeconds = vip?.delaySeconds
        binding.swCustomVolume.isChecked = volumePercent != null
        binding.sliderVolume.value = (volumePercent ?: prefs.alertVolumePercent).toFloat()
        binding.swCustomDelay.isChecked = delaySeconds != null
        binding.sliderDelay.value = (delaySeconds ?: prefs.alertDelaySeconds).toFloat()
        binding.toggleRingAlso.select(vip?.alertWhenRinging)
        binding.toggleShowCard.select(vip?.showCard)
        binding.toggleBoost.select(vip?.boostAlarmVolume)
        refreshOverrides()

        binding.swCustomVolume.setOnCheckedChangeListener { _, on ->
            volumePercent = if (on) binding.sliderVolume.value.toInt() else null
            refreshOverrides()
        }
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            volumePercent = value.toInt()
            refreshOverrides()
            if (preview.isPlaying) preview.setGain(value.toInt())
        }
        binding.swCustomDelay.setOnCheckedChangeListener { _, on ->
            delaySeconds = if (on) binding.sliderDelay.value.toInt() else null
            refreshOverrides()
        }
        binding.sliderDelay.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            delaySeconds = value.toInt()
            refreshOverrides()
        }
        binding.btnPreview.setOnClickListener { togglePreview() }
    }

    private fun refreshOverrides() {
        val volume = volumePercent
        val delay = delaySeconds
        binding.layoutCustomVolume.visibility = if (volume != null) View.VISIBLE else View.GONE
        binding.layoutCustomDelay.visibility = if (delay != null) View.VISIBLE else View.GONE
        binding.txtVolumeLabel.text = getString(R.string.volume_label_fmt, volume ?: prefs.alertVolumePercent)
        binding.txtDelayLabel.text = getString(R.string.delay_label_fmt, delay ?: prefs.alertDelaySeconds)
    }

    /** Plays this contact's sound at its own volume, so the level can be judged before saving. */
    private fun togglePreview() {
        if (preview.isPlaying) {
            stopPreview()
            return
        }
        val path = pickedRingtonePath ?: prefs.defaultRingtonePath
        preview.start(path?.let { Uri.fromFile(File(it)) } ?: RingtoneStore.defaultAlarmUri(),
            volumePercent ?: prefs.alertVolumePercent)
        binding.btnPreview.setText(R.string.preview_stop)
    }

    private fun stopPreview() {
        preview.stop()
        binding.btnPreview.setText(R.string.preview)
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    private fun MaterialButtonToggleGroup.select(value: Boolean?) =
        check(getChildAt(if (value == null) 0 else if (value) 1 else 2).id)

    private fun MaterialButtonToggleGroup.override(): Boolean? =
        when (indexOfChild(findViewById(checkedButtonId))) { 1 -> true; 2 -> false; else -> null }

    private fun readContact(uri: Uri) {
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val number = c.getString(0)
                    val name = c.getString(1)
                    if (!number.isNullOrBlank()) binding.editNumber.setText(number)
                    if (!name.isNullOrBlank() && binding.editName.text.isNullOrBlank()) {
                        binding.editName.setText(name)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.contact_read_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onRingtonePicked(uri: Uri) {
        val path = RingtoneStore.copyIn(this, uri)
        if (path == null) {
            Toast.makeText(this, R.string.ringtone_copy_failed, Toast.LENGTH_SHORT).show()
            return
        }
        // Remove a previously copied (non-original) file we are replacing.
        if (pickedRingtonePath != null && pickedRingtonePath != existing?.ringtonePath) {
            RingtoneStore.deleteIfOwned(this, pickedRingtonePath)
        }
        pickedRingtonePath = path
        binding.txtRingtone.text = ringtoneLabel(path)
    }

    private fun ringtoneLabel(path: String?): String =
        if (path == null) getString(R.string.ringtone_default)
        else File(path).name.substringAfterLast('.', "").let {
            getString(R.string.ringtone_custom)
        }

    /** 24-hour picker. Unset start defaults to 00:00 and unset end to 23:59, like the installed build. */
    private fun pickTime(isStart: Boolean) {
        val current = if (isStart) startTime else endTime
        val parts = current?.split(":")
        val defaultHour = if (isStart) 0 else 23
        val defaultMinute = if (isStart) 0 else 59
        val hour = parts?.getOrNull(0)?.toIntOrNull() ?: defaultHour
        val minute = parts?.getOrNull(1)?.toIntOrNull() ?: defaultMinute
        TimePickerDialog(this, { _, h, m ->
            val value = String.format(Locale.US, "%02d:%02d", h, m)
            if (isStart) startTime = value else endTime = value
            refreshTimeButtons()
        }, hour, minute, true).show()
    }

    private fun refreshTimeButtons() {
        binding.btnStartTime.text = startTime ?: getString(R.string.start_time)
        binding.btnEndTime.text = endTime ?: getString(R.string.end_time)
    }

    private fun save() {
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val number = binding.editNumber.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) { binding.editName.error = getString(R.string.required); return }
        if (number.isEmpty()) { binding.editNumber.error = getString(R.string.required); return }
        if ((startTime == null) != (endTime == null)) {
            Toast.makeText(this, R.string.schedule_both_required, Toast.LENGTH_SHORT).show()
            return
        }
        val duplicate = dao.getAll().any {
            it.id != editingId && PhoneMatch.matches(number, it.number)
        }
        if (duplicate) {
            binding.editNumber.error = getString(R.string.duplicate_number)
            return
        }

        val vip = Vip(
            id = editingId,
            name = name,
            number = number,
            normalizedNumber = PhoneMatch.normalize(number),
            ringtonePath = pickedRingtonePath,
            note = binding.editNote.text?.toString()?.trim()?.ifBlank { null },
            enabled = binding.swEnabled.isChecked,
            vibrateOnly = binding.swVibrateOnly.isChecked,
            startTime = startTime,
            endTime = endTime,
            volumePercent = volumePercent,
            delaySeconds = delaySeconds,
            alertWhenRinging = binding.toggleRingAlso.override(),
            showCard = binding.toggleShowCard.override(),
            boostAlarmVolume = binding.toggleBoost.override()
        )

        if (editingId == 0L) {
            dao.insert(vip)
        } else {
            // If the original custom ringtone was swapped out, clean it up.
            val old = existing?.ringtonePath
            if (old != null && old != pickedRingtonePath) RingtoneStore.deleteIfOwned(this, old)
            dao.update(vip)
        }
        AlarmController.onConfigChanged(this)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
