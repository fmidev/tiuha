package fi.fmi.tiuha

import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.*
import fi.fmi.tiuha.qc.LocalFakeQC
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
    fun `requires start and end params`() {
        assertEquals(get("/v1/edr/collections/netatmo-air_temperature/cube?bbox=-1000.0,-1000.0,1000.0,1000.0"), Response(
                status = 400,
                body = ErrorResponse("start is required")
        ))
        assertEquals(get("/v1/edr/collections/netatmo-air_temperature/cube?bbox=-1000.0,-1000.0,1000.0,1000.0&start=1970-01-01T00:00:00.000Z"), Response(
                status = 400,
                body = ErrorResponse("end is required")
        ))
    }

    @Test
    fun `validates bbox params`() {
        assertEquals(get("/v1/edr/collections/netatmo-air_temperature/cube?bbox=foobar&start=1970-01-01T00:00:00.000Z&end=1970-01-01T00:00:00.000Z"), Response(
                status = 400,
                body = ErrorResponse("Invalid bbox")
        ))
    }

    @Test
    fun `supports bounding box search`() {
        val response = get<GeoJson<MeasurementProperties>>("/v1/edr/collections/netatmo-air_temperature/cube?bbox=-1000.0,-1000.0,1000.0,1000.0&start=1970-01-01T00:00:00.000Z&end=2021-12-31T23:59:59.999Z")
        assertEquals(200, response.status)
        val body = response.body
        assertEquals("FeatureCollection", body.type)
        assertEquals(2, body.features.size)
    }

    @Test
    fun `does filter by time range`() {
        val response = get<GeoJson<MeasurementProperties>>("/v1/edr/collections/netatmo-air_temperature/cube?bbox=-1000.0,-1000.0,1000.0,1000.0&start=2021-09-23T14:12:53Z&end=2021-09-23T14:12:53Z")
        assertEquals(200, response.status)
        val body = response.body
        assertEquals("FeatureCollection", body.type)
        assertEquals(1, body.features.size)
    }

    inline fun <reified T : Any> get(url: String): Response<T> {
        val builder = URIBuilder("http://localhost:$httpPort$url")
        val request = HttpGet(builder.build())
        val response = client.execute(request, BasicHttpContext())
        val json = String(IOUtils.toByteArray(response.entity.content))
        val statusCode = response.getStatusLine().getStatusCode()
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

    val ecsClient = EcsClient.builder().region(Region.EU_WEST_1).build()
    val qcTask = QCTask(QCDb(Config.dataSource), ecsClient, s3, true)
    qcTask.processAllSync()

    val localFakeQC = LocalFakeQC(s3)
    localFakeQC.processAllSync()
    qcTask.processAllSync()

    val geomesaDs = S3DataStore(Config.measurementsBucket)
    val geomesaImport = ImportToMeasurementStoreJob(geomesaDs, s3, Config.importBucket)
    geomesaImport.processAllSync()
}