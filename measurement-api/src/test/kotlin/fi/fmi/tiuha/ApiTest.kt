package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.NetatmoConfig
import fi.fmi.tiuha.netatmo.TiuhaTest
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiTest : TiuhaTest() {
    val api = TiuhaApi(7766)

    @Before
    fun beforeApiTest() {
        api.start()
    }

    @After
    fun afterApiTest() {
        api.stop()
    }


    @Test
    fun `can call api`() {
        get<List<GeoJson<MeasurementProperties>>>("/").let {
            assertEquals(200, it.status)
            assertEquals(emptyList(), it.body)
        }
    }


    inline fun <reified T : Any> get(url: String): Response<T> {
        val builder = URIBuilder("http://localhost:7766$url")
        val request = HttpGet(builder.build())
        val response = client.execute(request, BasicHttpContext())
        val content = IOUtils.toByteArray(response.entity.content)
        val statusCode = response.getStatusLine().getStatusCode()
        val body = Json.decodeFromString<T>(String(content))
        return Response(statusCode, body)
    }

    val client = HttpClients.createDefault()
}

data class Response<T>(val status: Int, val body: T)