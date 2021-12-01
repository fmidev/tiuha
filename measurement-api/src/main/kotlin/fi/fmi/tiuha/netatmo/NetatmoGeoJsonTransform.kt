package fi.fmi.tiuha.netatmo

import com.google.gson.Gson
import fi.fmi.tiuha.*
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.qc.QCTask
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val SOURCE_ID = "netatmo"

class NetatmoGeoJsonTransform(val s3: S3) : ScheduledJob("netatmogeojsontransform") {
    val transformExecutor = Executors.newFixedThreadPool(1)

    val db = NetatmoImportDb(Config.dataSource)

    override fun nextFireTime(): ZonedDateTime =
            ZonedDateTime.now().plus(1, ChronoUnit.MINUTES)

    override fun exec() = Log.time("NetatmoGeoJsonTransform") {
        val datas = db.getDataForGeoJSONTransform(limit = 100)
        datas.map {
            attemptTransform(it.id)
        }.forEach {
            try {
                it.get()
            } catch (e: ExecutionException) {
                val cause = e.cause ?: UnknownError()
                Log.error(cause, "Failed to transform Netatmo data to GeoJSON")
            }
        }
    }

    fun attemptTransform(importId: Long) =
            transformExecutor.submit(Callable { process(importId) })

    fun processAllSync() {
        val datas = db.getDataForGeoJSONTransform()
        datas.forEach { process(it.id) }
    }

    fun process(id: Long) {
        db.inTx { tx ->
            val import = db.selectImportForProcessing(tx, id)
            if (import.geojsonkey != null) {
                Log.info("Import ${import.id} (${import.s3key} already processed")
            } else {
                val geojson = s3.getObjectStream(import.s3bucket, import.s3key).use { stream ->
                    convert(parseNetatmoData(stream))
                }

                val bucket = Config.importBucket
                val key = import.s3key.replace(".tar.gz", ".geojson.gz")
                s3.putObject(bucket, key, gzipGeoJSON(geojson))
                db.insertConvertedGeoJSONEntry(tx, import.id, key)
                insertQcTask(tx, key)
            }
        }
    }

    fun insertQcTask(tx: Transaction, inputKey: String): Long {
        val sql = "insert into qc_task (input_s3key) values (?) RETURNING qc_task_id"
        return tx.selectOne(sql, listOf(inputKey)) { rs -> rs.getLong(1) }
    }

    fun convert(ms: List<Measurement>): GeoJson<MeasurementProperties> {
        val features = ms.flatMap { m ->
            listOf(
                    extractTemperature(m),
                    extractHumidity(m),
                    extractPressure(m),
                    extractRain(m),
                    extractWind(m),
            ).flatten()
        }
        return GeoJson(type = "FeatureCollection", features = features)
    }

    fun extractTemperature(m: Measurement) =
            m.data.Temperature?.let {
                listOf(GeoJsonFeature(
                        type = "Feature",
                        geometry = geometry(m),
                        properties = MeasurementProperties(
                                sourceId = "netatmo",
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_utc)),
                                observedPropertyTitle = "Air temperature",
                                observedProperty = generatePropertyURI(SOURCE_ID, "air_temperature"),
                                unitOfMeasureTitle = NetatmoUnit.temperature,
                                unitOfMeasure = "http://www.opengis.net/def/uom/UCUM/degC",
                                result = it,
                        )
                ))
            }.orEmpty()

    fun extractHumidity(m: Measurement) =
            m.data.Humidity?.let {
                listOf(GeoJsonFeature(
                        type = "Feature",
                        geometry = geometry(m),
                        properties = MeasurementProperties(
                                sourceId = "netatmo",
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_utc)),
                                observedPropertyTitle = "Relative Humidity",
                                observedProperty = generatePropertyURI(SOURCE_ID, "relative_humidity"),
                                unitOfMeasureTitle = NetatmoUnit.humidity,
                                unitOfMeasure = "",
                                result = it,
                        )
                ))
            }.orEmpty()

    fun extractPressure(m: Measurement) =
            m.data.Pressure?.let {
                listOf(GeoJsonFeature(
                        type = "Feature",
                        geometry = geometry(m),
                        properties = MeasurementProperties(
                                sourceId = "netatmo",
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_utc)),
                                observedPropertyTitle = "Air Pressure",
                                observedProperty = generatePropertyURI(SOURCE_ID, "air_pressure"),
                                unitOfMeasureTitle = NetatmoUnit.pressure,
                                unitOfMeasure = "",
                                result = it,
                        )
                ))
            }.orEmpty()

    fun extractRain(m: Measurement): List<GeoJsonFeature<MeasurementProperties>> {
        val fs = mutableListOf<GeoJsonFeature<MeasurementProperties>>()
        if (m.data.Rain != null && m.data.time_day_rain != null) {
            fs.add(GeoJsonFeature(
                    type = "Feature",
                    geometry = geometry(m),
                    properties = MeasurementProperties(
                            sourceId = "netatmo",
                            _id = m._id,
                            featureType = "MeasureObservation",
                            resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_day_rain)),
                            observedPropertyTitle = "Daily rain accumulation",
                            observedProperty = generatePropertyURI(SOURCE_ID, "daily_rain_accumulation"),
                            unitOfMeasureTitle = NetatmoUnit.rainfall,
                            unitOfMeasure = "",
                            result = m.data.Rain,
                    )
            ))
        }
        if (m.data.sum_rain_1 != null && m.data.time_hour_rain != null) {
            fs.add(GeoJsonFeature(
                    type = "Feature",
                    geometry = geometry(m),
                    properties = MeasurementProperties(
                            sourceId = "netatmo",
                            _id = m._id,
                            featureType = "MeasureObservation",
                            resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_hour_rain)),
                            observedPropertyTitle = "Hourly rain accumulation",
                            observedProperty = generatePropertyURI(SOURCE_ID, "hourly_rain_accumulation"),
                            unitOfMeasureTitle = NetatmoUnit.rainfall,
                            unitOfMeasure = "",
                            result = m.data.sum_rain_1,
                    )
            ))
        }
        return fs
    }

    fun extractWind(m: Measurement): List<GeoJsonFeature<MeasurementProperties>> {
        val fs = mutableListOf<GeoJsonFeature<MeasurementProperties>>()
        m.data.wind?.let { wind ->
            fs.addAll(wind.flatMap {
                val windSpeed = it.value[0].toDouble()
                val windAngle = it.value[1].toDouble()
                listOf(
                    GeoJsonFeature(
                            type = "Feature",
                            geometry = geometry(m),
                            properties = MeasurementProperties(
                                    sourceId = "netatmo",
                                    _id = m._id,
                                    featureType = "MeasureObservation",
                                    resultTime = timeFormatter.format(parseNetatmoTimestamp(it.key)),
                                    observedPropertyTitle = "Wind",
                                    observedProperty = generatePropertyURI(SOURCE_ID, "wind_speed"),
                                    unitOfMeasureTitle = NetatmoUnit.windSpeed,
                                    unitOfMeasure = "",
                                    result = windSpeed,
                            )
                    ),
                    GeoJsonFeature(
                        type = "Feature",
                        geometry = geometry(m),
                        properties = MeasurementProperties(
                                sourceId = "netatmo",
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(it.key)),
                                observedPropertyTitle = "Wind angle",
                                observedProperty = generatePropertyURI(SOURCE_ID, "wind_angle"),
                                unitOfMeasureTitle = NetatmoUnit.windAngle,
                                unitOfMeasure = "",
                                result = windAngle,
                        )
                    )
                )})
        }
        m.data.wind_gust?.let { gust ->
            fs.addAll(gust.flatMap {
                val windGustSpeed = it.value[0].toDouble()
                val windGustAngle = it.value[1].toDouble()
                listOf(
                    GeoJsonFeature(
                            type = "Feature",
                            geometry = geometry(m),
                            properties = MeasurementProperties(
                                    sourceId = "netatmo",
                                    _id = m._id,
                                    featureType = "MeasureObservation",
                                    resultTime = timeFormatter.format(parseNetatmoTimestamp(it.key)),
                                    observedPropertyTitle = "Wind gust",
                                    observedProperty = generatePropertyURI(SOURCE_ID, "wind_gust"),
                                    unitOfMeasureTitle = NetatmoUnit.windSpeed,
                                    unitOfMeasure = "",
                                    result = windGustSpeed,
                            )
                    ),
                    GeoJsonFeature(
                            type = "Feature",
                            geometry = geometry(m),
                            properties = MeasurementProperties(
                                    sourceId = "netatmo",
                                    _id = m._id,
                                    featureType = "MeasureObservation",
                                    resultTime = timeFormatter.format(parseNetatmoTimestamp(it.key)),
                                    observedPropertyTitle = "Wind gust angle",
                                    observedProperty = generatePropertyURI(SOURCE_ID, "wind_gust_angle"),
                                    unitOfMeasureTitle = NetatmoUnit.windAngle,
                                    unitOfMeasure = "",
                                    result = windGustAngle,
                            )
                    ),
                )
            })
        }
        return fs
    }

    fun parseNetatmoData(targz: InputStream): List<Measurement> {
        val fileEntries = readFilesFromTar(targz)
        if (fileEntries.size != 1) {
            throw RuntimeException("Expected to find one file in Netatmo tar response (found ${fileEntries.size})")
        }
        val file = fileEntries[0].second
        return parseJsonMeasurements(StringReader(file))
    }

    fun readFilesFromTar(targz: InputStream): List<Pair<String, String>> {
        val files = mutableListOf<Pair<String, String>>()
        val tar = TarArchiveInputStream(GZIPInputStream(targz))
        while (true) {
            val entry = tar.nextEntry
            if (entry == null) break;

            Log.info("Handling entry ${entry.name}")
            if (entry.isDirectory) {
                throw RuntimeException("Did not expect to find directory from netatmo data")
            } else {
                files.add(Pair(entry.name, IOUtils.toString(tar)))
            }
        }
        return files
    }
}

val gson = Gson()

fun <T> gzipGeoJSON(x: GeoJson<T>): ByteArray {
    val json = gson.toJson(x)
    val bytes = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(bytes)
    try {
        IOUtils.copy(StringReader(json), gzip)
        gzip.finish()
        return bytes.toByteArray()
    } finally {
        bytes.close()
        gzip.close()
    }
}

object NetatmoUnit {
    val rainfall = "mm"
    val windSpeed = "kph"
    val windAngle = "deg"
    val pressure = "mbar"
    val temperature = "C"
    val humidity = "%"
}

val netatmoPropertyNameUnitMap = mapOf(
        "air_temperature" to NetatmoUnit.temperature,
        "daily_rain_accumulation" to NetatmoUnit.rainfall,
        "hourly_rain_accumulation" to NetatmoUnit.rainfall,
        "relative_humidity" to NetatmoUnit.humidity,
        "air_pressure" to NetatmoUnit.pressure,
        "wind_speed" to NetatmoUnit.windSpeed,
        "wind_angle" to NetatmoUnit.windAngle,
        "wind_gust_speed" to NetatmoUnit.windSpeed,
        "wind_gust_angle" to NetatmoUnit.windAngle,
)

val netatmoPropertyNameTitleMap = mapOf(
        "air_temperature" to "Air temperature",
        "daily_rain_accumulation" to "Daily rain accumulation",
        "hourly_rain_accumulation" to "Hourly rain accumulation",
        "relative_humidity" to "Relative Humidity",
        "air_pressure" to "Air Pressure",
        "wind_speed" to "Wind",
        "wind_angle" to "Wind angle",
        "wind_gust_speed" to "Wind gust",
        "wind_gust_angle" to "Wind gust angle",
)
