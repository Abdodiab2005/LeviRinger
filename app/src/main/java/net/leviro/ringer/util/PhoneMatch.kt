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

    /** Last [n] digits, ignoring '+' and formatting. */
    private fun lastDigits(raw: String, n: Int = 9): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length <= n) digits else digits.substring(digits.length - n)
    }

    /**
     * True if the two numbers refer to the same subscriber.
     * Strategy: platform compare first, then a last-9-digits fallback that survives
     * country-code and trunk-prefix differences.
     */
    fun matches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (PhoneNumberUtils.compare(a, b)) return true
        val la = lastDigits(a)
        val lb = lastDigits(b)
        return la.isNotEmpty() && la == lb
    }
}
