package fi.fmi.tiuha

import fi.fmi.tiuha.db.SchemaMigration
import fi.fmi.tiuha.netatmo.importMeasurementsFromS3Bucket

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

    val scheduledJobs = NetatmoImport.countries.map { NetatmoImport(it) }

    scheduledJobs.forEach { it.start() }
    scheduledJobs.forEach { it.await() }
}