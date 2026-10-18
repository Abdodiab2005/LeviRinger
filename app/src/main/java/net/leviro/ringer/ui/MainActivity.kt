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
import net.leviro.ringer.rules.GlobalSchedule
import net.leviro.ringer.rules.TimeWindow
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.PermissionHelper
import net.leviro.ringer.util.Prefs
import java.time.LocalDateTime

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
            onToggle = { vip, checked ->
                dao.setEnabled(vip.id, checked)
                AlarmController.onConfigChanged(this)
            },
            onDelete = { vip -> confirmDelete(vip) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.swMaster.isChecked = prefs.masterEnabled
        binding.swMaster.setOnCheckedChangeListener { _, c ->
            prefs.masterEnabled = c
            refreshStatus()
            AlarmController.onConfigChanged(this)
        }

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
        refreshStatus()
    }

    private fun refresh() {
        val items = dao.getAll()
        adapter.submit(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Status line: master state, what is still missing, and the global schedule state. */
    private fun refreshStatus() {
        binding.txtStatus.setText(
            if (prefs.masterEnabled) R.string.status_active else R.string.status_paused
        )

        if (!PermissionHelper.isCallScreeningRoleAvailable(this)) {
            binding.txtReadiness.setText(R.string.status_role_unavailable)
        } else {
            val gaps = PermissionHelper.missingSetupItems(this)
            binding.txtReadiness.text = if (gaps.isEmpty()) {
                getString(R.string.status_ready)
            } else {
                getString(R.string.status_needs, gaps.joinToString(", ") { getString(it) })
            }
        }

        val showSchedule = prefs.scheduleEnabled
        binding.txtScheduleState.visibility = if (showSchedule) View.VISIBLE else View.GONE
        if (showSchedule) {
            val schedule = GlobalSchedule(
                prefs.scheduleEnabled,
                TimeWindow.parse(prefs.scheduleStart, prefs.scheduleEnd),
                prefs.scheduleDays
            )
            binding.txtScheduleState.text = if (schedule.isActive(LocalDateTime.now())) {
                getString(R.string.schedule_active_now)
            } else {
                getString(
                    R.string.schedule_outside_hours, prefs.scheduleStart, prefs.scheduleEnd
                )
            }
        }
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(this, AddContactActivity::class.java).putExtra("id", id))
    }

    private fun confirmDelete(vip: Vip) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, vip.name))
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                dao.delete(vip.id)
                AlarmController.onConfigChanged(this)
                refresh()
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
        AlarmController.triggerTest(this)
        Snackbar.make(binding.root, R.string.test_started, Snackbar.LENGTH_LONG).show()
    }
}
