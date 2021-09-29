package fi.fmi.tiuha

import org.geotools.data.DataStore
import org.geotools.data.DataStoreFinder
import org.locationtech.geomesa.fs.storage.common.interop.ConfigurationUtils
import java.io.IOException


const val HADOOP_CONFIG = """
<configuration>
    <property>
        <name>fs.s3a.aws.credentials.provider</name>
        <value>
            com.amazonaws.auth.ContainerCredentialsProvider,
            com.amazonaws.auth.profile.ProfileCredentialsProvider
        </value>
    </property>
</configuration>
"""

class S3DataStore {
    val dataStore: DataStore = DataStoreFinder.getDataStore(
        mapOf(
            "fs.path" to "s3a://fmi-tiuha-measurements-dev/",
            "fs.config.xml" to HADOOP_CONFIG
        )
    )

    private val sft = MEASUREMENT_FEATURE_TYPE

    init {
        ConfigurationUtils.setScheme(sft, "hourly,z2-8bits", mapOf(
            "dtg-attribute" to "dtg",
        ))
        ConfigurationUtils.setEncoding(sft, "parquet")
        ConfigurationUtils.setLeafStorage(sft, true)

        try {
            dataStore.getSchema(FEATURE_NAME)
        } catch (e: IOException) {
            println("Creating schema")
            dataStore.createSchema(sft)
        }
    }
}
