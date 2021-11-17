package fi.fmi.tiuha

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PropertiesTest {
    @Test
    fun `it generates correct property URI`() {
        val uri = generatePropertyURI("netatmo", "wind_speed")
        assertEquals("http://tiuha.fmi.fi/property/netatmo/wind_speed", uri)
    }

    @Test
    fun `doesn't allow slashes in source'`() {
        assertFailsWith<AssertionError> {
            generatePropertyURI("foo/bar", "wind_speed")
        }
    }

    @Test
    fun `doesn't allow slashes in property name'`() {
        assertFailsWith<AssertionError> {
            generatePropertyURI("netatmo", "wind/speed")
        }
    }
}