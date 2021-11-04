package fi.fmi.tiuha.qc

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.netatmo.TiuhaTest
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import software.amazon.awssdk.services.ecs.EcsAsyncClient
import kotlin.test.assertEquals

class QCTaskTest : TiuhaTest() {
    @Test
    fun `output key is input key with qc_ in front of filename`() {
        val inputKey = "with/prefixes/file.json.gz"
        assertEquals("with/prefixes/qc_file.json.gz", generateOutputKey(inputKey))
    }

    @Test
    fun `QCTask does nothing when task is already started`() {
        val db = QCDb(Config.dataSource)

        val ecsClient = mockk<EcsAsyncClient>()
        val task = QCTask(db, ecsClient)

        val taskId = 1L
        db.execute(
            "insert into qc_task (qc_task_id, input_s3key, output_s3key, task_arn) values (?, 'input_key.json', 'output_key.json', 'arn:test')",
            listOf(taskId)
        )

        task.runQCTask(taskId)

        verify { ecsClient wasNot called }
    }
}
