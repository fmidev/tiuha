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

    if (Config.importMeasurements) {
        val s3 = ArchiveS3()
        importMeasurements(s3)
    }
}

fun importMeasurements(s3: S3) {
    val keys = s3.listKeys(Config.measurementArchiveBucket, "2021/w23/2021_w23_prod_id_4.csv")
    println(keys)
    keys.take(1).forEach { s3key ->
        val result = fetchAndParseCsv(s3, s3key)
        result.records.forEach { println(it) }
    }
}

fun fetchAndParseCsv(s3: S3, s3key: String): CSVParser {
    val inputStream = s3.getObjectStream(Config.measurementArchiveBucket, s3key)
    return CSVParser.parse(inputStream, Charset.forName("UTF-8"), CSVFormat.DEFAULT)
}