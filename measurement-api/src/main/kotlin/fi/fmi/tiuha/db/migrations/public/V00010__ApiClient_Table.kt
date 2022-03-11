package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00010__ApiClient_Table : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            CREATE TABLE apiclient (
                apiclient_id text PRIMARY KEY,
                apikeyhash text NOT NULL,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC')
            );
        """.trimIndent()).execute()
    }
}