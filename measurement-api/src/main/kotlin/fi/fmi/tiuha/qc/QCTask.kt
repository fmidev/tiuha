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
import java.util.concurrent.TimeUnit;
import java.lang.ProcessBuilder.Redirect
import java.io.InputStreamReader
import java.io.BufferedReader

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
        Log.info("Checking if output is completed for qc_task $task")
        if (s3.keyExists(Config.importBucket, task.outputKey!!)) {
            Log.info("Output for qc_task $id was found (${task.outputKey})")
            db.markQCTaskAsCompleted(tx, id)
            ImportToMeasurementStoreJob.insertMeasurementImport(tx, task.outputKey)
        } else {
            Log.info("Output for qc_task $id was not found (${task.outputKey})")
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
        } else {
            val taskArn = startOpenshiftTask(task, outputKey)
            db.markQCTaskAsStarted(tx, id, taskArn, outputKey)
        }
    }

    fun startFargateTask(task: QCTaskRow, outputKey: String): String {
        Log.info("Starting QC task on Fargate for qc_task ${task.id}")
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

        return runTaskResponse.tasks().first().taskArn()
    }

    fun startOpenshiftTask(task: QCTaskRow, outputKey: String): String {
        Log.info("Starting QC task on Openshift for qc_task ${task.id}")
        val process = ProcessBuilder("/bin/sh", "-c", "oc process qctask-template -p BUCKET=${Config.importBucket} -p INPUTKEY=${task.inputKey} -p OUTPUTKEY=${outputKey} -p TASK_ID=${task.id} -n tiuha-dev | oc create -f -").redirectOutput(Redirect.INHERIT).start()
        Thread.sleep(1_000)
        
        val get_uid = ProcessBuilder("/bin/sh", "-c", "oc get job qc-${task.id} -n tiuha-dev -o jsonpath='{.metadata.uid}'").start()
        Thread.sleep(1_000)
        
        var jobUid: String = ""
        BufferedReader(InputStreamReader(get_uid.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
              jobUid = line.toString()
            }
        }
        return jobUid
    }

    fun processAllSync() {
        db.getStartedTasks().forEach { task -> checkQCTask(task.id) }
        db.getUnstartedQCTaskIds().forEach { id -> runQCTask(id) }
    }

    override fun exec() {
        db.statusCounts().forEach {
            Log.info("qc_tasks with status ${it.first} ${it.second}")
        }

        db.getStartedTasks(limit = 100).forEach { task ->
            try {
                checkQCTask(task.id)
            } catch (e: Exception) {
                Log.error(e, "Checking QC task ${task.id} progress failed")
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