package fi.fmi.tiuha.qc

import fi.fmi.tiuha.db.DataSource
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.db.getDateTime
import java.sql.ResultSet
import java.time.ZonedDateTime

class QCDb(ds: DataSource) : Db(ds) {
    fun getUnstartedQCTaskIds(): List<Long> =
        select("""
            select qc_task_id from qc_task
            where output_s3key is null
            limit 10
        """.trimIndent(), emptyList()) { it.getLong("qc_task_id") }

    fun markQCTaskAsStarted(tx: Transaction, id: Long, taskArn: String, outputKey: String) {
        tx.execute("""
            update qc_task set task_arn = ?, output_s3key = ?
            where qc_task_id = ?
        """.trimIndent(), listOf(taskArn, outputKey, id))
    }

    fun getAndLockQCTask(tx: Transaction, id: Long): QCTaskRow =
        tx.selectOne("""
            select qc_task_id, input_s3key, output_s3key, task_arn, created, updated
            from qc_task
            where qc_task_id = ?
            for update
        """.trimIndent(), listOf(id), QCTaskRow::from)
}

data class QCTaskRow(
    val id: Long,
    val inputKey: String,
    val outputKey: String?,
    val taskArn: String?,
    val created: ZonedDateTime,
    val updated: ZonedDateTime,
) {
    companion object {
        fun from(rs: ResultSet): QCTaskRow = QCTaskRow(
            id = rs.getLong("qc_task_id"),
            inputKey = rs.getString("input_s3key"),
            outputKey = rs.getString("output_s3key"),
            taskArn = rs.getString("task_arn"),
            created = rs.getDateTime("created"),
            updated = rs.getDateTime("updated"),
        )
    }
}
