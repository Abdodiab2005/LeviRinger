package net.leviro.ringer.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class AlertRulesTest {

    private fun baseInput() = AlertInputs(
        appEnabled = true,
        contactMatched = true,
        contactEnabled = true,
        globalSchedule = GlobalSchedule(enabled = false, window = null),
        contactWindow = null,
        now = LocalDateTime.of(2026, 1, 5, 12, 0),
        audio = DeviceAudioState(RingerState.NORMAL, interruptionFilterAll = true, ringVolumeZero = false),
        alertWhenRinging = false,
        callRinging = true,
        manuallySilenced = false
    )

    // --- TimeWindow ------------------------------------------------------------

    @Test
    fun parseAcceptsBothHourFormats() {
        assertEquals(TimeWindow(7 * 60, 22 * 60), TimeWindow.parse("07:00", "22:00"))
        assertEquals(TimeWindow(7 * 60, 22 * 60), TimeWindow.parse("7:00", "22:00"))
        assertEquals(7 * 60, TimeWindow.parseMinute("07:00"))
        assertEquals(0, TimeWindow.parseMinute("0:00"))
    }

    @Test
    fun parseRejectsMalformedOrMissingValues() {
        assertNull(TimeWindow.parse(null, "10:00"))
        assertNull(TimeWindow.parse("10:00", null))
        assertNull(TimeWindow.parse("", "10:00"))
        assertNull(TimeWindow.parse("24:00", "10:00"))
        assertNull(TimeWindow.parse("12:60", "10:00"))
        assertNull(TimeWindow.parse("1200", "10:00"))
        assertNull(TimeWindow.parse("ab:cd", "10:00"))
        assertNull(TimeWindow.parse("9:5", "10:00"))
    }

    @Test
    fun sameDayWindowIsInclusiveAtBothEnds() {
        val window = TimeWindow(9 * 60, 17 * 60)
        assertTrue(window.contains(9 * 60))
        assertTrue(window.contains(17 * 60))
        assertFalse(window.contains(8 * 60 + 59))
        assertFalse(window.contains(17 * 60 + 1))
    }

    @Test
    fun overnightWindowCoversLateAndEarlyMinutes() {
        val window = TimeWindow(22 * 60, 6 * 60)
        assertFalse(window.contains(21 * 60 + 59))
        assertTrue(window.contains(22 * 60))
        assertTrue(window.contains(23 * 60 + 59))
        assertTrue(window.contains(0))
        assertTrue(window.contains(6 * 60))
        assertFalse(window.contains(6 * 60 + 1))
    }

    @Test
    fun equalStartAndEndCoversWholeDay() {
        val window = TimeWindow(10 * 60, 10 * 60)
        assertTrue(window.contains(0))
        assertTrue(window.contains(10 * 60))
        assertTrue(window.contains(1439))
    }

    @Test
    fun isAfterMidnightPartOnlyInOvernightTail() {
        val overnight = TimeWindow(22 * 60, 6 * 60)
        assertTrue(overnight.isAfterMidnightPart(3 * 60))
        assertTrue(overnight.isAfterMidnightPart(6 * 60))
        assertFalse(overnight.isAfterMidnightPart(6 * 60 + 1))
        assertFalse(overnight.isAfterMidnightPart(23 * 60))
        assertFalse(TimeWindow(9 * 60, 17 * 60).isAfterMidnightPart(10 * 60))
    }

    @Test
    fun formatRoundTrips() {
        assertEquals("00:00", TimeWindow.format(0))
        assertEquals("23:59", TimeWindow.format(1439))
        assertEquals("07:05", TimeWindow.format(TimeWindow.parseMinute("7:05")!!))
        assertEquals(330, TimeWindow.parseMinute(TimeWindow.format(330)))
    }

    // --- GlobalSchedule --------------------------------------------------------

    @Test
    fun disabledScheduleIsAlwaysActive() {
        assertTrue(GlobalSchedule(false, null).isActive(LocalDateTime.of(2026, 1, 5, 12, 0)))
        val window = TimeWindow(22 * 60, 6 * 60)
        assertTrue(GlobalSchedule(false, window, days = 0).isActive(LocalDateTime.of(2026, 1, 10, 3, 0)))
    }

    @Test
    fun fridayOvernightWindowUsesStartDay() {
        val schedule = GlobalSchedule(
            enabled = true,
            window = TimeWindow(22 * 60, 6 * 60),
            days = GlobalSchedule.dayBit(DayOfWeek.FRIDAY)
        )
        assertTrue(schedule.isActive(LocalDateTime.of(2026, 1, 9, 23, 0)))   // Friday night
        assertTrue(schedule.isActive(LocalDateTime.of(2026, 1, 10, 3, 0)))  // Saturday after midnight
        assertFalse(schedule.isActive(LocalDateTime.of(2026, 1, 10, 23, 0))) // Saturday night
    }

    @Test
    fun zeroDaysIsInactiveWhenEnabled() {
        assertFalse(GlobalSchedule(true, null, days = 0).isActive(LocalDateTime.of(2026, 1, 5, 12, 0)))
    }

    @Test
    fun nullWindowFallsBackToDayRule() {
        val schedule = GlobalSchedule(true, null, days = GlobalSchedule.dayBit(DayOfWeek.FRIDAY))
        assertTrue(schedule.isActive(LocalDateTime.of(2026, 1, 9, 12, 0)))  // Friday
        assertFalse(schedule.isActive(LocalDateTime.of(2026, 1, 10, 12, 0))) // Saturday
    }

    @Test
    fun dayBitMapsMondayToSunday() {
        assertEquals(1, GlobalSchedule.dayBit(DayOfWeek.MONDAY))
        assertEquals(16, GlobalSchedule.dayBit(DayOfWeek.FRIDAY))
        assertEquals(64, GlobalSchedule.dayBit(DayOfWeek.SUNDAY))
        assertEquals(GlobalSchedule.ALL_DAYS, (1..7).fold(0) { acc, d -> acc or GlobalSchedule.dayBit(DayOfWeek.of(d)) })
    }

    // --- AlertRules.evaluate ---------------------------------------------------

    @Test
    fun appDisabledSkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.APP_DISABLED),
            AlertRules.evaluate(baseInput().copy(appEnabled = false))
        )
    }

    @Test
    fun noMatchSkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.NO_MATCH),
            AlertRules.evaluate(baseInput().copy(contactMatched = false))
        )
    }

    @Test
    fun contactDisabledSkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.CONTACT_DISABLED),
            AlertRules.evaluate(baseInput().copy(contactEnabled = false))
        )
    }

    @Test
    fun outsideGlobalScheduleSkips() {
        val schedule = GlobalSchedule(true, TimeWindow(22 * 60, 6 * 60), GlobalSchedule.ALL_DAYS)
        assertEquals(
            AlertDecision.Skip(SkipReason.OUTSIDE_GLOBAL_SCHEDULE),
            AlertRules.evaluate(baseInput().copy(globalSchedule = schedule)) // now = 12:00
        )
    }

    @Test
    fun outsideContactWindowSkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.OUTSIDE_CONTACT_WINDOW),
            AlertRules.evaluate(baseInput().copy(contactWindow = TimeWindow(9 * 60, 10 * 60)))
        )
    }

    @Test
    fun phoneRingsNormallySkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.PHONE_RINGS_NORMALLY),
            AlertRules.evaluate(baseInput())
        )
    }

    @Test
    fun callNotRingingSkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.CALL_NOT_RINGING),
            AlertRules.evaluate(baseInput().copy(alertWhenRinging = true, callRinging = false))
        )
    }

    @Test
    fun manuallySilencedSkips() {
        assertEquals(
            AlertDecision.Skip(SkipReason.MANUALLY_SILENCED),
            AlertRules.evaluate(baseInput().copy(alertWhenRinging = true, manuallySilenced = true))
        )
    }

    @Test
    fun allConditionsPassReturnsAlert() {
        assertEquals(AlertDecision.Alert, AlertRules.evaluate(baseInput().copy(alertWhenRinging = true)))
    }

    @Test
    fun firstFailingReasonWinsInDeclarationOrder() {
        val failing = baseInput().copy(
            appEnabled = true,
            contactMatched = false,
            contactEnabled = false,
            audio = DeviceAudioState(RingerState.SILENT, interruptionFilterAll = false, ringVolumeZero = true),
            callRinging = false,
            manuallySilenced = true
        )
        assertEquals(AlertDecision.Skip(SkipReason.NO_MATCH), AlertRules.evaluate(failing))

        assertEquals(
            AlertDecision.Skip(SkipReason.APP_DISABLED),
            AlertRules.evaluate(failing.copy(appEnabled = false))
        )
        assertEquals(
            AlertDecision.Skip(SkipReason.CONTACT_DISABLED),
            AlertRules.evaluate(failing.copy(contactMatched = true))
        )
    }

    // --- AlertRules.needsSpecialAlert -----------------------------------------

    @Test
    fun needsSpecialAlertIsFalseOnlyWhenRingingNormally() {
        assertFalse(
            AlertRules.needsSpecialAlert(
                DeviceAudioState(RingerState.NORMAL, interruptionFilterAll = true, ringVolumeZero = false),
                alertWhenRinging = false
            )
        )
    }

    @Test
    fun needsSpecialAlertIsTrueForSilentModesAndZeroVolume() {
        assertTrue(AlertRules.needsSpecialAlert(DeviceAudioState(RingerState.VIBRATE, true, false), false))
        assertTrue(AlertRules.needsSpecialAlert(DeviceAudioState(RingerState.SILENT, true, false), false))
        assertTrue(AlertRules.needsSpecialAlert(DeviceAudioState(RingerState.NORMAL, false, false), false))
        assertTrue(AlertRules.needsSpecialAlert(DeviceAudioState(RingerState.NORMAL, true, true), false))
    }

    @Test
    fun needsSpecialAlertHonoursAlertWhenRingingOverride() {
        assertTrue(
            AlertRules.needsSpecialAlert(
                DeviceAudioState(RingerState.NORMAL, interruptionFilterAll = true, ringVolumeZero = false),
                alertWhenRinging = true
            )
        )
    }
}
