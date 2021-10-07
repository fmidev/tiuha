package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00002__NetatmoImport_Table : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("""
            CREATE TABLE netatmoimport (
                netatmoimport_id bigserial PRIMARY KEY,
                s3bucket text NOT NULL,
                s3key text NOT NULL,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC'),
                UNIQUE (s3bucket, s3key)
            );
        """.trimIndent(), emptyList())
    }
}