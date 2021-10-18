package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.ScheduledJob
import fi.fmi.tiuha.db.getDateTime
import org.junit.Test
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestJob : ScheduledJob("test") {
    override fun nextFireTime(): ZonedDateTime =
            ZonedDateTime.now().plusMonths(1)

    override fun exec() {}
}

class ScheduledJobTest : TiuhaTest() {
    @Test
    fun `checking and updating next fire time`() {
        val job = TestJob()

        job.schedulerDb.init(job)
        getNextFireTime().let { nft ->
            assertNotNull(nft, "Expected init function to set nextfiretime")
            assert(nft.isAfter(ZonedDateTime.now()), { "Expected scheduled job next fire time to be in future" })
        }

        assertEquals(false, job.tryExec(), "Expected scheduled job to not run")
        setNextFireTime(ZonedDateTime.now().minusDays(1))
        assertEquals(true, job.tryExec(), "Expected scheduled job to run")
    }

    fun setNextFireTime(nft: ZonedDateTime) =
            db.execute("update scheduledjob set nextfiretime = ? where name = ?", listOf(nft, "test"))

    fun getNextFireTime(): ZonedDateTime =
            db.selectOne("select nextfiretime from scheduledjob where name = ?", listOf("test")) { it.getDateTime(1) }
}