package fi.fmi.tiuha.qc

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.netatmo.TiuhaTest
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun ecsTaskRequestJson(cluster: String, subnet: String, bucket: String, inputKey: String, outputKey: String, taskDefinition: String) = """
    {"cluster":"$cluster","launchType":"FARGATE","networkConfiguration":{"awsvpcConfiguration":{"subnets":["$subnet"],"assignPublicIp":"DISABLED"}},"overrides":{"containerOverrides":[{"name":"TitanlibContainer","command":["--bucket","$bucket","--inputKey","$inputKey","--outputKey","$outputKey"]}]},"propagateTags":"TASK_DEFINITION","taskDefinition":"$taskDefinition"}
""".trimIndent()

fun ecsTaskResponseJson(taskArn: String) = """{
  "failures": [],
  "tasks": [
    {
      "clusterArn": "arn:aws:ecs:us-east-1:012345678910:cluster/default",
      "containerInstanceArn": "arn:aws:ecs:us-east-1:012345678910:container-instance/4c543eed-f83f-47da-b1d8-3d23f1da4c64",
      "containers": [
        {
          "containerArn": "arn:aws:ecs:us-east-1:012345678910:container/e76594d4-27e1-4c74-98b5-46a6435eb769",
          "lastStatus": "PENDING",
          "name": "wordpress",
          "taskArn": "arn:aws:ecs:us-east-1:012345678910:task/fdf2c302-468c-4e55-b884-5331d816e7fb"
        }
      ],
      "createdAt": 1479765460.842,
      "desiredStatus": "RUNNING",
      "lastStatus": "PENDING",
      "overrides": {
        "containerOverrides": [
          {
            "name": "wordpress"
          },
          {
            "name": "mysql"
          }
        ]
      },
      "taskArn": "$taskArn",
      "taskDefinitionArn": "arn:aws:ecs:us-east-1:012345678910:task-definition/hello_world:6",
      "version": 1
    }
  ]
}"""

class QCTaskTest : TiuhaTest() {
    override val db = QCDb(Config.dataSource)

    @Test
    fun `output key is input key with qc_ in front of filename`() {
        val inputKey = "with/prefixes/file.json.gz"
        assertEquals("with/prefixes/qc_file.json.gz", generateOutputKey(inputKey))
    }

    @Test
    fun `QCTask does nothing when task is already started`() {
        val ecsClient = mockk<EcsClient>()
        val task = QCTask(db, ecsClient, s3)

        val taskId = db.inTx { tx ->
            val id = QCTask.insertQcTask(tx, "input_key.json")
            db.markQCTaskAsStarted(tx, id, "arn:test", "output_key.json")
            id
        }

        task.runQCTask(taskId)
        verify { ecsClient wasNot called }
    }

    @Test
    fun `QCTask makes the correct RunTask request`() {
        val startedTaskArn = "arn:started-task"
        val httpClient = object : SdkHttpClient {
            override fun close() = Unit

            override fun prepareRequest(request: HttpExecuteRequest) = object : ExecutableHttpRequest {
                override fun call(): HttpExecuteResponse {
                    val requestContent = request.contentStreamProvider().map {
                        BufferedReader(InputStreamReader(it.newStream())).lines().collect(Collectors.toList())
                    }

                    val expectedRequest = ecsTaskRequestJson(
                        cluster = QCConfig.titanClusterArn,
                        bucket = Config.importBucket,
                        subnet = QCConfig.titanTaskSubnet,
                        taskDefinition = QCConfig.titanTaskDefinitionArn,
                        inputKey = "prefix/input_key.json",
                        outputKey = "prefix/qc_input_key.json",
                    )
                    assertEquals(expectedRequest, requestContent.get().first())

                    val runTaskResponseStream = AbortableInputStream.create(ecsTaskResponseJson(startedTaskArn).byteInputStream())
                    return HttpExecuteResponse.builder()
                        .response(SdkHttpFullResponse.builder()
                            .statusCode(200)
                            .appendHeader("Content-Type", "application/x-amz-json-1.1")
                            .content(runTaskResponseStream).build())
                        .responseBody(runTaskResponseStream)
                        .build()
                }

                override fun abort() = Unit
            }
        }
        val ecsClient = EcsClient.builder()
            .httpClient(httpClient)
            .region(Region.EU_WEST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .build()
        val task = QCTask(db, ecsClient, s3)

        val taskId = db.inTx { tx -> QCTask.insertQcTask(tx, "prefix/input_key.json") }

        task.runQCTask(taskId)
        getQCTaskRow(taskId).let {
            assertEquals(startedTaskArn, it.taskArn)
            assertEquals("STARTED", it.status)
            assertEquals("prefix/qc_input_key.json", it.outputKey)
        }
    }

    @Test
    fun `checks when QC output is produced and creates measurement store import`() {
        val ecsClient = EcsClient.builder().region(Region.EU_WEST_1).build()
        val task = QCTask(db, ecsClient, s3, true)

        val taskId = db.inTx { tx -> QCTask.insertQcTask(tx, "prefix/input_key.json") }

        getQCTaskRow(taskId).let {
            assertEquals("PENDING", it.status)
        }

        task.exec()
        getQCTaskRow(taskId).let {
            assertEquals("STARTED", it.status)
            assertEquals("prefix/qc_input_key.json", it.outputKey)
        }
        assertEquals(0, countMeasurementStoreImports())

        // Actual QC run is known to be done when the output is written to S3
        s3.putObject(Config.importBucket, "prefix/qc_input_key.json", "foobar".toByteArray())

        task.exec()
        getQCTaskRow(taskId).let {
            assertEquals("COMPLETE", it.status)
            assertTrue(it.updated > it.created)
        }
        assertEquals(1, countMeasurementStoreImports())
    }

    fun getQCTaskRow(taskId: Long) = db.inTx { tx -> db.getAndLockQCTask(tx, taskId) }

    fun countMeasurementStoreImports(): Long = db.selectOne("select count(*) from measurement_store_import", emptyList()) { it.getLong(1) }
}
