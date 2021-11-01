package fi.fmi.tiuha.qc

import software.amazon.awssdk.services.ecs.EcsAsyncClient
import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.ScheduledJob
import software.amazon.awssdk.services.ecs.model.AssignPublicIp
import software.amazon.awssdk.services.ecs.model.LaunchType
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

fun generateOutputKey(inputKey: String): String {
    val inputSegments = inputKey.split('/')
    val fileName = inputSegments.last()
    val outputFileName = "qc_$fileName"
    val outputPrefix = inputSegments.dropLast(1).joinToString("/")
    return "$outputPrefix/$outputFileName"
}

class QCTask : ScheduledJob("qc_task") {
    private val db = QCDb(Config.dataSource)
    private val ecsClient: EcsAsyncClient = EcsAsyncClient.builder().region(Config.awsRegion).build()

    override fun nextFireTime(): ZonedDateTime =
        ZonedDateTime.now().plus(10, ChronoUnit.MINUTES)

    private fun runQCTask(id: Long) = db.inTx { tx ->
        val task = db.getAndLockQCTask(tx, id)
        if (task.taskArn != null) {
            Log.info("QC task $id already started")
            return@inTx
        }
        val outputKey = generateOutputKey(task.inputKey)

        val runTaskResponse = ecsClient.runTask { builder ->
            builder
                .launchType(LaunchType.FARGATE)
                .cluster(QCConfig.titanClusterArn)
                .networkConfiguration { networkConfig ->
                    networkConfig.awsvpcConfiguration { vpcConfig ->
                        vpcConfig.subnets(QCConfig.titanTaskSubnet).assignPublicIp(AssignPublicIp.DISABLED)
                    }
                }
                .taskDefinition(QCConfig.titanTaskDefinitionArn)
                .overrides { overrideBuilder ->
                    overrideBuilder.containerOverrides({ containerOverrideBuilder ->
                        containerOverrideBuilder.command(
                            "--bucket",
                            Config.importBucket,
                            "--inputKey",
                            task.inputKey,
                            "--outputKey",
                            outputKey
                        ).name("TitanlibContainer")
                    })
                }
        }.join()

        assert(runTaskResponse.tasks().size == 1)
        val taskArn: String = runTaskResponse.tasks().first().taskArn()

        db.markQCTaskAsStarted(tx, id, taskArn, outputKey)
    }

    override fun exec() {
        val tasks = db.getUnstartedQCTaskIds()
        tasks.forEach { id ->
            try {
                runQCTask(id)
            } catch (e: Exception) {
                Log.error(e, "Starting QC task $id failed")
            }
        }
    }
}