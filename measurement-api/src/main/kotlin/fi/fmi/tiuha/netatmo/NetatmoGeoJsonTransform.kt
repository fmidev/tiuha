package fi.fmi.tiuha.netatmo

import com.google.gson.Gson
import fi.fmi.tiuha.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.time.Instant
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
        val formatter = DateTimeFormatter.ISO_INSTANT

        val features = ms.flatMap { m ->
            val fs = mutableListOf<GeoJsonFeature>()
            val inst = Instant.ofEpochSecond(m.data.time_utc)
            val time = formatter.format(inst)
            val geometry = Geometry(type = "Point", coordinates = when (m.altitude) {
                null -> listOf(m.location[0], m.location[1])
                else -> listOf(m.location[0], m.location[1], m.altitude.toDouble())
            })

            m.data.Temperature?.let { temp -> fs.add(mkTemperatureFeature(m._id, geometry, time, temp)) }
            m.data.Humidity?.let { hum -> fs.add(mkHumidityFeature(m._id, geometry, time, hum)) }
            m.data.Pressure?.let { press -> fs.add(mkPressureFeature(m._id, geometry, time, press)) }
            if (m.data.Rain != null && m.data.time_day_rain != null) {
                val ts = formatter.format(Instant.ofEpochSecond(m.data.time_day_rain))
                fs.add(mkDayRainfallFeature(m._id, geometry, ts, m.data.Rain))
            }
            if (m.data.sum_rain_1 != null && m.data.time_hour_rain != null) {
                val ts = formatter.format(Instant.ofEpochSecond(m.data.time_hour_rain))
                fs.add(mkHourRainfallFeature(m._id, geometry, ts, m.data.sum_rain_1))
            }
            m.data.wind?.let { fs.addAll(mkWindFeature(m._id, geometry, it)) }
            m.data.wind_gust?.let { fs.addAll(mkWindGustFeature(m._id, geometry, it)) }
            fs
        }
        return GeoJson(type = "FeatureCollection", features = features)
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