package fi.fmi.tiuha

import org.apache.log4j.LogManager
import java.time.Duration
import java.time.Instant

object Log {
    val logger = LogManager.getLogger("TIUHA")

    fun info(msg: String) {
        logger.info(msg)
    }

    fun info(ex: Exception, msg: String) {
        logger.info(msg, ex)
    }

    fun error(msg: String) {
        logger.error(msg)
    }

    fun error(ex: Throwable, msg: String) {
        logger.error(msg, ex)
    }

    fun <T> time (msg: String, fn: () -> T): T {
        val start = Instant.now()
        info("Start: $msg")
        val result = fn()
        val duration = Duration.between(start, Instant.now())

        val minutes = duration.toMinutes()
        val seconds = duration.seconds % 60
        val millis = duration.toMillis() % 1000
        val time = when {
            minutes > 0 -> "${minutes} minutes ${seconds} seconds ${millis} milliseconds"
            seconds > 0 -> "${seconds} seconds ${millis} milliseconds"
            else -> "${millis} milliseconds"
        }
        info("End: $msg, executed in $time")
        return result
    }
}