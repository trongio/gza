package ge.hackerman.gza.core.data.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import ge.hackerman.gza.core.data.database.entity.PatternEntity
import ge.hackerman.gza.core.data.database.entity.PatternStopEntity
import ge.hackerman.gza.core.data.database.entity.PolylineEntity
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.SchedulePeriodEntity
import ge.hackerman.gza.core.data.database.entity.ScheduleStopTimesEntity
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.model.TransportKind

/** Everything one route sync writes, built in memory before the transaction starts. */
internal data class RouteDataRows(
    val patterns: List<PatternEntity>,
    val patternStops: List<PatternStopEntity>,
    val polylines: List<PolylineEntity>,
    val periods: List<SchedulePeriodEntity>,
    val stopTimes: List<ScheduleStopTimesEntity>,
    /** Stops named by the pattern responses, inserted only when the catalog lacks them. */
    val stopsFromPatterns: List<StopEntity>,
    /** The route row from its detail, inserted only when the catalog lacks it. */
    val routeFromDetail: RouteEntity?
)

/** A pattern stop joined with the stops table; the stop columns are null when it is missing. */
internal data class PatternStopRow(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    val seq: Int,
    @ColumnInfo(name = "stop_id") val stopId: String,
    val code: String?,
    @ColumnInfo(name = "name_en") val nameEn: String?,
    @ColumnInfo(name = "name_ka") val nameKa: String?,
    val lat: Double?,
    val lon: Double?,
    val kind: TransportKind?
)

/** One consistent read of a route's cached data. */
internal data class RouteDataSnapshot(
    val route: RouteEntity?,
    val patterns: List<PatternEntity>,
    val patternStops: List<PatternStopRow>,
    val polylines: List<PolylineEntity>
)

@Dao
@Suppress("TooManyFunctions") // One delete, insert and read per table of a route.
internal abstract class RouteDataDao {
    @Query("SELECT * FROM patterns WHERE route_id = :routeId ORDER BY suffix")
    abstract suspend fun getPatterns(routeId: String): List<PatternEntity>

    @Query("SELECT COUNT(*) FROM patterns WHERE route_id = :routeId")
    abstract suspend fun countPatterns(routeId: String): Int

    @Query("SELECT * FROM pattern_stops WHERE route_id = :routeId AND suffix = :suffix ORDER BY seq")
    abstract suspend fun getPatternStops(routeId: String, suffix: String): List<PatternStopEntity>

    @Query(
        """
        SELECT ps.route_id, ps.suffix, ps.seq, ps.stop_id, s.code, s.name_en, s.name_ka, s.lat, s.lon, s.kind
        FROM pattern_stops ps LEFT JOIN stops s ON s.id = ps.stop_id
        WHERE ps.route_id = :routeId
        ORDER BY ps.suffix, ps.seq
        """
    )
    abstract suspend fun getPatternStopRows(routeId: String): List<PatternStopRow>

    @Query("SELECT * FROM polylines WHERE route_id = :routeId ORDER BY suffix")
    abstract suspend fun getPolylines(routeId: String): List<PolylineEntity>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    abstract suspend fun getRoute(routeId: String): RouteEntity?

    @Query("SELECT * FROM schedule_periods WHERE route_id = :routeId AND suffix = :suffix ORDER BY period_index")
    abstract suspend fun getSchedulePeriods(routeId: String, suffix: String): List<SchedulePeriodEntity>

    @Query(
        "SELECT * FROM schedule_stop_times WHERE route_id = :routeId AND suffix = :suffix ORDER BY period_index, seq"
    )
    abstract suspend fun getScheduleStopTimes(routeId: String, suffix: String): List<ScheduleStopTimesEntity>

    /** Patterns, stops in order and shapes read together, so they always belong to one sync. */
    @Transaction
    open suspend fun loadRouteData(routeId: String): RouteDataSnapshot = RouteDataSnapshot(
        route = getRoute(routeId),
        patterns = getPatterns(routeId),
        patternStops = getPatternStopRows(routeId),
        polylines = getPolylines(routeId)
    )

    @Query("DELETE FROM patterns WHERE route_id = :routeId")
    abstract suspend fun deletePatterns(routeId: String)

    @Query("DELETE FROM pattern_stops WHERE route_id = :routeId")
    abstract suspend fun deletePatternStops(routeId: String)

    @Query("DELETE FROM polylines WHERE route_id = :routeId")
    abstract suspend fun deletePolylines(routeId: String)

    @Query("DELETE FROM schedule_periods WHERE route_id = :routeId")
    abstract suspend fun deleteSchedulePeriods(routeId: String)

    @Query("DELETE FROM schedule_stop_times WHERE route_id = :routeId")
    abstract suspend fun deleteScheduleStopTimes(routeId: String)

    @Insert
    abstract suspend fun insertPatterns(rows: List<PatternEntity>)

    @Insert
    abstract suspend fun insertPatternStops(rows: List<PatternStopEntity>)

    @Insert
    abstract suspend fun insertPolylines(rows: List<PolylineEntity>)

    @Insert
    abstract suspend fun insertSchedulePeriods(rows: List<SchedulePeriodEntity>)

    @Insert
    abstract suspend fun insertScheduleStopTimes(rows: List<ScheduleStopTimesEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertStopsIfAbsent(rows: List<StopEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRouteIfAbsent(row: RouteEntity)

    @Upsert
    abstract suspend fun upsertSyncState(state: SyncStateEntity)

    /**
     * Replaces everything cached for [routeId] in one transaction: any failure (a constraint,
     * a full disk) rolls it all back and the previous data stays, and readers never see a new
     * stop order with an old timetable.
     */
    @Transaction
    open suspend fun replaceRouteData(routeId: String, data: RouteDataRows, syncState: SyncStateEntity) {
        deletePatterns(routeId)
        deletePatternStops(routeId)
        deletePolylines(routeId)
        deleteSchedulePeriods(routeId)
        deleteScheduleStopTimes(routeId)
        insertPatterns(data.patterns)
        insertPatternStops(data.patternStops)
        insertPolylines(data.polylines)
        insertSchedulePeriods(data.periods)
        insertScheduleStopTimes(data.stopTimes)
        insertStopsIfAbsent(data.stopsFromPatterns)
        data.routeFromDetail?.let { insertRouteIfAbsent(it) }
        upsertSyncState(syncState)
    }
}
