package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Geomesa
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class GeomesaTest : TiuhaTest() {
    @Test
    fun `does an actual query`() {
        fun query() = Geomesa.query("dtg BETWEEN 2021-01-01T00:00:00Z AND 2022-01-01T00:00:00Z")

        assertEquals(0, query().size)
        insertTestData()
        assertEquals(5, query().size)
    }
}