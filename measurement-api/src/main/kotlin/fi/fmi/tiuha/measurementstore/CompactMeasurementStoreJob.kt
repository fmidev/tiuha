package fi.fmi.tiuha.measurementstore

import fi.fmi.tiuha.FEATURE_NAME
import fi.fmi.tiuha.ScheduledJob
import org.locationtech.geomesa.fs.data.FileSystemDataStore
import org.locationtech.geomesa.fs.tools.compact.FsCompactCommand
import org.locationtech.geomesa.tools.DistributedRunParam
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class CompactMeasurementStoreJob(private val ds: FileSystemDataStore) : ScheduledJob("compact-measurementstore") {
    override fun nextFireTime() = ZonedDateTime.now().plus(15, ChronoUnit.MINUTES)

    override fun exec() {
        val compact = FsCompactCommand()
        compact.params().apply {
            `featureName_$eq`(FEATURE_NAME)
            `runMode_$eq`(DistributedRunParam.`RunModes$`.`MODULE$`.Local().toString())
            `threads_$eq`(2)
        }

        compact.compact(ds)
    }
}