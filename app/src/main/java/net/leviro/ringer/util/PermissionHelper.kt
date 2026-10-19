package net.leviro.ringer.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import net.leviro.ringer.R

/** Centralizes which permissions the app needs and how to request the special-access ones. */
object PermissionHelper {

    /** Runtime (dangerous) permissions requested through the normal dialog. */
    fun runtimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    fun missingRuntimePermissions(context: Context): List<String> =
        runtimePermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun hasAllRuntime(context: Context): Boolean = missingRuntimePermissions(context).isEmpty()

    /** True when Android 13+ would still show the notification permission dialog. */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    // ---- Battery optimization ----

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    // ---- Full-screen intent (Android 14+) ----

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun fullScreenIntentSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    // ---- Call-screening role (Android 10+: needed to read the caller number) ----

    fun isCallScreeningRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    }

    fun isCallScreeningRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun needsCallScreeningRole(context: Context): Boolean =
        isCallScreeningRoleAvailable(context) && !isCallScreeningRoleHeld(context)

    fun callScreeningRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    /**
     * String-resource ids of everything still missing for automatic alerts; empty list means
     * the app is ready. Call-screening availability itself is reported separately because
     * an unsupported device is not something setup can fix.
     */
    fun missingSetupItems(context: Context): List<Int> {
        val items = ArrayList<Int>()
        if (needsCallScreeningRole(context)) items.add(R.string.missing_role)
        if (!hasAllRuntime(context)) items.add(R.string.missing_runtime)
        if (!isIgnoringBatteryOptimizations(context)) items.add(R.string.missing_battery)
        return items
    }
}
