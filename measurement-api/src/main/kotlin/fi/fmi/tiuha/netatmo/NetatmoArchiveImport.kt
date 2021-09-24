package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.ArchiveS3
import fi.fmi.tiuha.Config
import fi.fmi.tiuha.S3
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

data class MeasurandInfo(val code: String, val description: String, val unit: String)

private data class MeasurementRow(val time: LocalDateTime, val measurand: MeasurandInfo, val value: Double)

private const val NETATMO_PROD_ID = "3"
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun importMeasurementsFromS3Bucket(keys: List<String>) {
    val s3 = ArchiveS3()

    val measurandMapping = importMeasurandMappingFromS3Bucket(s3)
    measurandMapping.entries.forEach { println("id: ${it.key}, measurand: ${it.value}") }

    keys.forEach { s3key ->
        val parser = fetchAndParseCsv(
            s3,
            s3key,
            10000 /* let's not download the whole file just for testing*/
        )
        parser.stream().filter { it.size() > 4 }.forEach {
            val measurand = measurandMapping.getValue(it.get("mid"))
            val value = it.get("data_value")
            val time = LocalDateTime.parse(it.get("data_time"), DATE_TIME_FORMAT)
            val row = MeasurementRow(time, measurand, value.toDouble())
            println(row)
        }
    }
}

private fun isNetatmoMeasurand(measurandRecord: CSVRecord): Boolean = measurandRecord.get("prod_id") == NETATMO_PROD_ID

private fun importMeasurandMappingFromS3Bucket(s3: S3): Map<String, MeasurandInfo> {
    val parser = fetchAndParseCsv(s3, "ext_measurand_v1.csv")
    return parser.stream()
        .filter(::isNetatmoMeasurand)
        .collect(Collectors.toMap(
            { it.get("mid") },
            { MeasurandInfo(it.get("mcode"), it.get("description"), it.get("m_unit")) }
        ))
}

private fun fetchAndParseCsv(s3: S3, s3key: String, maxBytes: Long? = null): CSVParser {
    val format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
    val inputStream = s3.getObjectStream(Config.measurementArchiveBucket, s3key, maxBytes)
    return CSVParser.parse(inputStream, Charset.forName("UTF-8"), format)
}
