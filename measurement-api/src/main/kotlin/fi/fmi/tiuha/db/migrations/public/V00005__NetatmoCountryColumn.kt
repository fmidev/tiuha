package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00005__NetatmoCountryColumn : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("ALTER TABLE netatmoimport ADD COLUMN country text".trimIndent()).execute()
    }
}
