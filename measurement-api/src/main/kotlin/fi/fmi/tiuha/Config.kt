package fi.fmi.tiuha

import com.amazonaws.regions.Regions
import fi.fmi.tiuha.db.DataSource

object Config {
    val awsRegion = Regions.EU_WEST_1
    val measurementArchiveBucket = "fmi-iot-obs-arch"

    val dbHostname = requireEnv("DATABASE_HOST")
    val dbPort = Integer.parseInt(requireEnv("DATABASE_PORT"))
    val dbName = requireEnv("DATABASE_NAME")
    val jdbcUrl = "jdbc:postgresql://$dbHostname:$dbPort/$dbName"
    val dbUsername = requireEnv("DATABASE_USERNAME")
    val dbPassword = requireEnv("DATABASE_PASSWORD")

    val dataSource = DataSource(this)
}

fun requireEnv(key: String): String =
        when (val value = System.getenv(key)) {
            null -> throw RuntimeException("Environment variable $key is required")
            else -> value
        }