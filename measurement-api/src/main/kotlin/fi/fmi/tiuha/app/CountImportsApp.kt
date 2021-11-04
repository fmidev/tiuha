package fi.fmi.tiuha.app

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.db.Db

object CountImportsApp {
    val db = Db(Config.dataSource)

    @JvmStatic
    fun main(args: Array<String>): Unit = Log.time("ImportApp") {
        try {
            val importIds = db.fetchNetatmoImportIds()
            Log.info("Fetched ${importIds.size} imports")
        } finally {
            Config.dataSource.close()
        }
    }
}

fun Db.fetchNetatmoImportIds(): List<Long> {
    val sql = "select netatmoimport_id from netatmoimport"
    return select(sql, emptyList()) { it.getLong("netatmoimport_id") }
}