package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00008__QCTaskStatus : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            CREATE TABLE qc_task_status (
                qc_task_status_id text PRIMARY KEY,
                description text NOT NULL
            );

            INSERT INTO qc_task_status(qc_task_status_id, description) VALUES
                ('PENDING', 'QC task is new and pending. Not yet started.'),
                ('STARTED', 'QC task has been started.'),
                ('COMPLETE', 'QC task has completed');

            ALTER TABLE qc_task ADD COLUMN qc_task_status_id text REFERENCES qc_task_status (qc_task_status_id);
            UPDATE qc_task SET qc_task_status_id = 'PENDING' WHERE task_arn IS NULL;
            UPDATE qc_task SET qc_task_status_id = 'STARTED' WHERE task_arn IS NOT NULL;
            ALTER TABLE qc_task ALTER COLUMN qc_task_status_id SET NOT NULL;
            CREATE INDEX qc_task_qc_task_status_id_idx ON qc_task (qc_task_status_id);
        """.trimIndent()).execute()
    }
}