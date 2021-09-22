package fi.fmi.tiuha

import com.amazonaws.regions.Regions

object Config {
    val awsRegion = Regions.EU_WEST_1
    val measurementArchiveBucket = "fmi-iot-obs-arch"
    val importMeasurements = false
}