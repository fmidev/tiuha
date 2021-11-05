package fi.fmi.tiuha.app

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.netatmo.NetatmoImportData

data class CountryUpdate(val importId: Long, val country: String)

object NetatmoImportCountryMigrationApp {
    val db = Db(Config.dataSource)
    val rollback = true

    @JvmStatic
    fun main(args: Array<String>): Unit = Log.time(this.javaClass.simpleName) {
        try {
            val imports = fetchNetatmoImportIdsToMigrate(db)
            Log.info("Fetched ${imports.size} imports")

            val updates = imports.map { CountryUpdate(it.id, parseCountry(it)) }

            db.inTx { tx -> countNulls(tx) }

            db.inTx { tx ->
                updates.chunked(1000).forEach { batch ->
                    Log.info("Processing ${batch.size} updates")
                    updateCountries(tx, batch)
                }
                countNulls(tx)

                if (rollback) throw RuntimeException("ROLLBACK")
            }
        } finally {
            Config.dataSource.close()
        }
    }

    fun countNulls(tx: Transaction) {
        val nullCount = tx.selectOne("select count(*) from netatmoimport where country is null", emptyList()) { it.getLong(1) }
        Log.info("NULL countries in database: ${nullCount}")
    }

    val countryFromKeyRegex = """countryweatherdata-(\w+)\.tar\.gz""".toRegex()
    fun parseCountry(import: NetatmoImportData): String {
        val result = countryFromKeyRegex.find(import.s3key)
        return result!!.groupValues[1]
    }

    fun updateCountries(tx: Transaction, updates: List<CountryUpdate>) {
        tx.batch("""
            UPDATE netatmoimport
            SET country = ?, updated = current_timestamp
            WHERE netatmoimport_id = ?
        """.trimIndent()) { batcher ->
            updates.forEach { batcher.addBatch(listOf(it.country, it.importId)) }
        }
    }

    fun fetchNetatmoImportIdsToMigrate(db: Db): List<NetatmoImportData> {
        return db.select("""
        select netatmoimport_id, country, s3bucket, s3key, geojsonkey, created, updated
        from netatmoimport where country is null
    """, emptyList()) { NetatmoImportData.from(it) }
    }
}