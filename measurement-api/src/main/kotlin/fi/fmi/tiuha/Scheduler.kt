package fi.fmi.tiuha

import fi.fmi.tiuha.db.DataSource
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.db.getDateTime
import org.joda.time.DateTime
import org.joda.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

abstract class ScheduledJob(val name: String) {
    val db = SchedulerDb(Config.dataSource)
    val executor = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        db.init(this)

        // Check if task needs to be run every 10 seconds starting random(0..10) seconds from start
        val delay = Duration.standardSeconds(10).millis
        val initialDelay = Random.nextLong(delay)

        val command = Runnable {
            val db = SchedulerDb(Config.dataSource)
            try {
                db.inTx { tx ->
                    val now = DateTime.now()
                    val nextFireTime = db.tryAcquireScheduledJob(tx, name)
                    if (nextFireTime != null && nextFireTime <= now) {
                        println("Executing scheduled job $name")
                        exec()
                        db.updateNextFireTime(tx, name, nextFireTime())
                        println("Executed $name successfully")
                    }
                }
            } catch (e: Exception) {
                println("Scheduler $name execution failed")
                println(e)
            }
        }

        executor.scheduleWithFixedDelay(command, initialDelay, delay, TimeUnit.MILLISECONDS)
    }

    abstract fun nextFireTime(): DateTime

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

    fun updateNextFireTime(tx: Transaction, name: String, nextFireTime: DateTime) {
        tx.execute("update scheduledjob set nextfiretime = ? where name = ?", listOf(nextFireTime, name))
    }

    fun tryAcquireScheduledJob(tx: Transaction, jobName: String): DateTime? {
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