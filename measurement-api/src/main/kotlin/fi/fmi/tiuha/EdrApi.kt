package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.netatmoPropertyNameTitleMap
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import java.time.Duration
import java.time.Instant

object EdrApi {
    val collections = netatmoPropertyNameTitleMap.keys.toList().map { "netatmo-$it" }
    val s3DataStore = S3DataStore(Config.measurementsBucket)
    val geomesa = Geomesa(s3DataStore.dataStore)

    fun routes(r: Route) = r.apply {
        get("/v1/edr/collections/{collection}/cube") {
            val collection = call.parameters["collection"] ?: throw BadRequestException("collection is required")
            if (!collections.contains(collection)) throw BadRequestException("Invalid collection '$collection'")
            val propertyId = collection.replace("-", "/")

            when {
                call.parameters["bbox"] != null -> {
                    val timeRange = TimeRange.fromParameters(call.parameters)
                    val result = bboxSearch(propertyId, timeRange, Bbox.fromParameters(call.parameters))
                    call.respond(result)
                }
                else -> throw BadRequestException("Bad Request")
            }
        }
    }

    fun bboxSearch(propertyId: String, timeRange: TimeRange, bbox: Bbox): GeoJson<MeasurementProperties> {
        return geomesa.query("""
            (dtg BETWEEN ${timeFormatter.format(timeRange.start)} AND ${timeFormatter.format(timeRange.end)})
            AND
            (BBOX (geom, ${bbox.lat1}, ${bbox.lon1}, ${bbox.lat2}, ${bbox.lon2}))
            AND
            (property_id = '$propertyId')
        """)
    }
}

data class TimeRange(
        val start: Instant,
        val end: Instant,
) {
    companion object {
        fun fromParameters(params: Parameters): TimeRange {
            val startParam = params["start"] ?: throw BadRequestException("start is required")
            val endParam = params["end"] ?: throw BadRequestException("end is required")
            try {
                val start = Instant.from(timeFormatter.parse(startParam))
                val end = Instant.from(timeFormatter.parse(endParam))

                if (start > end) {
                    throw BadRequestException("Time range start is earlier than end")
                }

                if (Duration.between(start, end) > Duration.ofHours(1)) {
                    throw BadRequestException("Time ranges longer than one hour are not allowed")
                }

                return TimeRange(start, end)
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