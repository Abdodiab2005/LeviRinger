package net.leviro.ringer.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import net.leviro.ringer.db.VipDao
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Prefs

/**
 * Manifest-registered receiver for ACTION_PHONE_STATE_CHANGED (exempt from the implicit
 * broadcast ban, so it fires on every supported version, even after reboot).
 *
 *  - RINGING : on API < 29 the incoming number is present here -> match + trigger alarm.
 *              On API >= 29 the number is withheld; RingerCallScreeningService handles it.
 *  - OFFHOOK : call answered/active -> stop the alarm.
 *  - IDLE    : call ended -> stop the alarm. (All versions.)
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // On Android 10+ the screening service owns number capture.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
                if (!Prefs(context).masterEnabled) return

                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                val vip = VipDao(context).findMatch(number)
                if (vip != null) AlarmController.trigger(context, vip)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (AlarmController.active) AlarmController.stop(context)
            }
        }
    }
}
