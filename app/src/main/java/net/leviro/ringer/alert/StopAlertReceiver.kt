package net.leviro.ringer.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.leviro.ringer.util.AlarmController
import net.leviro.ringer.util.Const

/** A Stop action can never start playback or silence a newer call. */
class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Const.ACTION_USER_STOP) {
            AlarmController.stopGeneration(context, intent.getLongExtra(Const.EXTRA_GENERATION, -1))
        }
    }
}
