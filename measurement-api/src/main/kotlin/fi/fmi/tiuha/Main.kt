package fi.fmi.tiuha

import fi.fmi.tiuha.db.runMigrations
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.*
import fi.fmi.tiuha.qc.LocalFakeQC
import fi.fmi.tiuha.qc.QCDb
import fi.fmi.tiuha.qc.QCTask
import software.amazon.awssdk.services.ecs.EcsClient
import java.lang.management.ManagementFactory

fun main(args: Array<String>) {
    Log.info("Server starting")
    logHeapMemorySize()
    runMigrations()

    val scheduledJobs = startScheduledJobs()

    Log.info("Starting HTTP server")
    val httpServer = TiuhaApi(Config.httpPort)
    httpServer.start()
    Log.info("HTTP server started")

    scheduledJobs.forEach { it.await() }
}

fun startScheduledJobs(): MutableList<ScheduledJob> {
    Log.info("Starting scheduled jobs")
    val s3 = TiuhaS3()
    val netatmo = NetatmoClient()
    val scheduledJobs = mutableListOf<ScheduledJob>()
    val transformTask = NetatmoGeoJsonTransform(s3)
    val measurementDataStore = S3DataStore(Config.measurementsBucket)

    val ecsClient = EcsClient.builder().region(Config.awsRegion).build()
    val qcTask = QCTask(QCDb(Config.dataSource), ecsClient, s3, Config.noopQualityControl)
    if (Config.noopQualityControl) {
        Log.info("Using fake QC")
        val fakeQualityControl = LocalFakeQC(s3)
        scheduledJobs.add(fakeQualityControl)
    } else {
        Log.info("Using real QC")
    }
    val insertToMeasurementStoreJob = ImportToMeasurementStoreJob(measurementDataStore, s3, Config.importBucket)

    scheduledJobs.add(transformTask)
    scheduledJobs.addAll(NetatmoImport.countries.map {
        NetatmoImport(it, s3, netatmo, transformTask)
    })
    scheduledJobs.add(qcTask)
    scheduledJobs.add(insertToMeasurementStoreJob)

    scheduledJobs.forEach { it.start() }
    Log.info("Scheduled jobs started")
    return scheduledJobs
}

fun logHeapMemorySize() {
    val mib = 1024 * 1024
    val memory = ManagementFactory.getMemoryMXBean()
    val xmx = memory.heapMemoryUsage.max / mib
    val xms = memory.heapMemoryUsage.init / mib
    Log.info("Initial memory (xms) : $xms MiB")
    Log.info("Max memory (xmx) : $xmx MiB")
}