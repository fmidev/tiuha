package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.*
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.runMigrations
import org.junit.Before

abstract class TiuhaTest {
    open val db: Db = Db(Config.dataSource)
    val s3 = LocalStackS3()

    @Before
    fun before() {
        clearDb()
        clearBucket(s3, Config.importBucket)
        clearBucket(s3, TestConfig.TEST_MEASUREMENTS_BUCKET)
    }

    fun clearDb() {
        listOf(
                "scheduledjob",
                "netatmoimport",
                "schemaversion",
                "qc_task",
                "measurement_store_import",
        ).forEach { db.execute("DROP TABLE IF EXISTS $it", emptyList()) }
        runMigrations()
    }
}

private fun clearBucket(s3: S3, bucket: String) =
        s3.listKeys(bucket).forEach { s3.deleteObject(bucket, it) }
