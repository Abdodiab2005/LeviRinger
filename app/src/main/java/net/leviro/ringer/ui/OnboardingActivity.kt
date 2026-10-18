package net.leviro.ringer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import net.leviro.ringer.R
import net.leviro.ringer.databinding.ActivityOnboardingBinding
import net.leviro.ringer.util.OemAutostart
import net.leviro.ringer.util.PermissionHelper
import net.leviro.ringer.util.Prefs

/**
 * Walks the user through every permission/setting the app needs, one tap at a time, with a
 * live status tick on each row. Nothing here can be forced silently — Android requires the
 * user to grant each of these, so we make the path as short and clear as possible.
 */
class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    private val genericResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPerms.setOnClickListener {
            requestPerms.launch(PermissionHelper.runtimePermissions())
        }
        binding.btnRole.setOnClickListener {
            PermissionHelper.callScreeningRoleIntent(this)?.let { genericResult.launch(it) }
        }
        binding.btnBattery.setOnClickListener {
            genericResult.launch(PermissionHelper.batteryOptimizationIntent(this))
        }
        binding.btnFullscreen.setOnClickListener {
            genericResult.launch(PermissionHelper.fullScreenIntentSettings(this))
        }
        binding.btnDnd.setOnClickListener {
            genericResult.launch(PermissionHelper.notificationPolicyIntent())
        }
        binding.btnAutostart.setOnClickListener {
            if (!OemAutostart.open(this)) {
                // Fall back to the app's system settings page.
                genericResult.launch(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                )
            }
        }
        binding.btnDone.setOnClickListener {
            Prefs(this).onboardingDone = true
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        // 1) runtime permissions
        mark(binding.tickPerms, PermissionHelper.hasAllRuntime(this))

        // 2) call-screening role (API 29+ only)
        val roleNeeded = PermissionHelper.needsCallScreeningRole(this)
        binding.rowRole.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) View.VISIBLE else View.GONE
        mark(binding.tickRole, !roleNeeded)

        // 3) battery optimization
        mark(binding.tickBattery, PermissionHelper.isIgnoringBatteryOptimizations(this))

        // 4) full-screen intent (API 34+ only)
        binding.rowFullscreen.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) View.VISIBLE else View.GONE
        mark(binding.tickFullscreen, PermissionHelper.canUseFullScreenIntent(this))

        // 5) DND policy access (optional)
        mark(binding.tickDnd, PermissionHelper.hasNotificationPolicyAccess(this))

        // 6) OEM autostart (only on known-aggressive devices; cannot be auto-detected, so we
        //    just surface the guidance — no tick)
        binding.rowAutostart.visibility =
            if (OemAutostart.isLikelyAffected()) View.VISIBLE else View.GONE
    }

    private fun mark(view: android.widget.ImageView, done: Boolean) {
        view.setImageResource(if (done) R.drawable.ic_check else R.drawable.ic_pending)
    }
}
