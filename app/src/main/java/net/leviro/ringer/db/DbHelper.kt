package net.leviro.ringer.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Raw SQLite database. No ORM, no third-party libraries. */
class DbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID            INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME          TEXT NOT NULL,
                $COL_NUMBER        TEXT NOT NULL,
                $COL_NORMALIZED    TEXT NOT NULL,
                $COL_RINGTONE      TEXT,
                $COL_NOTE          TEXT,
                $COL_ENABLED       INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_normalized ON $TABLE($COL_NORMALIZED)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First version. Future migrations go here.
    }

    companion object {
        const val DB_NAME = "leviro_ringer.db"
        const val DB_VERSION = 1

        const val TABLE = "vips"
        const val COL_ID = "_id"
        const val COL_NAME = "name"
        const val COL_NUMBER = "number"
        const val COL_NORMALIZED = "normalized"
        const val COL_RINGTONE = "ringtone"
        const val COL_NOTE = "note"
        const val COL_ENABLED = "enabled"

        @Volatile
        private var instance: DbHelper? = null

        fun get(context: Context): DbHelper =
            instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }
    }
}
