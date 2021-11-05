package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.NetatmoConfig
import fi.fmi.tiuha.netatmo.NetatmoGeoJsonTransform
import fi.fmi.tiuha.netatmo.NetatmoImportDb
import fi.fmi.tiuha.netatmo.S3
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.BasicHttpContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

class NetatmoImport(
        val country: String,
        val s3: S3,
        val netatmo: NetatmoClient,
        val transformTask: NetatmoGeoJsonTransform?,
) : ScheduledJob("netatmoimport_${country.lowercase()}") {
    companion object {
        val countries = listOf("FI", "NO", "SE", "DK", "EE", "LV", "LT")
    }

    val db = NetatmoImportDb(Config.dataSource)

    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HHmmss")

    override fun exec(): Unit = Log.time("NetatmoImport $country") {

        Log.info("Fetching data for country $country from Netatmo")
        val (statusCode, content) = netatmo.getCountryWeatherData(country)
        if (statusCode != 200) throw RuntimeException("Failed to fetch Netatmo data")
        processContent(content)
    }

    fun processContent(content: ByteArray) {
        val ts = ZonedDateTime.now().format(formatter)
        Log.info("Received ${content.size} bytes of data")
        val s3Key = "netatmo/${ts}/countryweatherdata-${country}.tar.gz"
        Log.info("Storing Netatmo response as $s3Key")
        s3.putObject(Config.importBucket, s3Key, content)
        val importId = db.insertImport(country, Config.importBucket, s3Key)

        transformTask?.attemptTransform(importId)
    }

    override fun nextFireTime(): ZonedDateTime {
        val now = ZonedDateTime.now()
        val minute = now.get(ChronoField.MINUTE_OF_HOUR)
        return now.with(ChronoField.MINUTE_OF_HOUR, (minute - (minute % 10)).toLong())
                .with(ChronoField.SECOND_OF_MINUTE, 0)
                .with(ChronoField.NANO_OF_SECOND, 0)
                .plus(10, ChronoUnit.MINUTES)
    }
}

open class NetatmoClient {
    val client = HttpClients.createDefault()

    open fun getCountryWeatherData(country: String): Pair<Int, ByteArray> {
        val builder = URIBuilder(NetatmoConfig.importUrl)
        builder.addParameter("country", country)
        builder.addParameter("key", NetatmoConfig.apiKey)
        val request = HttpGet(builder.build())
        val response = client.execute(request, BasicHttpContext())
        val content = IOUtils.toByteArray(response.entity.content)

        val statusCode = response.getStatusLine().getStatusCode()
        return Pair(statusCode, content)
    }
}