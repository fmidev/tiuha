package fi.fmi.tiuha

import fi.fmi.tiuha.db.DataSource
import software.amazon.awssdk.regions.Region


enum class Environment {
    PROD, DEV, LOCAL
}


object Config {
    val environment = Environment.valueOf(requireEnv("ENV").uppercase())
    val awsRegion = Region.EU_WEST_1
    val measurementArchiveBucket = "fmi-iot-obs-arch"

    val dbHostname = requireEnv("DATABASE_HOST")
    val dbPort = Integer.parseInt(requireEnv("DATABASE_PORT"))
    val dbName = requireEnv("DATABASE_NAME")
    val jdbcUrl = "jdbc:postgresql://$dbHostname:$dbPort/$dbName"
    val dbUsername = requireEnv("DATABASE_USERNAME")
    val dbPassword = requireEnv("DATABASE_PASSWORD")

    val dataSource = DataSource(this)

    val importBucket = requireEnv("IMPORT_BUCKET")
}

fun requireEnv(key: String): String =
        when (val value = System.getenv(key)) {
            null -> throw RuntimeException("Environment variable $key is required")
            else -> value
        }