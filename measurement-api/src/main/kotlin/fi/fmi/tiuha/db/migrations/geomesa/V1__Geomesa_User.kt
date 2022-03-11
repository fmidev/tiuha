package fi.fmi.tiuha.db.migrations.geomesa

import fi.fmi.tiuha.Config
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V1__Geomesa_User : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        val username = Config.geomesaUsername
        val password = Config.geomesaPassword.replace("'", "''")
        val schemaName = Config.geomesaSchema

        ctx.connection.prepareStatement("""
            CREATE ROLE $username WITH LOGIN PASSWORD '$password';
            GRANT ALL ON SCHEMA $schemaName TO $username;
        """.trimIndent()).execute()
    }
}