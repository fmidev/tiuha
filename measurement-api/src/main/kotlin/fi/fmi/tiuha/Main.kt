package fi.fmi.tiuha

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import fi.fmi.tiuha.netatmo.importMeasurementsFromS3Bucket
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.geotools.data.DataStore
import org.geotools.data.DataStoreFinder
import org.opengis.feature.simple.SimpleFeatureType
import java.io.File
import java.io.FileReader
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun main(args: Array<String>) {
    if (args.contains("--import")) {
        val keysToImport = args.dropWhile { it != "--import" }.drop(1)
        importMeasurementsFromS3Bucket(keysToImport)
    } else {
        val dataStore = DataStoreFinder.getDataStore(
            mapOf(
                "keyspaces.keyspaceName" to "measurements",
                "keyspaces.catalog" to "testcatalog",
                "keyspaces.region" to "eu-west-1"
            )
        )

        println("Schema name: $FEATURE_NAME")
        val measurementSchema = getOrCreateSchema(dataStore, FEATURE_NAME, ::createMeasurementFeatureType)
        measurementSchema.attributeDescriptors.forEach(::println)

        val measurementReader = getMeasurementReader(dataStore)
        while (measurementReader.hasNext()) {
            val feature = measurementReader.next()
            println(feature)
        }

        dataStore.dispose()
    }
}

fun getOrCreateSchema(store: DataStore, name: String, featureTypeCreator: () -> SimpleFeatureType): SimpleFeatureType {
    val schema = store.getSchema(name)
    if (schema == null) {
        println("Schema $name did not exist, creating")
        val featureType = featureTypeCreator()
        store.createSchema(featureType)
        return featureType
    }
    return schema
}