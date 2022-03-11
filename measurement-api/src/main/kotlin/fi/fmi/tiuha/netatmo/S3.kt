package fi.fmi.tiuha.netatmo

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Environment
import fi.fmi.tiuha.Log
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.io.InputStream

interface S3 {
    fun getObjectStream(bucket: String, key: String, maxBytes: Long? = null): InputStream
    fun putObject(bucket: String, key: String, content: ByteArray): Unit
    fun deleteObject(bucket: String, key: String): Unit
    fun listKeys(bucket: String, prefix: String? = null): List<String>
    fun keyExists(bucket: String, key: String): Boolean
}

val localStackCredentialsProvider =
        StaticCredentialsProvider.create(
                object : AwsCredentials {
                    override fun accessKeyId() = "access_key"
                    override fun secretAccessKey() = "secret_key"
                }
        )

private fun buildLocalstackS3Client(httpClient: SdkHttpClient): S3Client = S3Client.builder()
        .httpClient(httpClient)
        .endpointOverride(java.net.URI("http://localhost:4566"))
        .serviceConfiguration { it.pathStyleAccessEnabled(true) }
        .credentialsProvider(localStackCredentialsProvider)
        .region(Config.awsRegion)
        .build()

private fun buildRealS3Client(httpClient: SdkHttpClient): S3Client = S3Client.builder()
        .httpClient(httpClient)
        .region(Config.awsRegion)
        .build()

class LocalStackS3 : RealS3() {
    override val client = buildLocalstackS3Client(httpClient)
}

class TiuhaS3 : RealS3() {
    override val client = when(Config.environment) {
        Environment.PROD, Environment.DEV -> buildRealS3Client(httpClient)
        Environment.LOCAL -> buildLocalstackS3Client(httpClient)
    }
}

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

    override fun deleteObject(bucket: String, key: String) {
        Log.info("Deleting S3 object $key from bucket $bucket")
        client.deleteObject({ it.bucket(bucket).key(key) })
    }

    override fun listKeys(bucket: String, prefix: String?): List<String> {
        fun exec(fetchMore: Boolean, marker: String?, keys: List<String>): List<String> =
                if (!fetchMore) keys else {
                    val response = client.listObjects({ it.bucket(bucket).prefix(prefix).marker(marker) })
                    val moreKeys = response.contents().map { it.key() }
                    exec(response.isTruncated, response.nextMarker(), keys + moreKeys)
                }

        return exec(true, null, emptyList())
    }

    override fun keyExists(bucket: String, key: String): Boolean {
        try {
            client.headObject({ it.bucket(bucket).key(key) })
            return true
        } catch (e: NoSuchKeyException) {
            return false
        }
    }
}