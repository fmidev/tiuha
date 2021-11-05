package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00006__NetatmoCountryColumn_NotNull : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("ALTER TABLE netatmoimport ALTER COLUMN country SET NOT NULL".trimIndent(), emptyList())
    }
}
