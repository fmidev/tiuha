package fi.fmi.tiuha.netatmo

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.util.*
import kotlin.collections.ArrayList

data class MeasurementData(val Temperature: Double?, val Humidity: Double?, val Pressure: Double?, val time_utc: Long)

data class Measurement(val _id: String, val location: Array<Double>, val altitude: Int, val data: MeasurementData) {
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

