package fi.fmi.tiuha.netatmo

import com.google.gson.Gson
import fi.fmi.tiuha.*
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

class NetatmoGeoJsonTransform(val s3: S3) : ScheduledJob("netatmogeojsontransform") {
    val gson = Gson()
    val transformExecutor = Executors.newFixedThreadPool(1)
    val timeFormatter = DateTimeFormatter.ISO_INSTANT

    val db = NetatmoImportDb(Config.dataSource)

    override fun nextFireTime(): ZonedDateTime =
            ZonedDateTime.now().plus(10, ChronoUnit.MINUTES)

    override fun exec() = Log.time("NetatmoGeoJsonTransform") {
        val datas = db.getDataForGeoJSONTransform()
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
            }
        }
    }

    fun gzipGeoJSON(xs: GeoJson): ByteArray {
        val json = gson.toJson(xs)
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

    fun convert(ms: List<Measurement>): GeoJson {
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
                        properties = FeatureProperties(
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_utc)),
                                observedPropertyTitle = "Air temperature",
                                observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0023/",
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
                        properties = FeatureProperties(
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_utc)),
                                observedPropertyTitle = "Relative Humidity",
                                observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0413/",
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
                        properties = FeatureProperties(
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_utc)),
                                observedPropertyTitle = "Air Pressure",
                                observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0015/",
                                unitOfMeasureTitle = NetatmoUnit.pressure,
                                unitOfMeasure = "",
                                result = it,
                        )
                ))
            }.orEmpty()

    fun extractRain(m: Measurement): List<GeoJsonFeature> {
        val fs = mutableListOf<GeoJsonFeature>()
        if (m.data.Rain != null && m.data.time_day_rain != null) {
            fs.add(GeoJsonFeature(
                    type = "Feature",
                    geometry = geometry(m),
                    properties = FeatureProperties(
                            _id = m._id,
                            featureType = "MeasureObservation",
                            resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_day_rain)),
                            observedPropertyTitle = "Daily rain accumulation",
                            observedProperty = "",
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
                    properties = FeatureProperties(
                            _id = m._id,
                            featureType = "MeasureObservation",
                            resultTime = timeFormatter.format(parseNetatmoTimestamp(m.data.time_hour_rain)),
                            observedPropertyTitle = "Hourly rain accumulation",
                            observedProperty = "",
                            unitOfMeasureTitle = NetatmoUnit.rainfall,
                            unitOfMeasure = "",
                            result = m.data.sum_rain_1,
                    )
            ))
        }
        return fs
    }

    fun extractWind(m: Measurement): List<GeoJsonFeature> {
        val fs = mutableListOf<GeoJsonFeature>()
        m.data.wind?.let { wind ->
            fs.addAll(wind.map {
                GeoJsonFeature(
                        type = "Feature",
                        geometry = geometry(m),
                        properties = FeatureProperties(
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(it.key)),
                                observedPropertyTitle = "Wind",
                                observedProperty = "",
                                unitOfMeasureTitle = NetatmoUnit.wind,
                                unitOfMeasure = "",
                                result = it.value[0].toDouble(),
                                windAngle = it.value[1].toDouble()
                        )
                )
            })
        }
        m.data.wind_gust?.let { gust ->
            fs.addAll(gust.map {
                GeoJsonFeature(
                        type = "Feature",
                        geometry = geometry(m),
                        properties = FeatureProperties(
                                _id = m._id,
                                featureType = "MeasureObservation",
                                resultTime = timeFormatter.format(parseNetatmoTimestamp(it.key)),
                                observedPropertyTitle = "Wind gust",
                                observedProperty = "",
                                unitOfMeasureTitle = NetatmoUnit.wind,
                                unitOfMeasure = "",
                                result = it.value[0].toDouble(),
                                windAngle = it.value[1].toDouble()
                        )
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

object NetatmoUnit {
    val rainfall = "mm"
    val wind = "kph"
    val pressure = "mbar"
    val temperature = "C"
    val humidity = "%"
}