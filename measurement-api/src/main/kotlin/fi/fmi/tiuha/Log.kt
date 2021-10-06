package fi.fmi.tiuha

object Log {
    fun info(msg: String): Unit {
        println(msg)
    }

    fun info(ex: Exception, msg: String): Unit {
        println(msg)
        println(ex)
    }
}