package fi.fmi.tiuha

import org.junit.Test
import kotlin.test.assertEquals

class AuthenticationTest : ApiTest() {
    val testUrl = "/v1/edr/collections/netatmo-air_temperature/cube?bbox=-10.0,-10.0,10.0,10.0&start=1970-01-01T00:00:00.000Z&end=1970-01-01T00:00:00.000Z"

    @Test
    fun `denies access without credentials`() {
        assertEquals(get(testUrl, creds = null), Response(
                status = 401,
                body = ErrorResponse("Unauthorized")
        ))
    }

    @Test
    fun `denies access with invalid credentials`() {
        assertEquals(get(testUrl, ApiCredentials("invalidclient", "invalidpassword")), Response(
                status = 401,
                body = ErrorResponse("Unauthorized")
        ))
    }

    @Test
    fun `denies access without correct clientId but invalid password`() {
        assertEquals(get(testUrl, creds = testCredentials.copy(apiKey = "invalidapikey")), Response(
                status = 401,
                body = ErrorResponse("Unauthorized")
        ))
    }

    @Test
    fun `allows access with valid credentials`() {
        val response = getGeoJson(testUrl, creds = testCredentials)
        assertEquals(200, response.status)
    }
}