package fi.fmi.tiuha.netatmo

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import fi.fmi.tiuha.Geometry
import java.time.Instant

data class MeasurementData(
        val Temperature: Double?,
        val Humidity: Double?,
        val Pressure: Double?,
        val time_utc: Long,
        val Rain: Double?,
        val time_day_rain: Long?,
        val time_hour_rain: Long?,
        val sum_rain_1: Double?,
        val wind: Map<String, List<Int>>?,
        val wind_gust: Map<String, List<Int>>?,
)

data class Measurement(
        val _id: String,
        val location: List<Double>,
        val altitude: Int?,
        val data: MeasurementData
)

fun parseJsonMeasurements(reader: java.io.Reader): List<Measurement> {
    val measurements = mutableListOf<Measurement>()
    val gson = Gson()
    JsonReader(reader).use { reader ->
        reader.beginArray()

        while(reader.hasNext()) {
            val measurement = gson.fromJson<Measurement>(reader, Measurement::class.java)
            measurements.add(measurement)
        }
    }
    return measurements
}

fun geometry(m: Measurement) = Geometry(type = "Point", coordinates = when (m.altitude) {
    null -> listOf(m.location[0], m.location[1])
    else -> listOf(m.location[0], m.location[1], m.altitude.toDouble())
})

fun parseNetatmoTimestamp(n: Long) = Instant.ofEpochSecond(n)
fun parseNetatmoTimestamp(s: String) = parseNetatmoTimestamp(s.toLong())
