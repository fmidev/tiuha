package fi.fmi.tiuha

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

object EdrApi {
    val s3DataStore = S3DataStore(Config.measurementsBucket)
    val geomesa = Geomesa(s3DataStore.dataStore)

    fun routes(r: Routing) = r.apply {
        get("/v1/edr/collections/netatmo/items") {
            val bbox = call.parameters["bbox"]
            when {
                bbox != null -> {
                    val result = bboxSearch(parseBbox(bbox))
                    call.respond(result)
                }
                else -> throw BadRequestException("Bad Request")
            }
        }
    }

    fun parseBbox(s: String): Bbox {
        val coords = s.split(",")
        if (coords.size != 4) {
            throw BadRequestException("Invalid bbox")
        }
        try {
            return Bbox(coords[0].toFloat(), coords[1].toFloat(), coords[2].toFloat(), coords[3].toFloat())
        } catch (e: NumberFormatException) {
            throw BadRequestException("Invalid bbox")
        }
    }

    fun bboxSearch(bbox: Bbox): GeoJson<MeasurementProperties> {
        return geomesa.query("BBOX (geom, ${bbox.lat1}, ${bbox.lon1}, ${bbox.lat2}, ${bbox.lon2})")
    }
}

data class Bbox(
        val lat1: Float,
        val lon1: Float,
        val lat2: Float,
        val lon2: Float,
)