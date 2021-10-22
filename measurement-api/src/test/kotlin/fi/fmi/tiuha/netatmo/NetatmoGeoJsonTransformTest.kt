package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.IOUtils
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetatmoGeoJsonTransformTest : TiuhaTest() {
    val job = NetatmoGeoJsonTransform(fakeS3)

    @Test
    fun `can be executed without imports`() {
        job.exec()
    }

    @Test
    fun `updates netatmoimport state in database and S3`() {
        assertEquals(0, 0)
        val importId = insertImport("netatmo/19700101000000/countryweatherdata-FI.tar.gz", "world_data_FI.tar.gz")

        val before = getImport(importId)
        assertNull(before.geojsonkey)

        assertEquals(1, countUnprocessed())
        assertEquals(0, countProcessed())
        job.exec()
        assertEquals(0, countUnprocessed())
        assertEquals(1, countProcessed())

        val after = getImport(importId)
        assertEquals("netatmo/19700101000000/countryweatherdata-FI.geojson.gz", after.geojsonkey)
    }

    @Test
    fun `converts netatmo response to GeoJSON`() {
        val importId = insertImport("netatmo/19700101000000/countryweatherdata-FI.tar.gz", "world_data_FI.tar.gz")
        job.exec()
        val import = getImport(importId)

        val result = readGeoJSON(import)
        assertEquals(5, result.features.size)
        val expected = GeoJson(
                type = "FeatureCollection",
                features = listOf(
                        GeoJsonFeature(
                                type = "Feature",
                                geometry = Geometry(type = "Point", coordinates = listOf(24.9607611, 60.2037551, 30.0)),
                                properties = FeatureProperties(
                                        _id = "enc:16:testIdDynamicum",
                                        featureType = "MeasureObservation",
                                        resultTime = "2021-09-23T14:12:53Z",
                                        observedPropertyTitle = "Air temperature",
                                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0023/",
                                        unitOfMeasureTitle = "C",
                                        unitOfMeasure = "http://www.opengis.net/def/uom/UCUM/degC",
                                        result = 5.8,
                                )
                        ),
                        GeoJsonFeature(
                                type = "Feature",
                                geometry = Geometry(type = "Point", coordinates = listOf(24.9607611, 60.2037551, 30.0)),
                                properties = FeatureProperties(
                                        _id = "enc:16:testIdDynamicum",
                                        featureType = "MeasureObservation",
                                        resultTime = "2021-09-23T14:12:53Z",
                                        observedPropertyTitle = "Relative Humidity",
                                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0413/",
                                        unitOfMeasureTitle = "%",
                                        unitOfMeasure = "",
                                        result = 65.0,
                                )
                        ),

                        GeoJsonFeature(
                                type = "Feature",
                                geometry = Geometry(type = "Point", coordinates = listOf(24.9486983, 60.1696741, 5.0)),
                                properties = FeatureProperties(
                                        _id = "enc:16:testIdYk4",
                                        featureType = "MeasureObservation",
                                        resultTime = "2021-09-23T14:16:15Z",
                                        observedPropertyTitle = "Air temperature",
                                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0023/",
                                        unitOfMeasureTitle = "C",
                                        unitOfMeasure = "http://www.opengis.net/def/uom/UCUM/degC",
                                        result = 6.5,
                                )
                        ),
                        GeoJsonFeature(
                                type = "Feature",
                                geometry = Geometry(type = "Point", coordinates = listOf(24.9486983, 60.1696741, 5.0)),
                                properties = FeatureProperties(
                                        _id = "enc:16:testIdYk4",
                                        featureType = "MeasureObservation",
                                        resultTime = "2021-09-23T14:16:15Z",
                                        observedPropertyTitle = "Relative Humidity",
                                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0413/",
                                        unitOfMeasureTitle = "%",
                                        unitOfMeasure = "",
                                        result = 68.0,
                                )
                        ),
                        GeoJsonFeature(
                                type = "Feature",
                                geometry = Geometry(type = "Point", coordinates = listOf(24.9486983, 60.1696741, 5.0)),
                                properties = FeatureProperties(
                                        _id = "enc:16:testIdYk4",
                                        featureType = "MeasureObservation",
                                        resultTime = "2021-09-23T14:16:15Z",
                                        observedPropertyTitle = "Air Pressure",
                                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0015/",
                                        unitOfMeasureTitle = "mbar",
                                        unitOfMeasure = "",
                                        result = 1006.1,
                                )
                        )
                )
        )
        assertEquals(expected, result)
    }

    fun readGeoJSON(import: NetatmoImportData): GeoJson {
        val json = fakeS3.getObjectStream(import.s3bucket, import.geojsonkey!!).use { stream ->
            IOUtils.toString(GZIPInputStream(stream))
        }
        return Json.decodeFromString(json)
    }

    fun insertImport(s3key: String, resourceFile: String): Long {
        fakeS3.putObjectFromResources(Config.importBucket, s3key, resourceFile)
        return db.insertImport(Config.importBucket, s3key)
    }

    fun getImport(id: Long) = db.inTx { tx -> db.selectImportForProcessing(tx, id) }

    fun countUnprocessed() = countImports("geojsonkey is null")
    fun countProcessed() = countImports("geojsonkey is not null")
    fun countImports(filter: String) = db.selectOne("select count(*) from netatmoimport where $filter", emptyList()) { it.getLong(1) }
}