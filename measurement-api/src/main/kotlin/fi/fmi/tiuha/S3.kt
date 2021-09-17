package fi.fmi.tiuha

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import java.io.InputStream

object S3 {
    private val client = AmazonS3ClientBuilder.standard()
            .withRegion(Config.awsRegion)
            .build()

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