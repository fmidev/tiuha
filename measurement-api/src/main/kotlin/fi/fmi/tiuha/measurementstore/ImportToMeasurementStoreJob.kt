package fi.fmi.tiuha.measurementstore

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import fi.fmi.tiuha.*
import fi.fmi.tiuha.netatmo.S3
import org.locationtech.jts.geom.GeometryFactory
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream

class ImportToMeasurementStoreJob(private val ds: S3DataStore, private val s3: S3, private val importBucket: String) : ScheduledJob("insert_to_measurement_store") {
    private val db = MeasurementStoreDb(Config.dataSource)

    override fun nextFireTime() = ZonedDateTime.now().plus(10, ChronoUnit.MINUTES)

    override fun exec() {
        val importIds = db.listPendingImports()
        Log.info("Importing ${importIds.size} batches to measurement store")
        importIds.forEach(::importBatch)
    }

    private fun importBatch(id: Long) {
        db.inTx { tx ->
            val row = db.selectImportForProcessing(tx, id)
            if (row.importedAt != null) {
                Log.info("${row.importS3Key} already imported to measurement store")
            } else {
                Log.info("Importing ${row.importS3Key} to measurement store")
                val gson = Gson()
                s3.getObjectStream(importBucket, row.importS3Key).use { stream ->
                    val inflatedStream = GZIPInputStream(stream)
                    JsonReader(InputStreamReader(inflatedStream)).use { reader ->
                        val type =
                            TypeToken.getParameterized(GeoJson::class.java, QCMeasurementProperties::class.java).type
                        val geoJson = gson.fromJson<GeoJson<QCMeasurementProperties>>(reader, type)
                        writeFeatures(geoJson.features, row.id)
                    }
                }
                db.updateImportComplete(tx, id)
            }
        }
    }

    private fun writeFeatures(features: List<GeoJsonQCFeature>, importId: Long) {
        val geometryFactory = GeometryFactory()

        ds.getMeasurementFeatureWriter().use { writer ->
            features.filter { it.properties.qcPassed }.forEach { json ->
                val feat = writer.next()
                try {
                    setMeasurementFeatureAttributes(feat, geometryFactory, json, importId)
                    writer.write()
                } catch (e: Exception) {
                    Log.error(e, "Failed to set attributes for feature: $json")
                }
            }
        }
    }
}