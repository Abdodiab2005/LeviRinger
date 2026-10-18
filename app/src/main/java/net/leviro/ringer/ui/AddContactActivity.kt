package net.leviro.ringer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivityAddContactBinding
import net.leviro.ringer.db.Vip
import net.leviro.ringer.db.VipDao
import net.leviro.ringer.util.PhoneMatch
import net.leviro.ringer.util.Prefs
import net.leviro.ringer.util.RingtoneStore
import java.io.File

class AddContactActivity : BaseActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private lateinit var dao: VipDao
    private var editingId: Long = 0
    private var existing: Vip? = null
    private var pickedRingtonePath: String? = null

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
        editingId = intent.getLongExtra("id", 0)

        if (editingId != 0L) {
            existing = dao.getAll().firstOrNull { it.id == editingId }
            existing?.let { fill(it) }
            binding.toolbar.title = getString(R.string.edit_contact)
        } else {
            binding.toolbar.title = getString(R.string.add_contact)
            binding.swEnabled.isChecked = true
        }

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
        binding.btnSave.setOnClickListener { save() }
    }

    private fun fill(vip: Vip) {
        binding.editName.setText(vip.name)
        binding.editNumber.setText(vip.number)
        binding.editNote.setText(vip.note ?: "")
        binding.swEnabled.isChecked = vip.enabled
        pickedRingtonePath = vip.ringtonePath
        binding.txtRingtone.text = ringtoneLabel(vip.ringtonePath)
    }

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

    private fun save() {
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val number = binding.editNumber.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) { binding.editName.error = getString(R.string.required); return }
        if (number.isEmpty()) { binding.editNumber.error = getString(R.string.required); return }

        val vip = Vip(
            id = editingId,
            name = name,
            number = number,
            normalizedNumber = PhoneMatch.normalize(number),
            ringtonePath = pickedRingtonePath,
            note = binding.editNote.text?.toString()?.trim()?.ifBlank { null },
            enabled = binding.swEnabled.isChecked
        )

        if (editingId == 0L) {
            dao.insert(vip)
        } else {
            // If the original custom ringtone was swapped out, clean it up.
            val old = existing?.ringtonePath
            if (old != null && old != pickedRingtonePath) RingtoneStore.deleteIfOwned(this, old)
            dao.update(vip)
        }
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
