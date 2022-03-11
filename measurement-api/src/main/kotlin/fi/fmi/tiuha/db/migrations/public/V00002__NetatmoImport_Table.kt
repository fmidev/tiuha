package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00002__NetatmoImport_Table : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            CREATE TABLE netatmoimport (
                netatmoimport_id bigserial PRIMARY KEY,
                s3bucket text NOT NULL,
                s3key text NOT NULL,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC'),
                UNIQUE (s3bucket, s3key)
            );
        """.trimIndent()).execute()
    }
}