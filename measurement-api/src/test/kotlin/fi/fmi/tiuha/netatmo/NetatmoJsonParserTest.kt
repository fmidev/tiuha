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
            "enc:16:testIdDynamicum",
            arrayOf(24.9607611, 60.2037551),
            30,
            MeasurementData(
                5.8,
                65.0,
                null,
                1632406373,
                0.0,
                1632344400,
                1632406373,
                0.0,
                mapOf(
                    Pair("1633605701", listOf(1, 225)),
                    Pair("1633606003", listOf(1, 225)),
                    Pair("1633606304", listOf(1, 225)),
                ),
                mapOf(
                    Pair("1633605701", listOf(2, 233)),
                    Pair("1633606003", listOf(3, 237)),
                    Pair("1633606304", listOf(2, 251)),
                )
            )
        )
        assertEquals(expectedDynamicum, dynamicum)
    }
}