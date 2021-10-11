package fi.fmi.tiuha.db.migrations.public

import fi.fmi.tiuha.db.SchemaChange
import fi.fmi.tiuha.db.Transaction

class V00003__GeoJSONkey_Column : SchemaChange() {
    override fun exec(tx: Transaction) {
        tx.execute("""
            ALTER TABLE netatmoimport ADD COLUMN updated timestamptz DEFAULT (current_timestamp at time zone 'UTC');
            UPDATE netatmoimport SET updated = created;
            ALTER TABLE netatmoimport ALTER COLUMN updated SET NOT NULL;

            ALTER TABLE netatmoimport ADD COLUMN geojsonkey text;
            CREATE INDEX netatmoimport_geojsonkey_idx ON netatmoimport (geojsonkey);
        """, emptyList())
    }
}