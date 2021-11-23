package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00001__ScheduledJob_State_Table : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            CREATE TABLE scheduledjob (
                name text PRIMARY KEY,
                nextfiretime timestamp with time zone NOT NULL
            );
        """.trimIndent()).execute()
    }
}