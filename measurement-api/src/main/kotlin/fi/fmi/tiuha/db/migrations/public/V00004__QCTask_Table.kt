package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00004__QCTask_Table : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            CREATE TABLE qc_task (
                qc_task_id bigserial PRIMARY KEY,
                input_s3key text NOT NULL,
                task_arn text,
                output_s3key text UNIQUE,
                created timestamptz NOT NULL DEFAULT (current_timestamp at time zone 'UTC'),
                updated timestamptz DEFAULT (current_timestamp at time zone 'UTC'),
                check (input_s3key != output_s3key)
            );
        """.trimIndent()).execute()
    }
}