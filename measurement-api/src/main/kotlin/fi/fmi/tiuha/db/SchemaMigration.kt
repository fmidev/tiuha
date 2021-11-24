package fi.fmi.tiuha.db

import fi.fmi.tiuha.Config
import org.flywaydb.core.Flyway

fun runMigrations() {
    val db = Db(Config.dataSource)
    createFlywayForSchema("public", db.ds).migrate()
    createFlywayForSchema("geomesa", db.ds).migrate()
}

fun createFlywayForSchema(schemaName: String, ds: DataSource): Flyway {
    val versionTableName = "schemaversion"
    val changesClassPath = "fi/fmi/tiuha/db/migrations/$schemaName"

    return Flyway.configure()
        .locations("classpath:$changesClassPath")
        .failOnMissingLocations(true)
        .schemas(schemaName)
        .table(versionTableName)
        .dataSource(ds.hikariDataSource)
        .baselineOnMigrate(true)
        .load()
}