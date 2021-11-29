package fi.fmi.tiuha

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.*
import fi.fmi.tiuha.qc.QCDb
import fi.fmi.tiuha.qc.QCTask
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.BasicHttpContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class ApiTest : TiuhaTest() {
    val httpPort = 7766
    val api = TiuhaApi(httpPort)

    @Before
    fun beforeApiTest() {
        insertTestData()

        api.start()
    }

    @After
    fun afterApiTest() {
        api.stop()
    }

    @Test
    fun `validates bbox params`() {
        assertEquals(get("/v1/edr/collections/netatmo/items?bbox=foobar"), Response(
                status = 400,
                body = ErrorResponse("Invalid bbox")
        ))
    }

    @Test
    fun `supports bounding box search`() {
        val response = get<GeoJson<MeasurementProperties>>("/v1/edr/collections/netatmo/items?bbox=-1000.0,-1000.0,1000.0,1000.0")
        assertEquals(200, response.status)
        val body = response.body
        assertEquals("FeatureCollection", body.type)
        assertEquals(9, body.features.size)
    }

    inline fun <reified T : Any> get(url: String): Response<T> {
        val builder = URIBuilder("http://localhost:$httpPort$url")
        val request = HttpGet(builder.build())
        val response = client.execute(request, BasicHttpContext())
        val content = IOUtils.toByteArray(response.entity.content)
        val statusCode = response.getStatusLine().getStatusCode()
        val json = String(content)
        println(json)
        val body = Json.decodeFromString<T>(json)
        return Response(statusCode, body)
    }

    val client = HttpClients.createDefault()
}

data class Response<T>(val status: Int, val body: T)

fun insertTestData() {
    val db = Db(Config.dataSource)
    val s3 = LocalStackS3()

    val netatmoFakeResponses = listOf("world_data_FI.tar.gz")
    val import = NetatmoImport("FI", s3, netatmo = NetatmoClient(), null)
    netatmoFakeResponses.forEach { file ->
        val content = IOUtils.toByteArray(ClassLoader.getSystemClassLoader().getResourceAsStream(file)!!)
        import.processContent(content)
    }

    val transformTask = NetatmoGeoJsonTransform(s3)
    transformTask.processAllSync()

    val ecsClient = EcsClient.builder().apply {
        region(Region.EU_WEST_1)
    }.build()
    val qcTask = QCTask(QCDb(Config.dataSource), ecsClient, true)
    qcTask.processAllSync()
    simulateQC(db, s3)

    db.execute("""
        insert into measurement_store_import (import_s3key)
        select output_s3key from qc_task
    """, emptyList())

    val geomesaDs = S3DataStore(TestConfig.TEST_MEASUREMENTS_BUCKET)
    val geomesaImport = ImportToMeasurementStoreJob(geomesaDs, s3, TestConfig.TEST_IMPORT_BUCKET)
    geomesaImport.processAllSync()
}

data class QcTaskUpdate(val id: Long, val input: String, val output: String)

fun simulateQC(db: Db, s3: S3): List<Long> {
    val updates = db.select("select qc_task_id, input_s3key, output_s3key from qc_task", emptyList()) { rs ->
        QcTaskUpdate(rs.getLong("qc_task_id"), rs.getString("input_s3key"), rs.getString("output_s3key"))
    }
    updates.forEach {
        val geojson = s3.getObjectStream(TestConfig.TEST_IMPORT_BUCKET, it.input).use { stream -> readGzippedGeojson(stream) }
        val withQcDetails = addQCDetails(geojson)
        s3.putObject(TestConfig.TEST_IMPORT_BUCKET, it.output, gzipGeoJSON(withQcDetails))
        db.execute("update qc_task set output_s3key = ?, updated = current_timestamp where qc_task_id = ?", listOf(it.output, it.id))
    }

    return updates.map { it.id }
}

fun addQCDetails(geojson: GeoJson<QCMeasurementProperties>): GeoJson<QCMeasurementProperties> {
    val features = geojson.features.map { f->
        f.copy(properties = f.properties.copy(
                qcPassed = true,
                qcDetails = QCDetails(method = "skip", version = "test", flags = emptyList()),
        ))
    }

    return geojson.copy(features = features)
}

val gson = Gson()
fun readGzippedGeojson(stream: InputStream): GeoJson<QCMeasurementProperties> =
        JsonReader(InputStreamReader(GZIPInputStream(stream))).use { reader ->
            val type = TypeToken.getParameterized(GeoJson::class.java, QCMeasurementProperties::class.java).type
            gson.fromJson<GeoJson<QCMeasurementProperties>>(reader, type)
        }
