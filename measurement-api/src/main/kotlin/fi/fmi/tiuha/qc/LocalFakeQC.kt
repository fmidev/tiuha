package fi.fmi.tiuha.qc

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import fi.fmi.tiuha.*
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.netatmo.S3
import fi.fmi.tiuha.netatmo.gzipGeoJSON
import java.io.InputStream
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream

class LocalFakeQC(private val s3: S3) : ScheduledJob("fake_qc") {
    val db = QCDb(Config.dataSource)
    private val gson = Gson()

    override fun nextFireTime(): ZonedDateTime =
        ZonedDateTime.now().plus(5, ChronoUnit.SECONDS)

    override fun exec() {
        db.getStartedTasks(limit = 100).forEach { task ->
            try {
                runFakeQC(task.id)
            } catch (e: Exception) {
                Log.error(e, "Running Fake QC task ${task.id} failed")
            }
        }
    }

    fun processAllSync() {
        val tasks = db.getStartedTasks()
        tasks.forEach { task -> runFakeQC(task.id) }
    }

    private fun runFakeQC(id: Long) = db.inTx { tx ->
        val task = db.getAndLockQCTask(tx, id)
        if (!s3.keyExists(Config.importBucket, task.outputKey!!)) {
            Log.info("Executing fake QC on qc_task $id")
            val geojson = s3.getObjectStream(Config.importBucket, task.inputKey).use { stream -> readGzippedGeojson(stream) }
            val withQcDetails = addQCDetails(geojson)
            s3.putObject(Config.importBucket, task.outputKey, gzipGeoJSON(withQcDetails))
        } else {
            Log.info("QC already executed for qc_task $id")
        }
    }

    private fun addQCDetails(geojson: GeoJson<QCMeasurementProperties>): GeoJson<QCMeasurementProperties> {
        val features = geojson.features.map { f->
            f.copy(properties = f.properties.copy(
                    qcPassed = true,
                    qcDetails = QCDetails(method = "skip", version = "test", flags = emptyList()),
            ))
        }

        return geojson.copy(features = features)
    }

    private fun readGzippedGeojson(stream: InputStream): GeoJson<QCMeasurementProperties> =
            JsonReader(InputStreamReader(GZIPInputStream(stream))).use { reader ->
                val type = TypeToken.getParameterized(GeoJson::class.java, QCMeasurementProperties::class.java).type
                gson.fromJson<GeoJson<QCMeasurementProperties>>(reader, type)
            }
}