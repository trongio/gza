package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import ge.hackerman.gza.core.model.TransportKind

/** A stop with its English and Georgian names side by side; the language is picked on read. */
@Entity(tableName = "stops", indices = [Index("code")])
internal data class StopEntity(
    @PrimaryKey val id: String,
    val code: String?,
    @ColumnInfo(name = "name_en") val nameEn: String?,
    @ColumnInfo(name = "name_ka") val nameKa: String?,
    val lat: Double,
    val lon: Double,
    val kind: TransportKind
)
