package fi.fmi.tiuha.db.migrations.public

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V00003__GeoJSONkey_Column : BaseJavaMigration() {
    override fun migrate(ctx: Context) {
        ctx.connection.prepareStatement("""
            ALTER TABLE netatmoimport ADD COLUMN updated timestamptz DEFAULT (current_timestamp at time zone 'UTC');
            UPDATE netatmoimport SET updated = created;
            ALTER TABLE netatmoimport ALTER COLUMN updated SET NOT NULL;

            ALTER TABLE netatmoimport ADD COLUMN geojsonkey text;
            CREATE INDEX netatmoimport_geojsonkey_idx ON netatmoimport (geojsonkey);
        """).execute()
    }
}