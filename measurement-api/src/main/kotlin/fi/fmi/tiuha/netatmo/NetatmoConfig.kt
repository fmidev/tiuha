package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Environment
import fi.fmi.tiuha.SecretsManager

private object NetatmoProdConfig : NetatmoConfigType {
    override val apiKey: String by lazy { SecretsManager.getSecretValue("netatmo-api-key") }
    override val importUrl = "https://api.netatmo.com/apiexport/getcountryweatherdata"
}

private object NetatmoLocalConfig : NetatmoConfigType {
    override val apiKey = "this-would-be-a-netatmo-api-key"
    override val importUrl = "http://localhost:1234/getcountryweatherdata"
}

interface NetatmoConfigType {
    val apiKey: String
    val importUrl: String
}

val NetatmoConfig: NetatmoConfigType = when(Config.environment) {
    Environment.DEV, Environment.PROD -> NetatmoProdConfig
    Environment.LOCAL -> NetatmoLocalConfig
}

