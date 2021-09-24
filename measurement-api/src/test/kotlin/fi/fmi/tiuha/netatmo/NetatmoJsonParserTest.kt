package fi.fmi.tiuha.netatmo

import org.junit.Test
import java.io.InputStreamReader
import kotlin.test.assertEquals

class NetatmoJsonParserTest {
    @Test
    fun testParse() {
        val testJsonStream = ClassLoader.getSystemClassLoader().getResourceAsStream("netatmo.json")
        val actualMeasurements = parseJsonMeasurements(InputStreamReader(testJsonStream))
        assertEquals(2, actualMeasurements.size)

        val dynamicum = actualMeasurements[0]
        val expectedDynamicum = Measurement(
            arrayOf(24.9607611, 60.2037551),
            30,
            MeasurementData(
                5.8,
                65.0,
                null,
                1632406373
            )
        )
        assertEquals(expectedDynamicum, dynamicum)
    }
}