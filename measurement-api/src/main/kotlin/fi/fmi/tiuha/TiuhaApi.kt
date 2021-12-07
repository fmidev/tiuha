package fi.fmi.tiuha

import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.getDateTime
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mindrot.jbcrypt.BCrypt
import java.sql.ResultSet
import java.time.ZonedDateTime

data class BadRequestException(val error: String) : Throwable("BadRequest")

@Serializable data class ErrorResponse(val error: String)

class TiuhaApi(port: Int) {
    val db = Db(Config.dataSource)
    val server = embeddedServer(
            Netty,
            port = port
    ) {
        install(Authentication) {
            basic {
                validate { checkApiKey(it) }
            }
        }

        install(CallLogging) {
            disableDefaultColors()
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = Config.prettyPrintJson
            })
        }

        install(StatusPages) {
            status(HttpStatusCode.Unauthorized) {
                call.response.status(HttpStatusCode.Unauthorized)
                call.respond(ErrorResponse("Unauthorized"))
            }

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
            authenticate {
                EdrApi.routes(this)
            }
        }
    }

    fun checkApiKey(creds: UserPasswordCredential): Principal? {
        val clientId = creds.name
        val apiKey = creds.password
        Log.info("Validating API key for client '${clientId}'")
        return fetchApiClient(clientId)?.let {
            if (BCrypt.checkpw(apiKey, it.apiKeyHash)) {
                UserIdPrincipal(it.clientId)
            } else {
                Log.info("Invalid API key provided for client '${clientId}")
                null
            }
        }
    }

    fun fetchApiClient(clientId: String): ApiClientRow? =
            db.selectOneOrNone("""
                SELECT apiclient_id, apikeyhash, created
                FROM apiclient WHERE apiclient_id = ?
            """, listOf(clientId), ApiClientRow::from)

    fun start() {
        server.start(wait = false)
    }

    fun stop() {
        server.stop(0, 10_000)
    }
}

data class ApiClientRow(
        val clientId: String,
        val apiKeyHash: String,
        val created: ZonedDateTime,
) {
    companion object {
        fun from(rs: ResultSet) = ApiClientRow(
                clientId = rs.getString("apiclient_id"),
                apiKeyHash = rs.getString("apikeyhash"),
                created = rs.getDateTime("created"),
        )
    }
}
