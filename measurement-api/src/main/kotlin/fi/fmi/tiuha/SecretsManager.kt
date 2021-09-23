package fi.fmi.tiuha

import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest

object SecretsManager {
    private val client = AWSSecretsManagerClientBuilder.standard()
            .withRegion(Config.awsRegion)
            .build()

    fun getSecretValue(secretId: String): String {
        val response = client.getSecretValue(GetSecretValueRequest().withSecretId(secretId))
        return response.secretString
    }
}