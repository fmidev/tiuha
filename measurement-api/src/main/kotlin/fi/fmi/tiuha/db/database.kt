package fi.fmi.tiuha.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import fi.fmi.tiuha.Config
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

open class Db(val ds: DataSource) {
    fun <T> inTx(fn: (Transaction) -> T): T = ds.transaction(fn)
}

class DataSource(config: Config) {
    val hikariConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = config.jdbcUrl
        username = config.dbUsername
        password = config.dbPassword
        isAutoCommit = false
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
        val resultOpt = selectOneOrNone(query, params, fn)
        return when (resultOpt) {
            null -> throw RuntimeException("Query returned returned no rows: $query")
            else -> resultOpt
        }
    }

    private fun bind(statement: PreparedStatement, param: Any, index: Int): Unit =
            when (param) {
                is String -> statement.setString(index, param)
                is Long -> statement.setLong(index, param)
                is Int -> statement.setInt(index, param)
                is Boolean -> statement.setBoolean(index, param)
                is DateTime -> statement.setTimestamp(index, Timestamp(param.millis), Calendar.getInstance(defaultTimeZone))
                else -> throw RuntimeException("Unknown SQL parameter type")
            }
}

val defaultTimeZone = TimeZone.getTimeZone("UTC")

fun ResultSet.getDateTime(columnLabel: String): DateTime {
    val ts = this.getTimestamp(columnLabel)
    return DateTime(ts.time, DateTimeZone.forTimeZone(defaultTimeZone))
}

fun ResultSet.getDateTime(columnIndex: Int): DateTime {
    val ts = this.getTimestamp(columnIndex)
    return DateTime(ts.time, DateTimeZone.forTimeZone(defaultTimeZone))
}