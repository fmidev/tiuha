package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00004__QCTask_Table : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("""
            CREATE TABLE qc_task (
                qc_task_id bigserial PRIMARY KEY,
                input_s3key text NOT NULL,
                task_arn text,
                output_s3key text UNIQUE,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC'),
                updated timestamptz DEFAULT (current_timestamp at time zone 'UTC'),
                check (input_s3key != output_s3key)
            );
        """.trimIndent(), emptyList())
    }
}