package net.leviro.ringer.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Xiaomi/MIUI, Oppo, Vivo, Huawei, Samsung, etc. kill background apps unless the user
 * manually enables "Autostart". No app can toggle this programmatically, so the best we
 * can do is deep-link the user to the right OEM screen. Each candidate is tried in turn;
 * if none resolve we fall back to the app's own settings page.
 */
object OemAutostart {

    private val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
    )

    /** True if this device is from a manufacturer known to need manual autostart. */
    fun isLikelyAffected(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "huawei", "honor", "samsung", "asus", "letv", "oneplus")
            .any { m.contains(it) }
    }

    /** Tries to open an OEM autostart screen; returns true if one was launched. */
    fun open(context: Context): Boolean {
        for (cn in candidates) {
            val intent = Intent().apply {
                component = cn
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                return try {
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        return false
    }
}
