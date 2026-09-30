package ge.hackerman.gza.core.data.testing

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import ge.hackerman.gza.core.data.database.GzaDatabase

/**
 * Makes SQLite itself fail while inserting the row [id] into [table], the way a full disk or
 * an I/O error fails half way through a transaction: after the deletes already ran. The driver
 * throws a real `android.database.sqlite.SQLiteException` subclass.
 */
internal suspend fun GzaDatabase.failInsertOf(table: String, id: String, column: String = "id") = useWriterConnection {
    it.executeSQL(
        "CREATE TRIGGER fail_insert_$table BEFORE INSERT ON $table WHEN NEW.$column = '$id' " +
            "BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END"
    )
}

internal suspend fun GzaDatabase.stopFailingInsertsInto(table: String) = useWriterConnection {
    it.executeSQL("DROP TRIGGER IF EXISTS fail_insert_$table")
}
