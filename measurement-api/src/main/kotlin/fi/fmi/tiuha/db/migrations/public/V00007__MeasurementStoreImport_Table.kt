package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00007__MeasurementStoreImport_Table : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("""
            CREATE TABLE measurement_store_import (
                id bigserial PRIMARY KEY,
                import_s3key text NOT NULL,
                imported_at timestamptz,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC'),
                UNIQUE (import_s3key)
            );
        """.trimIndent(), emptyList())
    }
}