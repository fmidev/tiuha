package fi.fmi.tiuha.netatmo

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import fi.fmi.tiuha.Geometry
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

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
        val location: Array<Double>,
        val altitude: Int?,
        val data: MeasurementData
) {
    override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Measurement

        if (!location.contentEquals(other.location)) return false
        if (altitude != other.altitude) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(location.contentHashCode(), altitude, data.hashCode())
}

fun parseJsonMeasurements(reader: java.io.Reader): List<Measurement> {
    val measurements: MutableList<Measurement> = ArrayList()
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
