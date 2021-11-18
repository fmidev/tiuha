package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.*
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.SchemaMigration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.IOUtils
import org.geotools.data.Transaction
import org.junit.Before
import org.locationtech.jts.geom.GeometryFactory
import java.util.zip.GZIPInputStream

abstract class TiuhaTest {
    open val db: Db = Db(Config.dataSource)
    val s3 = LocalStackS3()

    @Before
    fun before() {
        clearDb()
        clearBucket(s3, Config.importBucket)
        clearBucket(s3, Config.measurementsBucket)
    }

    fun clearDb() {
        listOf(
                "scheduledjob",
                "netatmoimport",
                "schemaversion",
                "qc_task",
        ).forEach { db.execute("DROP TABLE IF EXISTS $it", emptyList()) }
        SchemaMigration.runMigrations()
    }

    fun insertTestData() {
        val db = Db(Config.dataSource)
        val s3 = LocalStackS3()

        val transformTask = NetatmoGeoJsonTransform(s3)
        val import = NetatmoImport("FI", s3, netatmo = NetatmoClient(), null)

        val netatmoFakeResponses = listOf(
                "world_data_FI.tar.gz",
        )
        netatmoFakeResponses.forEach { file ->
            val content = IOUtils.toByteArray(ClassLoader.getSystemClassLoader().getResourceAsStream(file)!!)
            import.processContent(content)
        }

        val importIds = db.select("select netatmoimport_id from netatmoimport", emptyList()) { it.getLong(1) }
        importIds.map { transformTask.attemptTransform(it) }.forEach { it.get() }

        val keys = db.select("select geojsonkey from netatmoimport", emptyList()) { it.getString(1) }
        importGeoJsonBatch(s3, keys)
    }
}

private fun clearBucket(s3: S3, bucket: String) =
        s3.listKeys(bucket).forEach { s3.deleteObject(bucket, it) }

private fun readGeoJSON(s3: S3, key: String): GeoJson<MeasurementProperties> {
    val json = s3.getObjectStream(Config.importBucket, key).use { stream ->
        IOUtils.toString(GZIPInputStream(stream))
    }
    return Json.decodeFromString(json)
}

private fun addQCFields(input: GeoJson<MeasurementProperties>): GeoJson<QCMeasurementProperties> {
    return GeoJson(
        type = input.type,
        features = input.features.map { GeoJsonFeature(
            it.type,
            it.geometry,
            QCMeasurementProperties.from(it.properties, true, QCDetails("titan", "v1", emptyList()))
        ) }
    )
}

private fun importGeoJsonBatch(s3: S3, keys: List<String>) = Log.time("ImportGeoJsonBatch") {
    val geometryFactory = GeometryFactory()
    val ds = S3DataStore()

    val geojsons = keys.map { key -> readGeoJSON(s3, key) }.map(::addQCFields)

    ds.dataStore.getFeatureWriterAppend(FEATURE_NAME, Transaction.AUTO_COMMIT).use { writer ->
        geojsons.flatMap { it.features }.forEach { f ->
            val feat = writer.next()
            setMeasurementFeatureAttributes(feat, geometryFactory, f)
            writer.write()
        }
    }
}
