package net.leviro.ringer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMatchTest {

    @Test
    fun normalizeKeepsOnlyLeadingPlusAndDigits() {
        assertEquals("+201001234567", PhoneMatch.normalize("+20 100-123 4567"))
        assertEquals("01001234567", PhoneMatch.normalize("0100 123 4567"))
        assertEquals("", PhoneMatch.normalize("()"))
    }

    @Test
    fun digitsMatchIgnoresCountryCodePrefix() {
        assertTrue(PhoneMatch.digitsMatch("01001234567", "+201001234567"))
        assertTrue(PhoneMatch.digitsMatch("+201001234567", "01001234567"))
    }

    @Test
    fun digitsMatchIgnoresFormatting() {
        assertTrue(PhoneMatch.digitsMatch("+20 100 123 4567", "01001234567"))
    }

    @Test
    fun digitsMatchRejectsDifferentCountryCodes() {
        assertFalse(PhoneMatch.digitsMatch("+441234567890", "+331234567890"))
    }

    @Test
    fun digitsMatchRejectsShortDifferentNumbers() {
        assertFalse(PhoneMatch.digitsMatch("12345", "912345"))
    }

    @Test
    fun digitsMatchMatchesIdenticalShortNumbers() {
        assertTrue(PhoneMatch.digitsMatch("12345", "12345"))
    }

    @Test
    fun digitsMatchNeverMatchesBlankInput() {
        assertFalse(PhoneMatch.digitsMatch("", "123456789"))
        assertFalse(PhoneMatch.digitsMatch("   ", "123456789"))
        assertFalse(PhoneMatch.digitsMatch("123456789", ""))
    }

    @Test
    fun matchesFallsBackToDigitsMatch() {
        // PhoneNumberUtils is a JVM stub, so the digits fallback is what decides here.
        assertTrue(PhoneMatch.matches("01001234567", "+201001234567"))
        assertFalse(PhoneMatch.matches("01001234567", "+441234567890"))
        assertFalse(PhoneMatch.matches("", "01001234567"))
    }
}
