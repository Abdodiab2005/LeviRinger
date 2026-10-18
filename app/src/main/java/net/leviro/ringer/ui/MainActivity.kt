package net.leviro.ringer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivityMainBinding
import net.leviro.ringer.db.Vip
import net.leviro.ringer.db.VipDao
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.PermissionHelper
import net.leviro.ringer.util.Prefs

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dao: VipDao
    private lateinit var prefs: Prefs
    private lateinit var adapter: VipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        dao = VipDao(this)
        prefs = Prefs(this)

        adapter = VipAdapter(
            emptyList(),
            onClick = { vip -> openEditor(vip.id) },
            onToggle = { vip, checked -> dao.setEnabled(vip.id, checked) },
            onDelete = { vip -> confirmDelete(vip) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.swMaster.isChecked = prefs.masterEnabled
        binding.swMaster.setOnCheckedChangeListener { _, c -> prefs.masterEnabled = c }

        binding.fabAdd.setOnClickListener { openEditor(0) }
        binding.btnSetup.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        if (!prefs.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateSetupBanner()
    }

    private fun refresh() {
        val items = dao.getAll()
        adapter.submit(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateSetupBanner() {
        val ready = PermissionHelper.hasAllRuntime(this) &&
            !PermissionHelper.needsCallScreeningRole(this) &&
            PermissionHelper.isIgnoringBatteryOptimizations(this)
        binding.setupCard.visibility = if (ready) View.GONE else View.VISIBLE
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(this, AddContactActivity::class.java).putExtra("id", id))
    }

    private fun confirmDelete(vip: Vip) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, vip.name))
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                dao.delete(vip.id); refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            R.id.action_test -> {
                testAlarm(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Fires the full alert with a fake caller so the user can verify sound + lock-screen behavior. */
    private fun testAlarm() {
        AlarmController.trigger(
            this,
            getString(R.string.test_caller_name),
            "+200000000000",
            getString(R.string.test_caller_note),
            prefs.defaultRingtonePath
        )
        Snackbar.make(binding.root, R.string.test_started, Snackbar.LENGTH_LONG).show()
    }
}
