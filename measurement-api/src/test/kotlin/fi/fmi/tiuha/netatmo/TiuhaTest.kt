package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.db.SchemaMigration
import org.junit.Before

abstract class TiuhaTest {
    val db = NetatmoImportDb(Config.dataSource)
    val fakeS3 = FakeS3()

    @Before
    fun before() {
        clearDb()
        fakeS3.cleanup()
    }

    fun clearDb() {
        listOf(
                "scheduledjob",
                "netatmoimport",
                "schemaversion",
                "qc_task",
        ).forEach { db.execute("DROP TABLE IF EXISTS $it", emptyList()) }
        SchemaMigration.runMigrations()
    }
}