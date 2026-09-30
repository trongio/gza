package ge.hackerman.gza.core.data.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import ge.hackerman.gza.core.data.database.dao.RouteDao
import ge.hackerman.gza.core.data.database.dao.RouteDataDao
import ge.hackerman.gza.core.data.database.dao.ScheduleDao
import ge.hackerman.gza.core.data.database.dao.StopDao
import ge.hackerman.gza.core.data.database.dao.StopRoutesDao
import ge.hackerman.gza.core.data.database.dao.SyncStateDao
import ge.hackerman.gza.core.data.database.entity.PatternEntity
import ge.hackerman.gza.core.data.database.entity.PatternStopEntity
import ge.hackerman.gza.core.data.database.entity.PolylineEntity
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.SchedulePeriodEntity
import ge.hackerman.gza.core.data.database.entity.ScheduleStopTimesEntity
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.data.database.entity.StopRouteEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity

/**
 * The transit cache. Everything here can be fetched again from the gateway; the user's own
 * data lives in DataStore. No foreign keys on purpose: route data can arrive before the
 * catalog, and cascades would silently delete children. The replace transactions in the DAOs
 * keep it consistent instead.
 */
@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        StopRouteEntity::class,
        PatternEntity::class,
        PatternStopEntity::class,
        PolylineEntity::class,
        SchedulePeriodEntity::class,
        ScheduleStopTimesEntity::class,
        SyncStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
internal abstract class GzaDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun stopRoutesDao(): StopRoutesDao
    abstract fun routeDataDao(): RouteDataDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME: String = "gza.db"
    }
}
