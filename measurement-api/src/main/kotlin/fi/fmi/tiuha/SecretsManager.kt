package fi.fmi.tiuha

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest

object SecretsManager {
    private val client = SecretsManagerClient.builder()
            .region(Config.awsRegion)
            .build()

    fun getSecretValue(secretId: String): String {
        val response = client.getSecretValue { it.secretId(secretId) }
        return response.secretString()
    }
}