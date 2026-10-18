package net.leviro.ringer.util

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import net.leviro.ringer.R
import net.leviro.ringer.db.Vip
import net.leviro.ringer.db.VipDao
import net.leviro.ringer.rules.AlertDecision
import net.leviro.ringer.rules.AlertInputs
import net.leviro.ringer.rules.AlertRules
import net.leviro.ringer.rules.GlobalSchedule
import net.leviro.ringer.rules.TimeWindow
import net.leviro.ringer.service.AlarmService
import java.time.LocalDateTime

/** Owns the call generation, eligibility and all alert transitions on the main looper. */
object AlarmController {
    private enum class State { IDLE, WAITING_FOR_DELAY, ALERTING, STOPPED }

    internal data class CallAlert(
        val generation: Long,
        val vipId: Long,
        val name: String,
        val number: String,
        val note: String?,
        val ringtonePath: String?,
        val vibrateOnly: Boolean,
        val delaySeconds: Int,
        val test: Boolean = false
    )

    private val handler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var generation = SystemClock.elapsedRealtimeNanos()
    private var call: CallAlert? = null
    private var service: AlarmService? = null
    private var latestCallState: String? = null
    private var ringingSeen = false
    private var manuallySilenced = false
    private var delayDue = 0L
    private var delayFired = false
    private var delayTask: Runnable? = null
    private var capTask: Runnable? = null

    @Volatile
    private var running = false
    val active: Boolean get() = running

    private fun serialized(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post { block() }
    }

    fun onIncomingCall(context: Context, number: String?) = serialized {
        val app = context.applicationContext
        finish(app, "new incoming call")
        val nextGeneration = ++generation
        if (number.isNullOrBlank()) {
            Log.i(TAG, "skip NO_MATCH private/blank")
            return@serialized
        }
        manuallySilenced = false
        val prefs = Prefs(app)
        val vip = VipDao(app).findMatch(number)
        val decision = evaluate(app, prefs, vip, true)
        if (decision is AlertDecision.Skip) {
            Log.i(TAG, "skip ${decision.reason} suffix=${number.filter(Char::isDigit).takeLast(2)}")
            return@serialized
        }
        vip ?: return@serialized
        Log.i(TAG, "match vip=${vip.id} suffix=${number.filter(Char::isDigit).takeLast(2)}")
        arm(app, CallAlert(nextGeneration, vip.id, vip.name, number, vip.note,
            vip.ringtonePath ?: prefs.defaultRingtonePath,
            vip.vibrateOnly || prefs.defaultVibrateOnly, prefs.alertDelaySeconds))
    }

    fun onCallStateChanged(context: Context, state: String) = serialized {
        if (state !in listOf(TelephonyManager.EXTRA_STATE_RINGING,
                TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE)) return@serialized
        latestCallState = state
        if (call?.test == true) return@serialized
        if (state == TelephonyManager.EXTRA_STATE_IDLE &&
            this.state == State.WAITING_FOR_DELAY && !ringingSeen) {
            Log.i(TAG, "generation=${call?.generation} ignore pre-ring IDLE")
            return@serialized
        }
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                ringingSeen = true
                if (delayFired) tryAlert(context.applicationContext)
            }
            else -> finish(context.applicationContext, "call $state")
        }
    }

    fun stop(context: Context) = serialized {
        manuallySilenced = true
        finish(context.applicationContext, "user Stop")
    }

    internal fun stopGeneration(context: Context, expected: Long) = serialized {
        if (call?.generation == expected) stop(context)
    }

    fun triggerTest(context: Context) = serialized {
        val app = context.applicationContext
        finish(app, "new test")
        val prefs = Prefs(app)
        arm(app, CallAlert(++generation, 0, app.getString(R.string.test_caller_name), "",
            app.getString(R.string.test_caller_note), prefs.defaultRingtonePath,
            prefs.defaultVibrateOnly, 0, test = true))
    }

    fun onConfigChanged(context: Context) = serialized {
        val current = call ?: return@serialized
        if (current.test) return@serialized
        // A pending screening match remains eligible before the first RINGING signal.
        val eligible = freshDecision(context, current, true)
        if (eligible is AlertDecision.Skip) finish(context.applicationContext, "config ${eligible.reason}")
    }

    private fun arm(context: Context, alert: CallAlert) {
        call = alert
        state = State.WAITING_FOR_DELAY
        running = true
        manuallySilenced = false
        ringingSeen = latestCallState == TelephonyManager.EXTRA_STATE_RINGING
        delayFired = false
        delayDue = SystemClock.uptimeMillis() + alert.delaySeconds * 1000L
        Log.i(TAG, "generation=${alert.generation} IDLE -> WAITING_FOR_DELAY delay=${alert.delaySeconds}")
        delayTask = Runnable {
            if (call?.generation != alert.generation || state != State.WAITING_FOR_DELAY) return@Runnable
            delayFired = true
            Log.i(TAG, "generation=${alert.generation} delay fired")
            tryAlert(context)
        }.also { handler.postAtTime(it, delayDue) }
        capTask = Runnable {
            if (call?.generation == alert.generation) finish(context, "hard cap")
        }.also { handler.postDelayed(it, alert.delaySeconds * 1000L + if (alert.test) 30_000L else 180_000L) }
        sendArm(context, alert)
    }

    /** Visible cards may retry only the still-pending generation. */
    internal fun resumeFromCard(context: Context, expected: Long): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val current = call ?: return false
        if (current.generation != expected) return false
        if (state == State.WAITING_FOR_DELAY && service == null) sendArm(context, current)
        return active
    }

    private fun sendArm(context: Context, current: CallAlert) {
        val intent = Intent(context, AlarmService::class.java).setAction(Const.ACTION_ARM)
            .putExtra(Const.EXTRA_GENERATION, current.generation)
            .putExtra(Const.EXTRA_VIP_ID, current.vipId)
            .putExtra(Const.EXTRA_NAME, current.name)
            .putExtra(Const.EXTRA_NUMBER, current.number)
            .putExtra(Const.EXTRA_NOTE, current.note)
            .putExtra(Const.EXTRA_RINGTONE, current.ringtonePath)
            .putExtra(Const.EXTRA_VIBRATE_ONLY, current.vibrateOnly)
            .putExtra(Const.EXTRA_DELAY_SECONDS, current.delaySeconds)
        try {
            ContextCompat.startForegroundService(context, intent)
            Log.i(TAG, "generation=${current.generation} FGS start accepted")
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "generation=${current.generation} FGS start forbidden")
            } else Log.w(TAG, "generation=${current.generation} FGS start failed ${e.javaClass.simpleName}")
            postFallback(context, current)
        } catch (e: SecurityException) {
            Log.w(TAG, "generation=${current.generation} FGS start denied")
            postFallback(context, current)
        }
    }

    private fun postFallback(context: Context, current: CallAlert) {
        Notifications.postAlert(context, current, waiting = true,
            fullScreen = Prefs(context).showAlertCard && Notifications.canUseFullScreenIntent(context))
        Log.w(TAG, "generation=${current.generation} notification fallback; playback awaits visible card")
    }

    /** ARM payloads never recreate state after process death or revive a stopped call. */
    internal fun attach(owner: AlarmService, expected: Long): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val current = call ?: return false
        if (expected != current.generation) return false
        if (service === owner) return true
        if (state != State.WAITING_FOR_DELAY) return false
        service = owner
        try {
            owner.arm(current, (delayDue - SystemClock.uptimeMillis()).coerceAtLeast(0))
        } catch (e: RuntimeException) {
            Log.w(TAG, "generation=$expected FGS ARM failed ${e.javaClass.simpleName}")
            service = null
            owner.releaseAlert()
            owner.stopSelf()
            postFallback(owner, current)
            return false
        }
        if (delayFired) tryAlert(owner)
        return true
    }

    internal fun serviceDestroyed(owner: AlarmService) = serialized {
        if (service === owner) finish(owner.applicationContext, "service destroyed")
    }

    internal fun playbackAllowed(expected: Long): Boolean =
        call?.generation == expected && state == State.ALERTING && !manuallySilenced

    private fun tryAlert(context: Context) {
        val current = call ?: return
        if (state != State.WAITING_FOR_DELAY || !delayFired) return
        if (!current.test) {
            val decision = freshDecision(context, current, true)
            if (decision is AlertDecision.Skip) {
                finish(context, "delay ${decision.reason}")
                return
            }
        }
        if (!current.test && !ringingSeen) {
            if (SystemClock.uptimeMillis() < delayDue + 5_000L) {
                delayTask?.let(handler::removeCallbacks)
                delayTask = Runnable {
                    if (call?.generation == current.generation) tryAlert(context)
                }.also { handler.postAtTime(it, delayDue + 5_000L) }
                return
            }
            finish(context, "CALL_NOT_RINGING timeout")
            return
        }
        val owner = service ?: return
        val prefs = Prefs(context)
        val vip = if (current.test) null else VipDao(context).getAll().firstOrNull { it.id == current.vipId }
        val refreshed = if (current.test) current else current.copy(
            name = vip?.name.orEmpty(), note = vip?.note,
            ringtonePath = vip?.ringtonePath ?: prefs.defaultRingtonePath,
            vibrateOnly = vip?.vibrateOnly == true || prefs.defaultVibrateOnly)
        call = refreshed
        state = State.ALERTING
        capTask?.let(handler::removeCallbacks)
        capTask = Runnable {
            if (call?.generation == current.generation) finish(context, "hard cap")
        }.also { handler.postDelayed(it, if (current.test) 30_000L else 180_000L) }
        Log.i(TAG, "generation=${current.generation} WAITING_FOR_DELAY -> ALERTING")
        owner.alert(refreshed, prefs)
    }

    private fun freshDecision(context: Context, current: CallAlert, ringing: Boolean): AlertDecision =
        evaluate(context, Prefs(context), VipDao(context).getAll().firstOrNull { it.id == current.vipId }, ringing)

    private fun evaluate(context: Context, prefs: Prefs, vip: Vip?, ringing: Boolean): AlertDecision =
        AlertRules.evaluate(AlertInputs(
            appEnabled = prefs.masterEnabled, contactMatched = vip != null,
            contactEnabled = vip?.enabled == true,
            globalSchedule = GlobalSchedule(prefs.scheduleEnabled,
                TimeWindow.parse(prefs.scheduleStart, prefs.scheduleEnd), prefs.scheduleDays),
            contactWindow = TimeWindow.parse(vip?.startTime, vip?.endTime),
            now = LocalDateTime.now(), audio = DeviceAudio.read(context),
            alertWhenRinging = prefs.alertWhenRinging, callRinging = ringing,
            manuallySilenced = manuallySilenced))

    private fun finish(context: Context, cause: String) {
        val current = call ?: return
        if (state == State.IDLE || state == State.STOPPED) return
        Log.i(TAG, "generation=${current.generation} $state -> STOPPED cause=$cause")
        state = State.STOPPED
        running = false
        delayTask?.let(handler::removeCallbacks)
        capTask?.let(handler::removeCallbacks)
        delayTask = null
        capTask = null
        val owner = service
        service = null
        call = null
        owner?.releaseAlert()
        owner?.stopSelf()
        Notifications.cancel(context)
        context.sendBroadcast(Intent(Const.ACTION_STOP_ALERT).setPackage(context.packageName)
            .putExtra(Const.EXTRA_GENERATION, current.generation))
        state = State.IDLE
        Log.i(TAG, "generation=${current.generation} STOPPED -> IDLE")
    }

    private const val TAG = "LeviroRinger"
}
