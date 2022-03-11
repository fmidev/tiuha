package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00006__NetatmoCountryColumn_NotNull : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("ALTER TABLE netatmoimport ALTER COLUMN country SET NOT NULL".trimIndent()).execute()
    }
}
