package fi.fmi.tiuha

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.joda.time.DateTime
import org.joda.time.Duration
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils

class NetatmoImport : ScheduledJob("netatmoimport") {
    val netatmo = NetatmoClient()

    override fun exec() {
        val response = netatmo.getCountryWeatherData("FI")
        Log.info("${response.first}")
        Log.info("${response.second.length}")
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

class NetatmoClient {
    val client = HttpClients.createDefault()
    val apiKey = SecretsManager.getSecretValue("netatmo-api-key")

    fun getCountryWeatherData(country: String): Pair<Int, String> {
        val builder = URIBuilder("https://api.netatmo.com/apiexport/getcountryweatherdata")
        builder.addParameter("country", country)
        builder.addParameter("key", apiKey)
        val request = HttpGet(builder.build())
        val response = client.execute(request, BasicHttpContext())
        val content = EntityUtils.toString(response.entity, "UTF-8")
        val statusCode = response.getStatusLine().getStatusCode()
        return Pair(statusCode, content)
    }
}