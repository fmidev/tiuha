package fi.fmi.tiuha

import org.geotools.data.DataStoreFinder
import org.geotools.data.FeatureWriter
import org.geotools.data.Transaction
import org.locationtech.geomesa.fs.data.FileSystemDataStore
import org.locationtech.geomesa.fs.storage.common.interop.ConfigurationUtils
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import java.io.IOException


val HADOOP_CONFIG = when(Config.environment) {
    Environment.PROD, Environment.DEV -> """
        <configuration>
            <property>
                <name>fs.s3a.aws.credentials.provider</name>
                <value>
                    com.amazonaws.auth.ContainerCredentialsProvider
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
    Environment.OPENSHIFT -> """
        <configuration>
            <property>
                <name>fs.s3a.endpoint</name>
                <value>https://lake.fmi.fi</value>
            </property>
            <property>
                <name>fs.s3a.connection.ssl.enabled</name>
                <value>true</value>
            </property>
            <property>
                <name>fs.s3a.access.key</name>
                <value>${Config.lakeaccesskey}</value>
            </property>
            <property>
                <name>fs.s3a.secret.key</name>
                <value>${Config.lakesecretkey}</value>
            </property>
            <property>
                <name>fs.s3a.path.style.access</name>
                <value>true</value>
            </property>
        </configuration>
    """
}

class S3DataStore(bucket: String) {
    var dataStore: FileSystemDataStore

    init {
        val ds = DataStoreFinder.getDataStore(
            mapOf(
                "fs.path" to "s3a://${bucket}/",
                "fs.config.xml" to HADOOP_CONFIG
            )
        )
        if (ds is FileSystemDataStore) {
            dataStore = ds
        } else {
            throw Error("Expected a FileSystemDataStore but instead got ${ds::class.qualifiedName}")
        }
    }

    private val sft = MEASUREMENT_FEATURE_TYPE

    init {
        ConfigurationUtils.setScheme(sft, "attribute,hourly,z2-8bits", mapOf(
            "dtg-attribute" to "dtg",
            "partitioned-attribute" to "property_id",
        ))
        ConfigurationUtils.setEncoding(sft, "parquet")
        ConfigurationUtils.setLeafStorage(sft, true)
        ConfigurationUtils.setMetadata(sft, "jdbc", mapOf(
            Pair("jdbc.url", Config.geomesaMetadataJdbcUrl),
            Pair("jdbc.user", Config.geomesaUsername),
            Pair("jdbc.password", Config.geomesaPassword)
        ))

        try {
            dataStore.getSchema(FEATURE_NAME)
        } catch (e: IOException) {
            Log.info(e, "Creating schema '$FEATURE_NAME'")
            dataStore.createSchema(sft)
        }
    }

    fun getMeasurementFeatureWriter(): FeatureWriter<SimpleFeatureType, SimpleFeature> =
        dataStore.getFeatureWriterAppend(sft.typeName, Transaction.AUTO_COMMIT)
}
