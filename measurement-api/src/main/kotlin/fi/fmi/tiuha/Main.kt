package fi.fmi.tiuha

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.geotools.data.DataStoreFinder
import java.nio.charset.Charset

fun main(args: Array<String>) {
    val dataStore = DataStoreFinder.getDataStore(mapOf(
        "keyspaces.keyspaceName" to "measurements",
        "keyspaces.catalog" to "testcatalog",
        "keyspaces.region" to "eu-west-1"
    ))

    dataStore.getSchema("gdelt-quickstart").attributeDescriptors.forEach {
            attr -> println(attr.name)
    }

    importMeasurements()
}

fun importMeasurements() {
    val keys = S3.listKeys(Config.measurementArchiveBucket, "2021/")
    println(keys)
    keys.take(1).forEach { s3key ->
        val result = fetchAndParseCsv(s3key)
        result.records.forEach { println(it) }
    }
}

fun fetchAndParseCsv(s3key: String): CSVParser {
    val inputStream = S3.getObjectStream(Config.measurementArchiveBucket, s3key)
    return CSVParser.parse(inputStream, Charset.forName("UTF-8"), CSVFormat.DEFAULT)
}