package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.db.*
import org.joda.time.DateTime
import java.sql.ResultSet

class NetatmoImportDb(ds: DataSource) : Db(ds) {
    fun insertImport(s3bucket: String, s3key: String): Long {
        return selectOne("""
            insert into netatmoimport (s3bucket, s3key)
            values (? ,?)
            returning netatmoimport_id
        """, listOf(s3bucket, s3key)) {
            it.getLong("netatmoimport_id")
        }
    }

    fun getNetatmoImportData(): List<NetatmoImportData> =
            select("""
                select netatmoimport_id, s3bucket, s3key, geojsonkey, created, updated
                from netatmoimport
            """, emptyList()) { NetatmoImportData.from(it) }

    fun getDataForGeoJSONTransform(): List<NetatmoImportData> =
            select("""
                select netatmoimport_id, s3bucket, s3key, geojsonkey, created, updated
                from netatmoimport
                where geojsonkey is null
                limit 2
            """, emptyList()) { NetatmoImportData.from(it) }

    fun insertConvertedGeoJSONEntry(tx: Transaction, netatmoImportId: Long, key: String) {
        tx.execute("update netatmoimport set geojsonkey = ? where netatmoimport_id = ?", listOf(key, netatmoImportId))
    }

    fun selectImportForProcessing(tx: Transaction, id: Long) =
            tx.selectOne("""
                select netatmoimport_id, s3bucket, s3key, geojsonkey, created, updated
                from netatmoimport
                where netatmoimport_id = ?
                for update
            """, listOf(id)) { NetatmoImportData.from(it) }
}

data class NetatmoImportData(
        val id: Long,
        val s3bucket: String,
        val s3key: String,
        val geojsonkey: String?,
        val created: DateTime,
        val updated: DateTime,
) {
    companion object {
        fun from(rs: ResultSet): NetatmoImportData = NetatmoImportData(
                id = rs.getLong("netatmoimport_id"),
                s3bucket = rs.getString("s3bucket"),
                s3key = rs.getString("s3key"),
                geojsonkey = rs.getString("geojsonkey"),
                created = rs.getDateTime("created"),
                updated = rs.getDateTime("updated"),
        )
    }
}
