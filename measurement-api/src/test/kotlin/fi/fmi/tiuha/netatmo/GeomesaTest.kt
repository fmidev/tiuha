package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Geomesa
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertNotEquals

class GeomesaTest {
    // Requires access to S3 and the test doesn't control the
    // data in there so this test isn't really useful
    @Ignore
    @Test
    fun `does an actual query`() {
        val features = Geomesa.query("dtg BETWEEN 2021-07-17T07:00:00Z AND 2021-07-17T08:00:00Z")
        assertNotEmpty(features)
    }
}

fun <T> assertNotEmpty(xs: List<T>) = assertNotEquals(0, xs.size)