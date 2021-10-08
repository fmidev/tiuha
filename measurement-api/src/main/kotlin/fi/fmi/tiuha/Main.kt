package fi.fmi.tiuha

import fi.fmi.tiuha.db.SchemaMigration
import fi.fmi.tiuha.netatmo.TiuhaS3
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

    val s3 = TiuhaS3()
    val netatmo = NetatmoClient()
    val scheduledJobs = NetatmoImport.countries.map { NetatmoImport(it, s3, netatmo) }

    scheduledJobs.forEach { it.start() }
    scheduledJobs.forEach { it.await() }
}