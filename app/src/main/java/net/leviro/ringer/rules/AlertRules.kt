package net.leviro.ringer.rules

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Daily window in minutes since midnight (0..1439). A start greater than the end crosses
 * midnight; an equal start and end covers the whole day; the end minute is inclusive.
 */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {

    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> true
        startMinute < endMinute -> minuteOfDay in startMinute..endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay <= endMinute
    }

    /** True when [minuteOfDay] lies in the after-midnight part of an overnight window (start > end and minuteOfDay <= end). */
    fun isAfterMidnightPart(minuteOfDay: Int): Boolean =
        startMinute > endMinute && minuteOfDay <= endMinute

    companion object {

        /** null when either value is null, blank or malformed. Callers treat null as "no restriction". */
        fun parse(start: String?, end: String?): TimeWindow? {
            val startMinute = parseMinute(start) ?: return null
            val endMinute = parseMinute(end) ?: return null
            return TimeWindow(startMinute, endMinute)
        }

        /** "HH:mm" or "H:mm" -> 0..1439, else null. */
        fun parseMinute(value: String?): Int? {
            val text = value?.trim() ?: return null
            val parts = text.split(':')
            if (parts.size != 2) return null
            val hourText = parts[0]
            val minuteText = parts[1]
            if (hourText.isEmpty() || hourText.length > 2 || !hourText.all { it.isDigit() }) return null
            if (minuteText.length != 2 || !minuteText.all { it.isDigit() }) return null
            val hour = hourText.toInt()
            val minute = minuteText.toInt()
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }

        /** 0..1439 -> "HH:mm". */
        fun format(minuteOfDay: Int): String {
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60
            return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
        }
    }
}

/**
 * Weekly availability mask for the global schedule. [days] uses bit0 = Monday .. bit6 = Sunday.
 * An overnight window belongs to the day it starts on.
 */
data class GlobalSchedule(
    val enabled: Boolean,
    val window: TimeWindow?,
    val days: Int = ALL_DAYS
) {

    /**
     * Not enabled -> true. Enabled: the day rule applies (the previous day is used for the
     * after-midnight part of an overnight window), then the window (null = no time restriction).
     * days == 0 -> false.
     */
    fun isActive(now: LocalDateTime): Boolean {
        if (!enabled) return true
        if (days == 0) return false
        val minute = now.hour * 60 + now.minute
        val effectiveDay =
            if (window != null && window.isAfterMidnightPart(minute)) now.dayOfWeek.minus(1)
            else now.dayOfWeek
        if (days and dayBit(effectiveDay) == 0) return false
        return window == null || window.contains(minute)
    }

    companion object {
        const val ALL_DAYS = 0x7F

        /** MONDAY -> 1, SUNDAY -> 64. */
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

enum class RingerState { NORMAL, VIBRATE, SILENT }

/** interruptionFilterAll: NotificationManager filter is ALL (or UNKNOWN). ringVolumeZero: STREAM_RING volume is 0. */
data class DeviceAudioState(
    val ringer: RingerState,
    val interruptionFilterAll: Boolean,
    val ringVolumeZero: Boolean
)

enum class SkipReason {
    APP_DISABLED,
    NO_MATCH,
    CONTACT_DISABLED,
    OUTSIDE_GLOBAL_SCHEDULE,
    OUTSIDE_CONTACT_WINDOW,
    PHONE_RINGS_NORMALLY,
    CALL_NOT_RINGING,
    MANUALLY_SILENCED
}

sealed interface AlertDecision {
    data object Alert : AlertDecision
    data class Skip(val reason: SkipReason) : AlertDecision
}

data class AlertInputs(
    val appEnabled: Boolean,
    val contactMatched: Boolean,
    val contactEnabled: Boolean,
    val globalSchedule: GlobalSchedule,
    val contactWindow: TimeWindow?,
    val now: LocalDateTime,
    val audio: DeviceAudioState,
    val alertWhenRinging: Boolean,
    val callRinging: Boolean,
    val manuallySilenced: Boolean
)

/** Pure decision logic for whether a matched caller should raise the alarm. */
object AlertRules {

    /** Checks in [SkipReason] declaration order and returns the first failing reason, else [AlertDecision.Alert]. */
    fun evaluate(input: AlertInputs): AlertDecision {
        if (!input.appEnabled) return AlertDecision.Skip(SkipReason.APP_DISABLED)
        if (!input.contactMatched) return AlertDecision.Skip(SkipReason.NO_MATCH)
        if (!input.contactEnabled) return AlertDecision.Skip(SkipReason.CONTACT_DISABLED)
        if (!input.globalSchedule.isActive(input.now)) return AlertDecision.Skip(SkipReason.OUTSIDE_GLOBAL_SCHEDULE)
        val minute = input.now.hour * 60 + input.now.minute
        if (input.contactWindow != null && !input.contactWindow.contains(minute)) {
            return AlertDecision.Skip(SkipReason.OUTSIDE_CONTACT_WINDOW)
        }
        if (!needsSpecialAlert(input.audio, input.alertWhenRinging)) {
            return AlertDecision.Skip(SkipReason.PHONE_RINGS_NORMALLY)
        }
        if (!input.callRinging) return AlertDecision.Skip(SkipReason.CALL_NOT_RINGING)
        if (input.manuallySilenced) return AlertDecision.Skip(SkipReason.MANUALLY_SILENCED)
        return AlertDecision.Alert
    }

    /** alertWhenRinging OR ringer != NORMAL OR !interruptionFilterAll OR ringVolumeZero. */
    fun needsSpecialAlert(audio: DeviceAudioState, alertWhenRinging: Boolean): Boolean =
        alertWhenRinging ||
            audio.ringer != RingerState.NORMAL ||
            !audio.interruptionFilterAll ||
            audio.ringVolumeZero
}
