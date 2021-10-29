package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Environment
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.SecretsManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.IOUtils
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.io.InputStream

interface S3 {
    fun getObjectStream(bucket: String, key: String, maxBytes: Long? = null): InputStream
    fun putObject(bucket: String, key: String, content: ByteArray): Unit
}

private fun buildLocalstackS3Client(httpClient: SdkHttpClient): S3Client = S3Client.builder()
        .httpClient(httpClient)
        .endpointOverride(java.net.URI("http://localhost:4566"))
        .serviceConfiguration { it.pathStyleAccessEnabled(true) }
        .region(Config.awsRegion)
        .build()

private fun buildRealS3Client(httpClient: SdkHttpClient): S3Client = S3Client.builder()
        .httpClient(httpClient)
        .region(Config.awsRegion)
        .build()

class TiuhaS3 : RealS3() {
    override val client = when(Config.environment) {
        Environment.PROD, Environment.DEV -> buildRealS3Client(httpClient)
        Environment.LOCAL -> buildLocalstackS3Client(httpClient)
    }
}

class ArchiveS3 : RealS3() {
    override val client: S3Client = S3Client.builder()
        .httpClient(httpClient)
        .region(Region.EU_WEST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(fetchCredentialsFromSecretManager())
        )
        .build()

    private fun fetchCredentialsFromSecretManager(): AwsCredentials {
        val json = SecretsManager.getSecretValue("observation-archive-access-keys")
        val creds = Json.decodeFromString<AwsCredentialsJson>(json)
        return object : AwsCredentials {
            override fun accessKeyId() = creds.accessKeyId
            override fun secretAccessKey() = creds.secretAccessKey
        }
    }
}

@Serializable
data class AwsCredentialsJson(
        val accessKeyId: String,
        val secretAccessKey: String
)

abstract class RealS3 : S3 {
    val httpClient: SdkHttpClient = ApacheHttpClient.builder().maxConnections(50).build()
    abstract val client: S3Client

    override fun getObjectStream(bucket: String, key: String, maxBytes: Long?): InputStream {
        return client.getObject {
            it.bucket(bucket)
            it.key(key)
            if (maxBytes != null) {
                it.range("bytes=0-$maxBytes")
            }
        }
    }

    override fun putObject(bucket: String, key: String, content: ByteArray) {
        Log.info("Uploading to S3 bucket $bucket object $key")
        client.putObject({ it.bucket(bucket).key(key) }, RequestBody.fromBytes(content))
    }
}

class FakeS3 : S3 {
    private val storage = mutableMapOf<Pair<String, String>, ByteArray>()

    fun listKeys(bucket: String, prefix: String? = null): List<String> =
            storage.keys.toList()
                    .filter { it.first == bucket }
                    .filter { prefix == null || it.second.startsWith(prefix) }
                    .map { it.second }

    override fun getObjectStream(bucket: String, key: String, maxBytes: Long?): InputStream =
            when (val obj = storage[Pair(bucket, key)]) {
                null -> throw RuntimeException("S3 object $key not found in bucket $bucket")
                else -> obj.inputStream()
            }

    override fun putObject(bucket: String, key: String, content: ByteArray) {
        storage[Pair(bucket, key)] = content.clone()
    }

    fun putObjectFromResources(bucket: String, key: String, resource: String) {
        val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(resource)!!
        return putObject(bucket, key, IOUtils.toByteArray(stream))
    }

    fun cleanup() = storage.clear()
}
