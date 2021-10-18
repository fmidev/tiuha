package fi.fmi.tiuha

import fi.fmi.tiuha.db.DataSource
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.db.getDateTime
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

abstract class ScheduledJob(val name: String) {
    val schedulerDb = SchedulerDb(Config.dataSource)
    val executor = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        Log.info("Starting scheduled job $name")
        schedulerDb.init(this)

        // Check if task needs to be run every 10 seconds starting random(0..10) seconds from start
        val delay = Duration.ofSeconds(10).toMillis()
        val initialDelay = Random.nextLong(delay)

        val command = Runnable {
            try {
                schedulerDb.inTx { tx ->
                    val now = ZonedDateTime.now()
                    val nextFireTime = schedulerDb.tryAcquireScheduledJob(tx, name)
                    if (nextFireTime != null && nextFireTime <= now) {
                        Log.info("Executing scheduled job $name")
                        exec()
                        schedulerDb.updateNextFireTime(tx, name, nextFireTime())
                        Log.info("Executed $name successfully")
                    }
                }
            } catch (e: Exception) {
                Log.error(e, "Scheduler $name execution failed")
            }
        }

        executor.scheduleWithFixedDelay(command, initialDelay, delay, TimeUnit.MILLISECONDS)
    }

    fun await() = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)

    abstract fun nextFireTime(): ZonedDateTime

    abstract fun exec(): Unit
}

class SchedulerDb(ds: DataSource) : Db(ds) {
    fun init(job: ScheduledJob) {
        inTx { tx ->
            tx.execute("""
                insert into scheduledjob (name, nextfiretime)
                values (?, ?) on conflict do nothing
            """.trimIndent(), listOf(job.name, job.nextFireTime()))
        }
    }

    fun updateNextFireTime(tx: Transaction, name: String, nextFireTime: ZonedDateTime) {
        tx.execute("update scheduledjob set nextfiretime = ? where name = ?", listOf(nextFireTime, name))
    }

    fun tryAcquireScheduledJob(tx: Transaction, jobName: String): ZonedDateTime? {
        return tx.selectOneOrNone("""
            SELECT nextfiretime FROM scheduledjob
            WHERE name = ? FOR UPDATE SKIP LOCKED
        """, listOf(jobName)) { it.getDateTime(1) }
    }

    fun tryLock(tx: Transaction, key: Long): Boolean {
        return tx.selectOne("select pg_try_advisory_xact_lock(?)", listOf(key), { it.getBoolean(1) })
    }


    fun getPendingJobs(tx: Transaction): List<String> {
        val sql = "select scheduledjob_id from scheduledjob nextfiretime for update"
        return tx.select(sql, emptyList()) { it.getString("scheduledjob_id") }
    }
}