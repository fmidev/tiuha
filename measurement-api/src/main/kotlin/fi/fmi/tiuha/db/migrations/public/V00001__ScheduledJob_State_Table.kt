package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00001__ScheduledJob_State_Table : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("""
            CREATE TABLE scheduledjob (
                name text PRIMARY KEY,
                nextfiretime timestamp with time zone NOT NULL
            );
        """.trimIndent(), emptyList())
    }
}