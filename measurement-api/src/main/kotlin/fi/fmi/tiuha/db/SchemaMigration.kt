package fi.fmi.tiuha.db

import fi.fmi.tiuha.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import java.sql.Connection
import java.util.concurrent.atomic.AtomicReference

object SchemaMigration {
    fun runMigrations() {
        val db = Db(Config.dataSource)
        val migration = PublicSchemaMigration(db.ds)
        db.inTx { tx -> migration.exec(tx) }
    }
}

class PublicSchemaMigration(ds: DataSource) {
    val versionTableName = "schemaversion"
    val schemaName = "public"
    val changesClassPath = "fi.fmi.tiuha.db.migrations.$schemaName"

    val flyway = Flyway().apply {
        setLocations("classpath:$changesClassPath")
        setSchemas(schemaName)
        setTable(versionTableName)
        setDataSource(ds.hikariDataSource)
        setBaselineOnMigrate(true)
    }

    fun exec(tx: Transaction) {
        SchemaChange.txRef.set(tx)
        flyway.migrate()
        SchemaChange.txRef.set(null)
    }
}

abstract class SchemaChange : JdbcMigration {
    companion object {
        val txRef = AtomicReference<Transaction>()
    }

    abstract fun exec(tx: Transaction): Unit

    override fun migrate(con: Connection): Unit {
        exec(txRef.get())
    }
}