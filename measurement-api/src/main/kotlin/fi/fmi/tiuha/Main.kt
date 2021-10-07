package fi.fmi.tiuha

import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.SchemaMigration
import fi.fmi.tiuha.netatmo.importMeasurementsFromS3Bucket
import org.joda.time.Duration
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    if (args.contains("--import")) {
        val keysToImport = args.dropWhile { it != "--import" }.drop(1)
        importMeasurementsFromS3Bucket(keysToImport)
    } else {
        startServer()
    }
}

fun startServer() {
    Log.info("Server started")
    SchemaMigration.runMigrations()

    val netatmoImport = NetatmoImport()
    netatmoImport.start()
    netatmoImport.executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
}