package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.NetatmoClient
import fi.fmi.tiuha.NetatmoImport
import org.apache.commons.io.IOUtils
import org.junit.Test
import kotlin.test.assertEquals

class NetatmoImportTest : TiuhaTest() {
    private val fakeNetatmoClient = FakeNetatmoClient()
    private val job = NetatmoImport("FI", s3, fakeNetatmoClient, null)
    override val db = NetatmoImportDb(Config.dataSource)

    @Test
    fun `Netatmo import adds the key to S3 and records it in database`() {
        assertEquals(s3.listKeys(Config.importBucket).size, 0)
        assertEquals(db.getNetatmoImportData().size, 0)

        job.exec()

        val keys = s3.listKeys(Config.importBucket)
        assertEquals(keys.size, 1)
        val imports = db.getNetatmoImportData()
        assertEquals(imports.size, 1)

        assertEquals(keys[0], imports[0].s3key)
        assertEquals("FI", imports[0].country)
    }
}

class FakeNetatmoClient : NetatmoClient() {
    var responseStatus = 200
    var responseContent = readFile("world_data_FI.tar.gz")

    private fun readFile(file: String): ByteArray {
        val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(file)!!
        return IOUtils.toByteArray(stream)
    }

    override fun getCountryWeatherData(country: String): Pair<Int, ByteArray> {
        return Pair(responseStatus, responseContent)
    }
}