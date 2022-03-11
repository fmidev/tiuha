package fi.fmi.tiuha.qc

import fi.fmi.tiuha.db.DataSource
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.db.getDateTime
import java.sql.ResultSet
import java.time.ZonedDateTime

class QCDb(ds: DataSource) : Db(ds) {
    fun getUnstartedQCTaskIds(limit: Long? = null): List<Long> {
        val (limitSql, limitParams) = when (limit) {
            null -> Pair("", emptyList())
            else -> Pair("limit ?", listOf(limit))
        }
        return select("""
            select qc_task_id from qc_task
            where qc_task_status_id = 'PENDING'
            $limitSql
        """.trimIndent(), limitParams) { it.getLong("qc_task_id") }
    }

    fun markQCTaskAsStarted(tx: Transaction, id: Long, taskArn: String, outputKey: String) {
        tx.execute("""
            update qc_task set task_arn = ?, output_s3key = ?, qc_task_status_id = 'STARTED', updated = current_timestamp
            where qc_task_id = ?
        """.trimIndent(), listOf(taskArn, outputKey, id))
    }

    fun getStartedTasks(limit: Long? = null): List<QCTaskRow> {
        val (limitSql, limitParams) = when (limit) {
            null -> Pair("", emptyList())
            else -> Pair("limit ?", listOf(limit))
        }
        return select("""
            select qc_task_id, qc_task_status_id, input_s3key, output_s3key, task_arn, created, updated
            from qc_task where qc_task_status_id = 'STARTED'
            $limitSql
        """.trimIndent(), limitParams, QCTaskRow::from)
    }

    fun markQCTaskAsCompleted(tx: Transaction, id: Long) {
        tx.execute("""
            update qc_task set qc_task_status_id = 'COMPLETE', updated = current_timestamp
            where qc_task_id = ?
        """.trimIndent(), listOf(id))
    }

    fun getAndLockQCTask(tx: Transaction, id: Long): QCTaskRow =
        tx.selectOne("""
            select qc_task_id, qc_task_status_id, input_s3key, output_s3key, task_arn, created, updated
            from qc_task
            where qc_task_id = ?
            for update
        """.trimIndent(), listOf(id), QCTaskRow::from)

    fun statusCounts(): List<Pair<String, Long>> {
        return select("""
            select qc_task_status_id, count(qc_task_id) as count
            from qc_task_status
            left join qc_task using (qc_task_status_id)
            group by qc_task_status_id
            order by qc_task_status_id
        """.trimIndent(), emptyList()) { rs ->
            Pair(rs.getString("qc_task_status_id"), rs.getLong("count"))
        }
    }
}

data class QCTaskRow(
    val id: Long,
    val status: String,
    val inputKey: String,
    val outputKey: String?,
    val taskArn: String?,
    val created: ZonedDateTime,
    val updated: ZonedDateTime,
) {
    companion object {
        fun from(rs: ResultSet): QCTaskRow = QCTaskRow(
            id = rs.getLong("qc_task_id"),
            status = rs.getString("qc_task_status_id"),
            inputKey = rs.getString("input_s3key"),
            outputKey = rs.getString("output_s3key"),
            taskArn = rs.getString("task_arn"),
            created = rs.getDateTime("created"),
            updated = rs.getDateTime("updated"),
        )
    }
}
