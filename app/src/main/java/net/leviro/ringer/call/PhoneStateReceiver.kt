package net.leviro.ringer.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import net.leviro.ringer.util.AlarmController

/** Call-state cancellation survives process restarts; screening owns number capture. */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        intent.getStringExtra(TelephonyManager.EXTRA_STATE)?.let {
            AlarmController.onCallStateChanged(context, it)
        }
    }
}
