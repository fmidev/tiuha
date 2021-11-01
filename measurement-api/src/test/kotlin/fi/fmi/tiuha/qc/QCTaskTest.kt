package fi.fmi.tiuha.qc

import org.junit.Test
import kotlin.test.assertEquals

class QCTaskTest {
    @Test
    fun `output key is input key with qc_ in front of filename`() {
        val inputKey = "with/prefixes/file.json.gz"
        assertEquals("with/prefixes/qc_file.json.gz", generateOutputKey(inputKey))
    }
}