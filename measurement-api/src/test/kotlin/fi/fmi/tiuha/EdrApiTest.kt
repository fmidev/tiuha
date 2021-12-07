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

open class EdrApiTest : ApiTest() {
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
    fun `validates that start time is earlier than equal to end time`() {
        assertEquals(get("/v1/edr/collections/netatmo-air_temperature/cube?bbox=foobar&start=2021-01-01T00:00:00.000Z&end=1970-01-01T00:00:00.000Z"), Response(
                status = 400,
                body = ErrorResponse("Time range start is earlier than end")
        ))
    }

    @Test
    fun `validates that the time range is at most one hour`() {
        assertEquals(get("/v1/edr/collections/netatmo-air_temperature/cube?bbox=foobar&start=1970-01-01T00:00:00.000Z&end=2021-01-01T00:00:00.000Z"), Response(
                status = 400,
                body = ErrorResponse("Time ranges longer than one hour are not allowed")
        ))
    }

    @Test
    fun `supports bounding box search`() {
        val response = getGeoJson("/v1/edr/collections/netatmo-air_temperature/cube?bbox=-1000.0,-1000.0,1000.0,1000.0&start=2021-09-23T14:00:00Z&end=2021-09-23T15:00:00Z")
        assertEquals(200, response.status)
        val body = response.body
        assertEquals("FeatureCollection", body.type)
        assertEquals(2, body.features.size)
    }

    @Test
    fun `does filter by time range`() {
        val response = getGeoJson("/v1/edr/collections/netatmo-air_temperature/cube?bbox=-1000.0,-1000.0,1000.0,1000.0&start=2021-09-23T14:12:53Z&end=2021-09-23T14:12:53Z")
        assertEquals(200, response.status)
        val body = response.body
        assertEquals("FeatureCollection", body.type)
        assertEquals(1, body.features.size)
    }
}