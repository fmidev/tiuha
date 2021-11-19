package fi.fmi.tiuha.measurementstore

import fi.fmi.tiuha.db.DataSource
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.getDateTime
import fi.fmi.tiuha.db.getDateTimeNullable
import java.sql.ResultSet
import java.time.ZonedDateTime

class MeasurementStoreDb(ds: DataSource) : Db(ds) {
    fun selectPendingImports(): List<MeasurementStoreImportJobRow> =
        ds.transaction {
            it.select(
                "select id, import_s3key, imported_at, created from measurement_store_import where imported_at is null order by created asc limit 5",
                emptyList(),
                MeasurementStoreImportJobRow::from
            )
        }
}

data class MeasurementStoreImportJobRow(
    val id: Long,
    val importS3Key: String,
    val importedAt: ZonedDateTime?,
    val created: ZonedDateTime,
) {
    companion object {
        fun from(rs: ResultSet) = MeasurementStoreImportJobRow(
            rs.getLong("id"),
            rs.getString("import_s3key"),
            rs.getDateTimeNullable("imported_at"),
            rs.getDateTime("created")
        )
    }
}