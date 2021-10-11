package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.NetatmoClient
import fi.fmi.tiuha.NetatmoImport
import org.apache.commons.io.IOUtils
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NetatmoImportTest : TiuhaTest() {
    val fakeNetatmoClient = FakeNetatmoClient()
    val job = NetatmoImport("FI", fakeS3, fakeNetatmoClient, null)
    fun exec() = job.exec()

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
    var responseContent = readFile("world_data_FI.tar.gz")

    fun readFile(file: String): ByteArray {
        val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(file)!!
        return IOUtils.toByteArray(stream)
    }

    override fun getCountryWeatherData(country: String): Pair<Int, ByteArray> {
        return Pair(responseStatus, responseContent)
    }
}