package fi.fmi.tiuha.netatmo

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.SecretsManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.IOUtils
import java.io.InputStream

interface S3 {
    fun listKeys(bucket: String, prefix: String? = null): List<String>
    fun getObjectStream(bucket: String, key: String, maxBytes: Long? = null): InputStream
    fun putObject(bucket: String, key: String, content: ByteArray): Unit
}

class TiuhaS3 : RealS3() {
    override val client = AmazonS3ClientBuilder.standard()
            .withRegion(Config.awsRegion)
            .build()
}

class ArchiveS3 : RealS3() {
    override val client = AmazonS3ClientBuilder.standard()
            .withRegion(Config.awsRegion)
            .withCredentials(fetchCredentialsFromSecretManager())
            .build()

    private fun fetchCredentialsFromSecretManager(): AWSCredentialsProvider {
        val json = SecretsManager.getSecretValue("observation-archive-access-keys")
        val creds = Json.decodeFromString<AwsCredentials>(json)
        return AWSStaticCredentialsProvider(BasicAWSCredentials(creds.accessKeyId, creds.secretAccessKey))
    }
}

@Serializable
data class AwsCredentials(
        val accessKeyId: String,
        val secretAccessKey: String
)

abstract class RealS3 : S3 {
    abstract val client: AmazonS3

    override fun listKeys(bucket: String, prefix: String?): List<String> {
        Log.info("Fetching keys from S3 bucket $bucket with prefix $prefix")

        fun exec(more: Boolean, marker: String?, previousKeys: List<String>): List<String> {
            when (more) {
                false -> return previousKeys
                true -> {
                    val req = ListObjectsRequest()
                            .withBucketName(bucket)
                            .withPrefix(prefix)
                            .withMarker(marker)
                    val response = client.listObjects(req)
                    val keys = response.objectSummaries.map { it.key }
                    return exec(response.isTruncated, response.nextMarker, previousKeys + keys)
                }
            }
        }

        return exec(true, null, emptyList())
    }

    override fun getObjectStream(bucket: String, key: String, maxBytes: Long?): InputStream {
        val request = GetObjectRequest(bucket, key)
        if (maxBytes != null) {
            request.setRange(0, maxBytes)
        }
        return client.getObject(request).objectContent
    }

    override fun putObject(bucket: String, key: String, content: ByteArray) {
        Log.info("Uploading to S3 bucket $bucket object $key")
        val metadata = ObjectMetadata()
        metadata.contentLength = content.size.toLong()
        val request = PutObjectRequest(bucket, key, content.inputStream(), metadata)
        client.putObject(request)
    }
}

class FakeS3 : S3 {
    private val storage = mutableMapOf<String, ByteArray>()

    override fun listKeys(bucket: String, prefix: String?): List<String> =
            storage.keys.toList().filter { prefix == null || it.startsWith(prefix) }

    override fun getObjectStream(bucket: String, key: String, maxBytes: Long?): InputStream =
            when (val obj = storage["$bucket/$key"]) {
                null -> throw RuntimeException("S3 object $key not found in bucket $bucket")
                else -> obj.inputStream()
            }

    override fun putObject(bucket: String, key: String, content: ByteArray) {
        storage["$bucket/$key"] = content.clone()
    }

    fun putObjectFromResources(bucket: String, key: String, resource: String) {
        val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(resource)!!
        return putObject(bucket, key, IOUtils.toByteArray(stream))
    }

    fun cleanup() = storage.clear()
}
