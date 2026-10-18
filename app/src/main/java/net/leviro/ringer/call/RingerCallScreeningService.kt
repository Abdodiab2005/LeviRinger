package net.leviro.ringer.call

import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import android.os.Build
import net.leviro.ringer.db.VipDao
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Prefs

/**
 * Android 10+ (API 29+) path. The system hands us the caller number here without needing
 * READ_CALL_LOG. We never block or silence the call (allow it through unchanged); we only
 * use the number to decide whether to fire our own alarm. Requires the user to grant the
 * call-screening role.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class RingerCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Pass every call through untouched — we are not a spam blocker.
        respondToCall(callDetails, CallResponse.Builder().build())

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        if (!Prefs(this).masterEnabled) return

        val number = callDetails.handle?.schemeSpecificPart // tel: number
        val vip = VipDao(this).findMatch(number)
        if (vip != null) AlarmController.trigger(this, vip)
    }
}
