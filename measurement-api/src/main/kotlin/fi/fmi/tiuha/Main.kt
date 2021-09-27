package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.importMeasurementsFromS3Bucket
import org.geotools.data.DataStore
import org.geotools.data.DataStoreFinder
import org.opengis.feature.simple.SimpleFeatureType

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
        val measurementSchema = getOrCreateSchema(dataStore, FEATURE_NAME, MEASUREMENT_FEATURE_TYPE)
        measurementSchema.attributeDescriptors.forEach(::println)

        val measurementReader = getMeasurementReader(dataStore)
        while (measurementReader.hasNext()) {
            val feature = measurementReader.next()
            println(feature)
        }

        dataStore.dispose()
    }
}

fun getOrCreateSchema(store: DataStore, name: String, featureType: SimpleFeatureType): SimpleFeatureType {
    val schema = store.getSchema(name)
    if (schema == null) {
        println("Schema $name did not exist, creating")
        store.createSchema(featureType)
        return featureType
    }
    return schema
}