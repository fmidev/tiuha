package fi.fmi.tiuha.app

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.db.Db
import fi.fmi.tiuha.db.Transaction
import org.mindrot.jbcrypt.BCrypt

object CreateApiClientApp {
    val db = Db(Config.dataSource)
    @JvmStatic
    fun main(args: Array<String>): Unit = Log.time(this.javaClass.simpleName) {
        try {
            val clientId = args[0]
            val apiKey = args[1]

            db.inTx { tx -> insertCredentials(tx, clientId, apiKey) }
            Log.info("Created API client '$clientId' with API key '$apiKey'")
        } finally {
            Config.dataSource.close()
        }
    }

    fun insertCredentials(tx: Transaction, clientId: String, password: String) {
        Log.info("Inserting credentials for client '$clientId'")
        tx.execute(
                "INSERT INTO apiclient (apiclient_id, apikeyhash) VALUES (?, ?)",
                listOf(clientId, BCrypt.hashpw(password, BCrypt.gensalt()))
        )
    }
}