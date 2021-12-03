package fi.fmi.tiuha

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

data class BadRequestException(val error: String) : Throwable("BadRequest")

@Serializable data class ErrorResponse(val error: String)

class TiuhaApi(port: Int) {
    val server = embeddedServer(
            Netty,
            port = port,
            watchPaths = listOf("measurement-api"),
    ) {
        install(CallLogging) {
            disableDefaultColors()
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = Config.prettyPrintJson
            })
        }

        install(StatusPages) {
            exception<BadRequestException> { cause ->
                Log.info("Bad Request ${cause}")
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(ErrorResponse(cause.error))
            }

            exception<Throwable> { cause ->
                Log.error("Exception ${cause.message}")
                call.response.status(HttpStatusCode.InternalServerError)
                call.respond(ErrorResponse("Internal Server Error"))
            }
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