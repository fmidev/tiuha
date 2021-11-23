package fi.fmi.tiuha

import fi.fmi.tiuha.db.runMigrations
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.*
import fi.fmi.tiuha.qc.QCDb
import fi.fmi.tiuha.qc.QCTask
import software.amazon.awssdk.services.ecs.EcsClient

fun main(args: Array<String>) {
    startServer()
}

fun startServer() {
    Log.info("Server started")
    runMigrations()

    val s3 = TiuhaS3()
    val netatmo = NetatmoClient()
    val scheduledJobs = mutableListOf<ScheduledJob>()
    val transformTask = NetatmoGeoJsonTransform(s3)
    val measurementDataStore = S3DataStore(Config.measurementsBucket)

    val ecsClient = EcsClient.builder().region(Config.awsRegion).build()
    val qcTask = QCTask(QCDb(Config.dataSource), ecsClient)
    val insertToMeasurementStoreJob = ImportToMeasurementStoreJob(measurementDataStore, s3, Config.importBucket)

    scheduledJobs.add(transformTask)
    scheduledJobs.addAll(NetatmoImport.countries.map {
        NetatmoImport(it, s3, netatmo, transformTask)
    })
    scheduledJobs.add(qcTask)
    scheduledJobs.add(insertToMeasurementStoreJob)

    scheduledJobs.forEach { it.start() }
    scheduledJobs.forEach { it.await() }
}