package fi.fmi.tiuha.measurementstore

import fi.fmi.tiuha.*
import fi.fmi.tiuha.netatmo.TiuhaTest
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val GEOJSON_TEST_FILE = "countryweatherdata-FI.geojson"
private const val TEST_IMPORT_KEY = "import_to_measurement_store_job_test/countryweatherdata-FI.geojson.gz"

class ImportToMeasurementStoreJobTest : TiuhaTest() {

    @Test
    fun `features can be queried after import to measurement store`() {
        val key = insertTestGeoJSONToImport()
        val importId = db.selectOne("insert into measurement_store_import (import_s3key) VALUES (?) returning id", listOf(key)) {
            it.getLong(
                "id"
            )
        }

        val geomesaDs = S3DataStore(TestConfig.TEST_MEASUREMENTS_BUCKET)
        val job = ImportToMeasurementStoreJob(geomesaDs, s3, TestConfig.TEST_IMPORT_BUCKET)

        val gm = Geomesa(S3DataStore(TestConfig.TEST_MEASUREMENTS_BUCKET).dataStore)
        fun allFeatures() = gm.query("BBOX (geom, -180, -90, 180, 90)")
        assertEquals(0, allFeatures().size)

        job.exec()

        val importedAt = db.selectOne("select imported_at from measurement_store_import where id = ?", listOf(importId)) {
            it.getTimestamp("imported_at")
        }
        assertNotNull(importedAt, "Measurement store import timestamp was not set after job was completed")
        assertEquals(4, allFeatures().size)
        val airTemperatures = gm.query("property_id = 'netatmo/air_temperature'")
        assertEquals(2, airTemperatures.size)
    }

    private fun insertTestGeoJSONToImport(): String {
        val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(GEOJSON_TEST_FILE)!!
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use {
            stream.copyTo(it)
        }
        val bytes = byteArrayOutputStream.toByteArray()

        val key = TEST_IMPORT_KEY
        s3.putObject(TestConfig.TEST_IMPORT_BUCKET, key, bytes)
        return key
    }
}