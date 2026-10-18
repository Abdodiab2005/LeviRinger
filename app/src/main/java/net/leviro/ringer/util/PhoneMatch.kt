package net.leviro.ringer.util

import android.telephony.PhoneNumberUtils

/**
 * Robust phone-number matching. Handles the common case where the stored number
 * is "01001234567" but the incoming call presents "+201001234567" (or vice-versa).
 */
object PhoneMatch {

    /** Digits only (keeps a leading +). Used as the stored normalized key. */
    fun normalize(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            if (ch.isDigit()) sb.append(ch)
            else if (ch == '+' && sb.isEmpty()) sb.append('+')
        }
        return sb.toString()
    }

    /**
     * True if the two numbers refer to the same subscriber.
     * Strategy: platform compare first, then [digitsMatch] as a pure fallback.
     */
    fun matches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (PhoneNumberUtils.compare(a, b)) return true
        return digitsMatch(a, b)
    }

    /**
     * Digits-only comparison that survives formatting and trunk-prefix differences.
     * When both values carry a leading '+', their digit strings must be identical
     * (so different country codes never collapse into one another). Otherwise both
     * need at least 7 digits and are compared by their last 9 digits, or by full
     * equality when either is shorter than 9 digits. Blank input never matches.
     */
    fun digitsMatch(a: String, b: String): Boolean {
        val sa = a.trim()
        val sb = b.trim()
        if (sa.isEmpty() || sb.isEmpty()) return false
        val da = sa.filter { it.isDigit() }
        val db = sb.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        if (sa.startsWith('+') && sb.startsWith('+')) return da == db
        if (da == db) return true
        if (da.length < 7 || db.length < 7) return false
        if (da.length < 9 || db.length < 9) return false
        return da.takeLast(9) == db.takeLast(9)
    }
}
