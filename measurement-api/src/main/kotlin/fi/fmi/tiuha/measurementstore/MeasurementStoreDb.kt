package fi.fmi.tiuha.measurementstore

import fi.fmi.tiuha.db.*
import java.sql.ResultSet
import java.time.ZonedDateTime

class MeasurementStoreDb(ds: DataSource) : Db(ds) {
    fun listPendingImports(limit: Long? = null): List<Long> {
        val (limitSql, limitParams) = when (limit) {
            null -> Pair("", emptyList())
            else -> Pair("limit ?", listOf(limit))
        }

        return select("""
            select id, import_s3key, imported_at, created
            from measurement_store_import
            where imported_at is null
            order by created asc
            $limitSql
        """, limitParams) { it.getLong("id") }
    }

    fun selectImportForProcessing(tx: Transaction, id: Long) =
        tx.selectOne("""
            select id, import_s3key, imported_at, created
            from measurement_store_import
            where id = ?
            for update
        """.trimIndent(), listOf(id), MeasurementStoreImportJobRow::from)

    fun updateImportComplete(tx: Transaction, id: Long) =
        tx.execute("""
            update measurement_store_import
            set imported_at = current_timestamp at time zone 'UTC'
            where id = ?
        """.trimIndent(), listOf(id))
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