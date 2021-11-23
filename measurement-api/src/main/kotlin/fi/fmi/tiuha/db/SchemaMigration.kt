package fi.fmi.tiuha.db

import fi.fmi.tiuha.Config
import org.flywaydb.core.Flyway

fun runMigrations() {
    val db = Db(Config.dataSource)
    runMigrationForSchema("public", db.ds)
}

fun runMigrationForSchema(schemaName: String, ds: DataSource) {
    val versionTableName = "schemaversion"
    val changesClassPath = "fi/fmi/tiuha/db/migrations/$schemaName"

    val flyway = Flyway.configure()
        .locations("classpath:$changesClassPath")
        .failOnMissingLocations(true)
        .schemas(schemaName)
        .table(versionTableName)
        .dataSource(ds.hikariDataSource)
        .baselineOnMigrate(true)
        .load()

    flyway.migrate()
}