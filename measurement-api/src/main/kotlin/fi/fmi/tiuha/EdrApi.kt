package fi.fmi.tiuha

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import java.time.Instant
import java.util.*

object EdrApi {
    val s3DataStore = S3DataStore(Config.measurementsBucket)
    val geomesa = Geomesa(s3DataStore.dataStore)

    fun routes(r: Routing) = r.apply {
        get("/v1/edr/collections/netatmo/items") {
            when {
                call.parameters["bbox"] != null -> {
                    val timeRange = TimeRange.fromParameters(call.parameters)
                    val result = bboxSearch(timeRange, Bbox.fromParameters(call.parameters))
                    call.respond(result)
                }
                else -> throw BadRequestException("Bad Request")
            }
        }
    }

    fun bboxSearch(timeRange: TimeRange, bbox: Bbox): GeoJson<MeasurementProperties> {
        return geomesa.query("""
            (dtg BETWEEN ${timeFormatter.format(timeRange.start)} AND ${timeFormatter.format(timeRange.end)})
            AND
            (BBOX (geom, ${bbox.lat1}, ${bbox.lon1}, ${bbox.lat2}, ${bbox.lon2}))
        """)
    }
}

data class TimeRange(
        val start: Instant,
        val end: Instant,
) {
    companion object {
        fun fromParameters(params: Parameters): TimeRange {
            val start = params["start"] ?: throw BadRequestException("start is required")
            val end = params["end"] ?: throw BadRequestException("end is required")
            try {
                return TimeRange(Date(start.toLong()).toInstant(), Date(end.toLong()).toInstant())
            } catch (e: java.lang.NumberFormatException) {
                throw BadRequestException("Invalid start or end time")
            }
        }
    }
}

data class Bbox(
        val lat1: Float,
        val lon1: Float,
        val lat2: Float,
        val lon2: Float,
) {
    companion object {
        fun fromParameters(params: Parameters): Bbox {
            val bbox = params["bbox"] ?: throw BadRequestException("Invalid bbox")
            val coords = bbox.split(",")
            if (coords.size != 4) {
                throw BadRequestException("Invalid bbox")
            }
            try {
                return Bbox(coords[0].toFloat(), coords[1].toFloat(), coords[2].toFloat(), coords[3].toFloat())
            } catch (e: NumberFormatException) {
                throw BadRequestException("Invalid bbox")
            }
        }
    }
}