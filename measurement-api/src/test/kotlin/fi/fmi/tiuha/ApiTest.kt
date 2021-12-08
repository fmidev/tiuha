package fi.fmi.tiuha

import fi.fmi.tiuha.app.CreateApiClientApp.insertCredentials
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.*
import fi.fmi.tiuha.qc.LocalFakeQC
import fi.fmi.tiuha.qc.QCDb
import fi.fmi.tiuha.qc.QCTask
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.IOUtils
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClients
import org.junit.After
import org.junit.Before
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient

data class ApiCredentials(
        val clientId: String,
        val apiKey: String,
)

abstract class ApiTest : TiuhaTest() {
    val httpPort = 7766
    val geomesaDs = S3DataStore(Config.measurementsBucket)
    val api = TiuhaApi(httpPort, geomesaDs)
    val testCredentials = ApiCredentials("testclient", "password123")
    val http = HttpClients.createDefault()

    @Before
    fun beforeApiTest() {
        db.inTx { tx -> insertCredentials(tx, testCredentials.clientId, testCredentials.apiKey) }
        insertTestData()

        api.start()
    }

    @After
    fun afterApiTest() {
        api.stop()
    }

    fun getGeoJson(url: String, creds: ApiCredentials? = testCredentials): Response<GeoJson<MeasurementProperties>> = get(url, creds = creds)

    inline fun <reified T : Any> get(url: String, creds: ApiCredentials? = testCredentials): Response<T> {
        val context = HttpClientContext.create()
        if (creds != null) {
            val provider = BasicCredentialsProvider()
            provider.setCredentials(AuthScope.ANY, UsernamePasswordCredentials(creds.clientId, creds.apiKey))
            context.setCredentialsProvider(provider)
            context.setAuthCache(BasicAuthCache().apply {
                put(HttpHost("localhost", httpPort, "http"), BasicScheme())
            })
        }

        val builder = URIBuilder("http://localhost:$httpPort$url")
        val request = HttpGet(builder.build())
        val response = http.execute(request, context)
        val json = String(IOUtils.toByteArray(response.entity.content))
        val statusCode = response.getStatusLine().getStatusCode()
        val body = Json.decodeFromString<T>(json)
        return Response(statusCode, body)
    }
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