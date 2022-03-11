package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00007__MeasurementStoreImport_Table : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            CREATE TABLE measurement_store_import (
                id bigserial PRIMARY KEY,
                import_s3key text NOT NULL,
                imported_at timestamptz,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC'),
                UNIQUE (import_s3key)
            );
        """.trimIndent()).execute()
    }
}