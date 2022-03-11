package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00009__Insert_QcTasks_For_Older_Data : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            WITH transformed_imports_without_tasks AS (
              SELECT netatmoimport_id, geojsonkey
              FROM netatmoimport
              LEFT JOIN qc_task ON (qc_task.input_s3key = netatmoimport.geojsonkey)
              WHERE geojsonkey IS NOT NULL AND qc_task_id IS NULL
            )
            INSERT INTO qc_task (qc_task_status_id, input_s3key)
            SELECT 'PENDING', geojsonkey FROM transformed_imports_without_tasks;
        """.trimIndent()).execute()
    }
}