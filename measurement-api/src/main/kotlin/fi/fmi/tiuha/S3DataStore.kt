package fi.fmi.tiuha

import org.geotools.data.DataStore
import org.geotools.data.DataStoreFinder
import org.locationtech.geomesa.fs.storage.common.interop.ConfigurationUtils
import java.io.IOException


val HADOOP_CONFIG = when(Config.environment) {
    Environment.PROD, Environment.DEV -> """
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
    Environment.LOCAL -> """
        <configuration>
            <property>
                <name>fs.s3a.endpoint</name>
                <value>http://localhost:4566</value>
            </property>
            <property>
                <name>fs.s3a.connection.ssl.enabled</name>
                <value>false</value>
            </property>
            <property>
                <name>fs.s3a.path.style.access</name>
                <value>true</value>
            </property>
            <property>
                <name>fs.s3a.access.key</name>
                <value>access_key</value>
            </property>
            <property>
                <name>fs.s3a.secret.key</name>
                <value>secret_key</value>
            </property>
        </configuration>
    """
}

class S3DataStore {
    val dataStore: DataStore = DataStoreFinder.getDataStore(
        mapOf(
            "fs.path" to "s3a://${Config.measurementsBucket}/",
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
            Log.info("Creating schema")
            dataStore.createSchema(sft)
        }
    }
}
