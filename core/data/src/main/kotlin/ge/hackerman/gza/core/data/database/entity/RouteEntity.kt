package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import ge.hackerman.gza.core.model.TransportKind

@Entity(tableName = "routes")
internal data class RouteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "short_name") val shortName: String,
    @ColumnInfo(name = "long_name_en") val longNameEn: String?,
    @ColumnInfo(name = "long_name_ka") val longNameKa: String?,
    /** 0xRRGGBB. */
    val color: Int?,
    val kind: TransportKind
)
