package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.geotools.data.Transaction
import org.geotools.geometry.jts.WKBReader
import org.locationtech.jts.geom.Point
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

data class MeasurandInfo(val code: String, val description: String, val unit: String)

private data class MeasurementRow(val time: LocalDateTime, val measurand: MeasurandInfo, val station: StationInfo?, val value: Float)
private data class StationInfo(val stationCode: String, val location: Point, val altitude: Double?)

private const val NETATMO_PROD_ID = "3"
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun importMeasurementsFromS3Bucket(keys: List<String>) = Log.time("NetatmoArchiveImport") {
    val s3 = ArchiveS3()
    val ds = S3DataStore()

    val measurandMapping = importMeasurandMappingFromS3Bucket(s3)
    measurandMapping.entries.forEach { Log.info("id: ${it.key}, measurand: ${it.value}") }

    val stationMapping = importStationMappingFromS3Bucket(s3)
    Log.info("I have ${stationMapping.size} stations")
    val nullStations = stationMapping.values.count { it == null }
    val stationsWithoutAltitude = stationMapping.values.count { it?.altitude == null } - nullStations
    Log.info("null: $nullStations, without alt: $stationsWithoutAltitude")

    ds.dataStore.getFeatureWriterAppend(FEATURE_NAME, Transaction.AUTO_COMMIT).use { writer ->
        keys.forEach { s3key ->
            val parser = fetchAndParseCsv(
                s3,
                s3key
            )
            var i = 0
            val start = System.currentTimeMillis()
            parser.stream().filter { it.size() > 4 }.forEach {
                i++
                if (i % 100000 == 0) {
                    val seconds = (System.currentTimeMillis() - start) / 1000.0
                    Log.info("#$i, $seconds s")
                }
                val measurand = measurandMapping.getValue(it.get("mid"))
                val value = it.get("data_value").toFloat()
                val time = LocalDateTime.parse(it.get("data_time"), DATE_TIME_FORMAT)
                val stationId = it.get("station_id")
                val station = stationMapping[stationId]
                val row = MeasurementRow(time, measurand, station, value)
                if (station == null) {
                    Log.info("missing station $row")
                } else {
                    val feat = writer.next()
                    val dtg = time.toInstant(ZoneOffset.UTC)
                    val temp = if (measurand.code == "Temperature") value else null
                    val rh = if (measurand.code == "Humidity") value else null
                    val pa = if (measurand.code == "Pressure") value else null
                    setMeasurementFeatureAttributes(
                        feat,
                        station.location,
                        dtg,
                        temp,
                        rh,
                        pa
                    )
                    writer.write()
                }
            }
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

private fun isNetatmoStation(record: CSVRecord) = record.get("prod_id") == NETATMO_PROD_ID
private fun hexEWKBToPoint(hexString: String): Point? {
    val wkbReader = WKBReader()
    val geom = wkbReader.read(WKBReader.hexToBytes(hexString))
    if (geom is Point) {
        return geom
    }
    return null
}

private fun rowToStationInfo(row: CSVRecord): StationInfo? {
    val geom = hexEWKBToPoint(row.get("geom"))
    if (geom == null) {
        return null
    }
    return StationInfo(row.get("station_code"), geom, row.get("altitude").toDoubleOrNull())
}

private fun importStationMappingFromS3Bucket(s3: S3): Map<String, StationInfo?> {
    val parser = fetchAndParseCsv(s3, "ext_station_v1.csv")
    return parser.stream()
        .filter(::isNetatmoStation)
        .collect(Collectors.toMap(
            { it.get("station_id") },
            ::rowToStationInfo
        ))
}

private fun fetchAndParseCsv(s3: S3, s3key: String, maxBytes: Long? = null): CSVParser {
    val format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
    s3.getObjectStream(Config.measurementArchiveBucket, s3key, maxBytes).use { inputStream ->
        return CSVParser.parse(inputStream, Charset.forName("UTF-8"), format)
    }
}
