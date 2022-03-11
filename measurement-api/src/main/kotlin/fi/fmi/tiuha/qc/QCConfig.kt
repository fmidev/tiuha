package fi.fmi.tiuha.qc

import fi.fmi.tiuha.requireEnv

object QCConfig {
    val titanTaskSubnet = requireEnv("TITAN_TASK_SUBNET")
    val titanTaskDefinitionArn = requireEnv("TITAN_TASK_DEFINITION_ARN")
    val titanClusterArn = requireEnv("TITAN_CLUSTER_ARN")
}