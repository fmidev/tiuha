package fi.fmi.tiuha.qc

import fi.fmi.tiuha.Config
import fi.fmi.tiuha.Log
import fi.fmi.tiuha.ScheduledJob
import fi.fmi.tiuha.db.Transaction
import fi.fmi.tiuha.measurementstore.ImportToMeasurementStoreJob
import fi.fmi.tiuha.netatmo.S3
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
    private val s3: S3,
    private val noopQualityControl: Boolean = false,
) : ScheduledJob("qc_task") {
    override fun nextFireTime(): ZonedDateTime =
        ZonedDateTime.now().plus(2, ChronoUnit.MINUTES)

    fun checkQCTask(id: Long) = db.inTx { tx ->
        val task = db.getAndLockQCTask(tx, id)
        Log.info("Checking if qc_task $id output is complete")
        if (s3.keyExists(Config.importBucket, task.outputKey!!)) {
            Log.info("Output for qc_task $id was found ($task.outputKey)")
            db.markQCTaskAsCompleted(tx, id)
            ImportToMeasurementStoreJob.insertMeasurementImport(tx, task.outputKey)
        } else {
            Log.info("Output for qc_task $id was not found ($task.outputKey)")
        }
    }

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
        db.getStartedTasks(limit = 100).forEach { task ->
            try {
                checkQCTask(task.id)
            } catch (e: Exception) {
                Log.error(e, "Starting QC task ${task.id} failed")
            }
        }


        val tasks = db.getUnstartedQCTaskIds(limit = 10)
        tasks.forEach { id ->
            try {
                runQCTask(id)
            } catch (e: Exception) {
                Log.error(e, "Starting QC task $id failed")
            }
        }
    }

    companion object {
        fun insertQcTask(tx: Transaction, inputKey: String): Long {
            val sql = "insert into qc_task (qc_task_status_id, input_s3key) values ('PENDING', ?) RETURNING qc_task_id"
            return tx.selectOne(sql, listOf(inputKey)) { rs -> rs.getLong(1) }
        }
    }
}