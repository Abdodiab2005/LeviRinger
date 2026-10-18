package net.leviro.ringer.call

import android.telecom.Call
import android.telecom.CallScreeningService
import net.leviro.ringer.util.AlarmController

/** Allows Telecom to proceed before matching an incoming caller. */
class RingerCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            val handle = callDetails.handle
            val number = if (callDetails.handlePresentation == android.telecom.TelecomManager.PRESENTATION_ALLOWED &&
                handle?.scheme == "tel") handle.schemeSpecificPart else null
            AlarmController.onIncomingCall(this, number)
        }
    }
}
