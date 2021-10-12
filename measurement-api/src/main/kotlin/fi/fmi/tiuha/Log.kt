package fi.fmi.tiuha

import org.apache.log4j.LogManager

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

    fun error(ex: Exception, msg: String) {
        logger.error(msg, ex)
    }
}