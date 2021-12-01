package fi.fmi.tiuha.qc

import software.amazon.awssdk.services.ecs.EcsAsyncClient
import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.ScheduledJob
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.LocalStackS3
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.AssignPublicIp
import software.amazon.awssdk.services.ecs.model.LaunchType
import software.amazon.awssdk.services.ecs.model.PropagateTags
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

fun generateOutputKey(inputKey: String): String {
    val inputSegments = inputKey.split('/')
    val fileName = inputSegments.last()
    val outputFileName = "qc_$fileName"
    val outputPrefix = inputSegments.dropLast(1).joinToString("/")
    return "$outputPrefix/$outputFileName"
}

class QCTask(
    private val db: QCDb,
    private val ecsClient: EcsClient,
    private val noopQualityControl: Boolean = false,
) : ScheduledJob("qc_task") {
    override fun nextFireTime(): ZonedDateTime =
        ZonedDateTime.now().plus(2, ChronoUnit.MINUTES)

    fun runQCTask(id: Long) = db.inTx { tx ->
        val task = db.getAndLockQCTask(tx, id)
        if (task.status == "STARTED") {
            Log.info("QC task $id already started")
            return@inTx
        }
        val outputKey = generateOutputKey(task.inputKey)

        if (noopQualityControl) {
            db.markQCTaskAsStarted(tx, id, "arn:fake", outputKey)
            return@inTx
        }

        val runTaskResponse = ecsClient.runTask { builder ->
            builder
                .launchType(LaunchType.FARGATE)
                .cluster(QCConfig.titanClusterArn)
                .networkConfiguration { networkConfig ->
                    networkConfig.awsvpcConfiguration { vpcConfig ->
                        vpcConfig.subnets(QCConfig.titanTaskSubnet).assignPublicIp(AssignPublicIp.DISABLED)
                    }
                }
                .propagateTags(PropagateTags.TASK_DEFINITION)
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
        }

        println(runTaskResponse)
        println(runTaskResponse.tasks())
        val taskArn: String = runTaskResponse.tasks().first().taskArn()

        db.markQCTaskAsStarted(tx, id, taskArn, outputKey)
    }

    fun processAllSync() {
        val tasks = db.getUnstartedQCTaskIds()
        tasks.forEach { id -> runQCTask(id) }
    }

    override fun exec() {
        val tasks = db.getUnstartedQCTaskIds(limit = 10)
        tasks.forEach { id ->
            try {
                runQCTask(id)
            } catch (e: Exception) {
                Log.error(e, "Starting QC task $id failed")
            }
        }
    }
}