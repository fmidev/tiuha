package fi.fmi.tiuha

import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.defaultTimeZone
import fi.fmi.tiuha.netatmo.TiuhaS3
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.BasicHttpContext
import org.joda.time.DateTime
import org.joda.time.Duration

class NetatmoImport(val country: String) : ScheduledJob("netatmoimport_${country.lowercase()}") {
    companion object {
        val countries = listOf("FI", "NO", "SE", "DK", "EE", "LV", "LT")
    }

    val db = NetatmoImportDb(Config.dataSource)
    val s3 = TiuhaS3()
    val netatmo = NetatmoClient()

    override fun exec() {
        val ts = DateTime.now().toString("yyyyMMddHHmmss")

        val (statusCode, content) = netatmo.getCountryWeatherData(country)
        if (statusCode != 200) throw RuntimeException("Failed to fetch Netatmo data")

        val s3Key = "netatmo/${ts}/countryweatherdata-${country}.tar.gz"
        s3.putObject(Config.importBucket, s3Key, content)
        db.insertImport(Config.importBucket, s3Key)
    }

    override fun nextFireTime(): DateTime {
        val now = DateTime.now()
        val minute = now.minuteOfHour().get()
        return now.withMinuteOfHour(minute - (minute % 10))
                .withSecondOfMinute(0)
                .withMillisOfSecond(0)
                .withDurationAdded(Duration.standardMinutes(10), 1)
    }
}

class NetatmoImportDb(ds: fi.fmi.tiuha.db.DataSource) : Db(ds) {
    fun insertImport(s3bucket: String, s3key: String) {
        execute("insert into netatmoimport (s3bucket, s3key) values (? ,?)", listOf(s3bucket, s3key))
    }
}

class NetatmoClient {
    val client = HttpClients.createDefault()
    val apiKey = SecretsManager.getSecretValue("netatmo-api-key")

    fun getCountryWeatherData(country: String): Pair<Int, ByteArray> {
        val builder = URIBuilder("https://api.netatmo.com/apiexport/getcountryweatherdata")
        builder.addParameter("country", country)
        builder.addParameter("key", apiKey)
        val request = HttpGet(builder.build())
        val response = client.execute(request, BasicHttpContext())
        val content = IOUtils.toByteArray(response.entity.content)

        val statusCode = response.getStatusLine().getStatusCode()
        return Pair(statusCode, content)
    }
}