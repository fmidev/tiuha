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
        val task = QCTask(db, ecsClient)

        val taskId = 1L
        db.execute(
            "insert into qc_task (qc_task_id, qc_task_status_id, input_s3key, output_s3key, task_arn) values (?, 'PENDING', 'input_key.json', 'output_key.json', 'arn:test')",
            listOf(taskId)
        )

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
        val task = QCTask(db, ecsClient)

        val taskId = 2L
        db.execute(
            "insert into qc_task (qc_task_id, qc_task_status_id, input_s3key) values (?, 'PENDING', 'prefix/input_key.json')",
            listOf(taskId)
        )

        task.runQCTask(taskId)
        val (actualTaskArn, statusId, actualOutputKey) = db.selectOne(
            "select task_arn, qc_task_status_id, output_s3key from qc_task where qc_task_id = ?",
            listOf(taskId)
        ) { Triple(it.getString("task_arn"), it.getString("qc_task_status_id"), it.getString("output_s3key")) }
        assertEquals(startedTaskArn, actualTaskArn)
        assertEquals("STARTED", statusId)
        assertEquals("prefix/qc_input_key.json", actualOutputKey)
    }
}
