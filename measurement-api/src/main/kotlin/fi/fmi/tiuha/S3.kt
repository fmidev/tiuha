package fi.fmi.tiuha

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream

class TiuhaS3 : S3 {
    override val client = AmazonS3ClientBuilder.standard()
            .withRegion(Config.awsRegion)
            .build()
}

class ArchiveS3 : S3 {
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

interface S3 {
    val client: AmazonS3

    fun listKeys(bucket: String, prefix: String? = null): List<String> {
        println("Fetching keys from S3 bucket $bucket with prefix $prefix")

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

    fun getObjectStream(bucket: String, key: String): InputStream =
            client.getObject(GetObjectRequest(bucket, key)).objectContent
}