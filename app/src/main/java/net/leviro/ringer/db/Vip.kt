package net.leviro.ringer.db

/**
 * One priority caller in the local whitelist.
 *
 * @param ringtonePath absolute path to a copied audio file in app-internal storage,
 *        or null to use the global default alarm sound.
 * @param vibrateOnly vibrate without sound for this caller.
 * @param startTime / [endTime] optional "HH:mm" window when this caller may trigger the alert;
 *        both null means any time. A start later than the end crosses midnight.
 */
data class Vip(
    val id: Long = 0,
    val name: String,
    val number: String,
    val normalizedNumber: String,
    val ringtonePath: String? = null,
    val note: String? = null,
    val enabled: Boolean = true,
    val vibrateOnly: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null
)
