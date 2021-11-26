package fi.fmi.tiuha

import fi.fmi.tiuha.db.DataSource
import software.amazon.awssdk.regions.Region


enum class Environment {
    PROD, DEV, LOCAL
}


object Config {
    val environment = Environment.valueOf(requireEnv("ENV").uppercase())
    val awsRegion = Region.EU_WEST_1

    val dbHostname = requireEnv("DATABASE_HOST")
    val dbPort = Integer.parseInt(requireEnv("DATABASE_PORT"))
    val dbName = requireEnv("DATABASE_NAME")
    val jdbcUrl = "jdbc:postgresql://$dbHostname:$dbPort/$dbName"
    val dbUsername = requireEnv("DATABASE_USERNAME")
    val dbPassword = requireEnv("DATABASE_PASSWORD")

    val geomesaUsername = "geomesa"
    val geomesaSchema = geomesaUsername
    val geomesaPassword = requireEnv("GEOMESA_DB_PASSWORD")
    val geomesaMetadataJdbcUrl = "jdbc:postgresql://$dbHostname:$dbPort/$dbName?currentSchema=$geomesaSchema"

    val dataSource = DataSource(jdbcUrl, dbUsername, dbPassword)

    val importBucket = requireEnv("IMPORT_BUCKET")
    val measurementsBucket = "fmi-tiuha-measurements-" + requireEnv("ENV")

    val httpPort = 8383
    val prettyPrintJson = true
}

fun requireEnv(key: String): String =
        when (val value = System.getenv(key)) {
            null -> throw RuntimeException("Environment variable $key is required")
            else -> value
        }