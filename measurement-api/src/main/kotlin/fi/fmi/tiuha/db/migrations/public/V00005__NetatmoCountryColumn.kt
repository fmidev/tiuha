package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00005__NetatmoCountryColumn : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("ALTER TABLE netatmoimport ADD COLUMN country text".trimIndent(), emptyList())
    }
}
