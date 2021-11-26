package fi.fmi.tiuha

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TiuhaApi(port: Int) {
    val server = embeddedServer(
            Netty,
            port = port,
            watchPaths = listOf("measurement-api"),
    ) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = Config.prettyPrintJson
            })
        }

        install(Routing) {
            EdrApi.routes(this)
        }
    }

    fun start() {
        server.start(wait = false)
    }

    fun stop() {
        server.stop(0, 10_000)
    }
}