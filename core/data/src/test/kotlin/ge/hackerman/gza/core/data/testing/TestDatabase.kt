package ge.hackerman.gza.core.data.testing

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import ge.hackerman.gza.core.data.database.GzaDatabase
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/** Databases the way the app builds them (framework driver), in memory or on a file. */
internal object TestDatabase {
    fun inMemory(context: CoroutineContext = Dispatchers.IO): GzaDatabase =
        Room.inMemoryDatabaseBuilder<GzaDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(context)
            .build()

    fun onFile(file: File, context: CoroutineContext = Dispatchers.IO): GzaDatabase =
        Room.databaseBuilder<GzaDatabase>(ApplicationProvider.getApplicationContext<Context>(), file.absolutePath)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(context)
            .build()
}
