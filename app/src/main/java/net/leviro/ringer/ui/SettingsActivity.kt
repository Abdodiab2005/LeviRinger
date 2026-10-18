package net.leviro.ringer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivitySettingsBinding
import net.leviro.ringer.util.Prefs
import net.leviro.ringer.util.RingtoneStore

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private val pickRingtone = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::onRingtonePicked) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = Prefs(this)
        binding.txtDefaultRingtone.text =
            if (prefs.defaultRingtonePath == null) getString(R.string.ringtone_default)
            else getString(R.string.ringtone_custom)

        binding.btnDefaultRingtone.setOnClickListener { pickRingtone.launch(arrayOf("audio/*")) }
        binding.btnResetRingtone.setOnClickListener {
            RingtoneStore.deleteIfOwned(this, prefs.defaultRingtonePath)
            prefs.defaultRingtonePath = null
            binding.txtDefaultRingtone.text = getString(R.string.ringtone_default)
        }

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
}
