package fi.fmi.tiuha

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import org.opengis.feature.simple.SimpleFeature

object EdrApi {
    val s3DataStore = S3DataStore(Config.measurementsBucket)
    val geomesa = Geomesa(s3DataStore.dataStore)

    fun routes(r: Routing) {
        r.get("/") {
            val features: List<SimpleFeature> = geomesa.query("BBOX (geom, -180, -90, 180, 90)")
            call.respond(features)
        }
    }
}