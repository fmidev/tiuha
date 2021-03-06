package fi.fmi.tiuha.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import fi.fmi.tiuha.Config
import java.sql.*
import java.time.ZonedDateTime
import java.util.*

open class Db(val ds: DataSource) {
    fun <T> inTx(fn: (Transaction) -> T): T = ds.transaction(fn)

    fun execute(query: String, params: List<Any>) = inTx { tx -> tx.execute(query, params) }
    fun <T> select(query: String, params: List<Any>, fn: (ResultSet) -> T) = inTx { tx -> tx.select(query, params, fn) }
    fun <T> selectOne(query: String, params: List<Any>, fn: (ResultSet) -> T) = inTx { tx -> tx.selectOne(query, params, fn) }
    fun <T> selectOneOrNone(query: String, params: List<Any>, fn: (ResultSet) -> T) = inTx { tx -> tx.selectOneOrNone(query, params, fn) }
}

class DataSource(private val jdbcUrl: String, private val dbUsername: String, private val dbPassword: String) {
    private val hikariConfig = HikariConfig().also {
        it.driverClassName = "org.postgresql.Driver"
        it.jdbcUrl = jdbcUrl
        it.username = dbUsername
        it.password = dbPassword
        it.isAutoCommit = false
    }

    val hikariDataSource = HikariDataSource(hikariConfig)

    fun <T> transaction(fn: (Transaction) -> T): T {
        return connection { c ->
            try {
                val result = fn(Transaction(c))
                c.commit()
                result
            } catch (e: Exception) {
                c.rollback()
                throw e
            }
        }
    }

    fun <T> connection(fn: (Connection) -> T): T {
        val connection = hikariDataSource.getConnection()
        try {
            return fn(connection)
        } finally {
            connection.close()
        }
    }

    fun close() = hikariDataSource.close()
}

class Transaction(val c: Connection) {
    fun execute(query: String, params: List<Any>) {
        val statement = c.prepareStatement(query)
        params.withIndex().forEach {
            bind(statement, it.value, it.index + 1)
        }
        statement.execute()
    }

    fun <T> select(query: String, params: List<Any>, fn: (ResultSet) -> T): List<T> {
        val statement = c.prepareStatement(query)
        params.withIndex().forEach {
            bind(statement, it.value, it.index + 1)
        }
        val resultSet = statement.executeQuery()
        val results = mutableListOf<T>()
        while (resultSet.next()) {
            results.add(fn(resultSet))
        }
        return results
    }

    fun <T> selectOneOrNone(query: String, params: List<Any>, fn: (ResultSet) -> T): T? {
        val results = select(query, params, fn)
        if (results.size > 1) {
            throw RuntimeException("Query returned returned more than one row: $query")
        }
        return results.getOrNull(0)
    }

    fun <T> selectOne(query: String, params: List<Any>, fn: (ResultSet) -> T): T {
        val results = select(query, params, fn)
        val rows = results.size
        if (rows != 1) {
            throw RuntimeException("selectOne query returned returned wrong number of rows ($rows): $query")
        }
        return results.first()
    }

    class Batcher(val statement: PreparedStatement) {
        fun addBatch(params: List<Any>) {
            params.withIndex().forEach {
                bind(statement, it.value, it.index + 1)
            }
            statement.addBatch()
        }
    }

    fun <T> batch(query: String, f: (Batcher) -> T) {
        val statement = c.prepareStatement(query)
        f(Batcher(statement))
        statement.executeBatch()
    }

}

private fun bind(statement: PreparedStatement, param: Any, index: Int): Unit =
        when (param) {
            is String -> statement.setString(index, param)
            is Long -> statement.setLong(index, param)
            is Int -> statement.setInt(index, param)
            is Boolean -> statement.setBoolean(index, param)
            is ZonedDateTime -> statement.setTimestamp(index, Timestamp(param.toInstant().toEpochMilli()), defaultCalendar)
            else -> throw RuntimeException("Unknown SQL parameter type")
        }

val defaultTimeZone = TimeZone.getTimeZone("UTC")
val defaultCalendar = Calendar.getInstance(defaultTimeZone)

fun ResultSet.optLong(columnLabel: String): Long? {
    val value = this.getLong(columnLabel)
    return if (this.wasNull()) null else value
}

fun ResultSet.optString(columnLabel: String): String? {
    val value = this.getString(columnLabel)
    return if (this.wasNull()) null else value
}

fun ResultSet.getDateTime(columnLabel: String): ZonedDateTime {
    val ts = this.getTimestamp(columnLabel, defaultCalendar)
    return ZonedDateTime.ofInstant(ts.toInstant(), defaultTimeZone.toZoneId())
}

fun ResultSet.getDateTimeNullable(columnLabel: String): ZonedDateTime? {
    if (this.getTimestamp(columnLabel) == null) {
        return null
    }
    return this.getDateTime(columnLabel)
}

fun ResultSet.getDateTime(columnIndex: Int): ZonedDateTime {
    val ts = this.getTimestamp(columnIndex, defaultCalendar)
    return ZonedDateTime.ofInstant(ts.toInstant(), defaultTimeZone.toZoneId())
}
