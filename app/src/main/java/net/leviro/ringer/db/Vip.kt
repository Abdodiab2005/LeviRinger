package net.leviro.ringer.db

/**
 * One priority caller in the local whitelist.
 *
 * @param ringtonePath absolute path to a copied audio file in app-internal storage,
 *        or null to use the global default alarm sound.
 */
data class Vip(
    val id: Long = 0,
    val name: String,
    val number: String,
    val normalizedNumber: String,
    val ringtonePath: String? = null,
    val note: String? = null,
    val enabled: Boolean = true
)
