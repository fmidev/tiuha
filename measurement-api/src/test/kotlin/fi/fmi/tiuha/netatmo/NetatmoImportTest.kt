package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.NetatmoClient
import fi.fmi.tiuha.NetatmoImport
import fi.fmi.tiuha.NetatmoImportDb
import fi.fmi.tiuha.db.SchemaMigration
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NetatmoImportTest {
    val db = NetatmoImportDb(Config.dataSource)
    val fakeNetatmoClient = FakeNetatmoClient()
    val fakeS3 = FakeS3()
    val job = NetatmoImport("FI", fakeS3, fakeNetatmoClient)
    fun exec() = job.exec()

    @Before
    fun before() {
        SchemaMigration.runMigrations()
        fakeS3.cleanup()
        db.execute("TRUNCATE netatmoimport", emptyList())
    }

    @Test
    fun `Netatmo import adds the key to S3 and records it in database`() {
        assertEquals(fakeS3.listKeys(Config.importBucket).size, 0)
        assertEquals(db.getNetatmoImportData().size, 0)

        exec()

        val keys = fakeS3.listKeys(Config.importBucket)
        assertEquals(keys.size, 1)
        val imports = db.getNetatmoImportData()
        assertEquals(imports.size, 1)

        val import = imports[0]
        assertNotNull(fakeS3.getObjectStream(import.s3bucket, import.s3key))
    }
}

class FakeNetatmoClient : NetatmoClient() {
    var responseStatus = 200
    var responseContent = ByteArray(0)

    override fun getCountryWeatherData(country: String): Pair<Int, ByteArray> {
        return Pair(responseStatus, responseContent)
    }
}