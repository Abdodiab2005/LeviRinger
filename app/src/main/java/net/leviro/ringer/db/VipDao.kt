package net.leviro.ringer.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import net.leviro.ringer.util.PhoneMatch

/** All read/write access to the whitelist table. */
class VipDao(context: Context) {

    private val helper = DbHelper.get(context)

    fun insert(vip: Vip): Long {
        val values = vip.toContentValues()
        return helper.writableDatabase.insert(DbHelper.TABLE, null, values)
    }

    fun update(vip: Vip): Int {
        val values = vip.toContentValues()
        return helper.writableDatabase.update(
            DbHelper.TABLE, values, "${DbHelper.COL_ID}=?", arrayOf(vip.id.toString())
        )
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply {
            put(DbHelper.COL_ENABLED, if (enabled) 1 else 0)
        }
        helper.writableDatabase.update(
            DbHelper.TABLE, values, "${DbHelper.COL_ID}=?", arrayOf(id.toString())
        )
    }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(DbHelper.TABLE, "${DbHelper.COL_ID}=?", arrayOf(id.toString()))

    fun getAll(): List<Vip> {
        val list = ArrayList<Vip>()
        helper.readableDatabase.query(
            DbHelper.TABLE, null, null, null, null, null, "${DbHelper.COL_NAME} COLLATE NOCASE ASC"
        ).use { c ->
            while (c.moveToNext()) list.add(c.toVip())
        }
        return list
    }

    /**
     * Returns the enabled VIP whose stored number matches [incomingNumber], or null.
     * Matching tolerates country-code / formatting differences (see [PhoneMatch]).
     */
    fun findMatch(incomingNumber: String?): Vip? {
        if (incomingNumber.isNullOrBlank()) return null
        val incomingNorm = PhoneMatch.normalize(incomingNumber)

        // Fast path: exact normalized hit on the index.
        helper.readableDatabase.query(
            DbHelper.TABLE, null,
            "${DbHelper.COL_NORMALIZED}=? AND ${DbHelper.COL_ENABLED}=1",
            arrayOf(incomingNorm), null, null, null
        ).use { c ->
            if (c.moveToFirst()) return c.toVip()
        }

        // Fallback: tolerant comparison against every enabled row (handles +20 vs 0 prefixes).
        helper.readableDatabase.query(
            DbHelper.TABLE, null, "${DbHelper.COL_ENABLED}=1", null, null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                val vip = c.toVip()
                if (PhoneMatch.matches(incomingNumber, vip.number)) return vip
            }
        }
        return null
    }

    private fun Vip.toContentValues() = ContentValues().apply {
        put(DbHelper.COL_NAME, name)
        put(DbHelper.COL_NUMBER, number)
        put(DbHelper.COL_NORMALIZED, normalizedNumber)
        put(DbHelper.COL_RINGTONE, ringtonePath)
        put(DbHelper.COL_NOTE, note)
        put(DbHelper.COL_ENABLED, if (enabled) 1 else 0)
        put(DbHelper.COL_VIBRATE_ONLY, if (vibrateOnly) 1 else 0)
        put(DbHelper.COL_START_TIME, startTime)
        put(DbHelper.COL_END_TIME, endTime)
        put(DbHelper.COL_VOLUME, volumePercent)
        put(DbHelper.COL_DELAY, delaySeconds)
        put(DbHelper.COL_RING_ALSO, alertWhenRinging?.let { if (it) 1 else 0 })
        put(DbHelper.COL_SHOW_CARD, showCard?.let { if (it) 1 else 0 })
        put(DbHelper.COL_BOOST, boostAlarmVolume?.let { if (it) 1 else 0 })
    }

    private fun Cursor.toVip(): Vip = Vip(
        id = getLong(getColumnIndexOrThrow(DbHelper.COL_ID)),
        name = getString(getColumnIndexOrThrow(DbHelper.COL_NAME)),
        number = getString(getColumnIndexOrThrow(DbHelper.COL_NUMBER)),
        normalizedNumber = getString(getColumnIndexOrThrow(DbHelper.COL_NORMALIZED)),
        ringtonePath = getString(getColumnIndexOrThrow(DbHelper.COL_RINGTONE)),
        note = getString(getColumnIndexOrThrow(DbHelper.COL_NOTE)),
        enabled = getInt(getColumnIndexOrThrow(DbHelper.COL_ENABLED)) == 1,
        vibrateOnly = getInt(getColumnIndexOrThrow(DbHelper.COL_VIBRATE_ONLY)) == 1,
        startTime = getString(getColumnIndexOrThrow(DbHelper.COL_START_TIME)),
        endTime = getString(getColumnIndexOrThrow(DbHelper.COL_END_TIME)),
        volumePercent = getIntOrNull(DbHelper.COL_VOLUME),
        delaySeconds = getIntOrNull(DbHelper.COL_DELAY),
        alertWhenRinging = getIntOrNull(DbHelper.COL_RING_ALSO)?.let { it == 1 },
        showCard = getIntOrNull(DbHelper.COL_SHOW_CARD)?.let { it == 1 },
        boostAlarmVolume = getIntOrNull(DbHelper.COL_BOOST)?.let { it == 1 }
    )

    private fun Cursor.getIntOrNull(column: String): Int? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }
}
