package fi.fmi.tiuha

import fi.fmi.tiuha.db.SchemaMigration
import fi.fmi.tiuha.netatmo.NetatmoGeoJsonTransform
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
    val scheduledJobs = mutableListOf<ScheduledJob>()
    val transformTask = NetatmoGeoJsonTransform(s3)

    scheduledJobs.add(transformTask)
    scheduledJobs.addAll(NetatmoImport.countries.map {
        NetatmoImport(it, s3, netatmo, transformTask)
    })

    scheduledJobs.forEach { it.start() }
    scheduledJobs.forEach { it.await() }
}